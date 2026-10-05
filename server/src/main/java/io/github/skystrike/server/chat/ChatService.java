package io.github.skystrike.server.chat;

import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.shared.text.RateLimiter;
import io.github.skystrike.shared.text.TextLimits;
import io.github.skystrike.shared.text.TextSanitizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The authoritative chat relay: validate, sanitise, rate-limit, scope, address
 * (console plan §11 build-order Phase 2).
 *
 * <p>Deliberately knows nothing about sockets. It is handed a {@link Roster} describing who is
 * joined and which team they are on, and it returns the exact set of player ids a message is
 * addressed to. The handler does the sending. That split is what makes every rule below
 * testable without a network, and it is why team scoping can be asserted rather than assumed.
 *
 * <p><b>Team scoping is resolved here, from the server's own roster.</b> The request carries a
 * target, never a team: a modified client can ask for TEAM, but it cannot choose <i>which</i>
 * team, and the recipients are the sockets the server believes are on the author's team. A team
 * line is never sent to the other team's connection at all, so there is nothing for a patched
 * client to reveal.
 *
 * <p><b>Threading.</b> Tick thread only.
 */
public final class ChatService {

    /** Unknown player, used where a team index would otherwise be ambiguous. */
    public static final int NOT_JOINED = -1;

    /**
     * What the server knows about who is connected. The only source of identity and team for
     * chat — nothing here is ever read from the request.
     */
    public interface Roster {

        /** Every joined player id. */
        Collection<Integer> playerIds();

        /** The team index the server has recorded, or {@link #NOT_JOINED}. */
        int teamIndexOf(int playerId);
    }

    /** Why a request was not relayed. {@link #ACCEPTED} is the only success value. */
    public enum Rejection {
        ACCEPTED,
        /** The sender is not a joined player, or vanished between send and tick. */
        UNKNOWN_AUTHOR,
        /** Nothing displayable survived sanitisation. */
        EMPTY,
        /** The sender is muted server-side. */
        MUTED,
        /** The same line again inside the duplicate window. */
        DUPLICATE,
        /** The sender's token bucket is empty. */
        RATE_LIMITED
    }

    /**
     * The outcome of one request.
     *
     * @param rejection    {@link Rejection#ACCEPTED} or the reason it stopped
     * @param message      the message to deliver, or {@code null} when rejected
     * @param recipientIds exactly who it is addressed to; empty when rejected
     */
    public record Result(Rejection rejection, ChatMessage message, List<Integer> recipientIds) {

        public boolean accepted() {
            return rejection == Rejection.ACCEPTED;
        }

        static Result rejected(Rejection rejection) {
            return new Result(rejection, null, List.of());
        }
    }

    /** Per-author bucket plus the last line it sent, for duplicate suppression. */
    private static final class Sender {
        private final RateLimiter limiter;
        private String lastBody = "";
        private long lastBodyMillis = Long.MIN_VALUE;

        private Sender(long nowMillis) {
            this.limiter = RateLimiter.chat(nowMillis);
        }
    }

    private final Roster roster;
    private final ChatModeration moderation;
    private final ChatHistory history;
    private final Map<Integer, Sender> senders = new HashMap<>();

    private long accepted;
    private long rejected;

    public ChatService(Roster roster) {
        this(roster, new ChatModeration(), new ChatHistory());
    }

    public ChatService(Roster roster, ChatModeration moderation, ChatHistory history) {
        if (roster == null) {
            throw new IllegalArgumentException("roster is required");
        }
        this.roster = roster;
        this.moderation = moderation == null ? new ChatModeration() : moderation;
        this.history = history == null ? new ChatHistory() : history;
    }

    public ChatModeration moderation() {
        return moderation;
    }

    public ChatHistory history() {
        return history;
    }

    public long acceptedCount() {
        return accepted;
    }

    public long rejectedCount() {
        return rejected;
    }

    /**
     * Validates and scopes one request.
     *
     * <p>Check order is load-bearing. Sanitisation runs before anything else so a line made
     * entirely of control characters is simply empty rather than a mute-worthy offence, and the
     * rate limiter is spent <b>last</b> so a muted or duplicated line does not consume the token
     * budget of a player who is about to say something legitimate.
     *
     * @param authorId   the sender, as the server knows them
     * @param authorName the sender's server-accepted name, never the one in the packet
     * @param target     ALL or TEAM, the one thing the client does choose
     * @param rawBody    the untrusted body
     * @param nowMillis  server clock
     */
    public Result submit(int authorId, String authorName, ChatTarget target, String rawBody, long nowMillis) {
        int teamIndex = roster.teamIndexOf(authorId);
        if (teamIndex == NOT_JOINED) {
            return reject(Rejection.UNKNOWN_AUTHOR);
        }

        String body = TextSanitizer.sanitizeChatBody(rawBody);
        if (body.isEmpty()) {
            return reject(Rejection.EMPTY);
        }
        if (moderation.isMuted(authorId)) {
            return reject(Rejection.MUTED);
        }

        Sender sender = senders.computeIfAbsent(authorId, id -> new Sender(nowMillis));
        if (body.equals(sender.lastBody)
            && nowMillis - sender.lastBodyMillis < TextLimits.DUPLICATE_MESSAGE_WINDOW_MILLIS) {
            return reject(Rejection.DUPLICATE);
        }
        if (!sender.limiter.tryAcquire(nowMillis)) {
            return reject(Rejection.RATE_LIMITED);
        }

        sender.lastBody = body;
        sender.lastBodyMillis = nowMillis;

        ChatTarget resolved = target == null ? ChatTarget.ALL : target;
        ChatMessage message =
            ChatMessage.fromPlayer(nowMillis, resolved, authorId, authorName, teamIndex, body);
        history.record(message);
        accepted++;
        return new Result(Rejection.ACCEPTED, message, recipientsFor(resolved, teamIndex));
    }

    /**
     * Addresses a server-authored notice. System and server channels go to everyone; console and
     * debug channels are addressed by the caller that knows who may read them.
     */
    public Result announce(ChatMessage message) {
        if (message == null) {
            return reject(Rejection.EMPTY);
        }
        String body = TextSanitizer.sanitizeChatBody(message.body);
        if (body.isEmpty()) {
            return reject(Rejection.EMPTY);
        }
        message.body = body;
        history.record(message);
        accepted++;
        return new Result(Rejection.ACCEPTED, message, List.copyOf(roster.playerIds()));
    }

    /** Drops the per-author limiter and duplicate state when a player disconnects. */
    public void forget(int playerId) {
        senders.remove(playerId);
    }

    public void clear() {
        senders.clear();
        history.clear();
        moderation.clear();
        accepted = 0;
        rejected = 0;
    }

    /**
     * The send list. ALL reaches every joined player, TEAM reaches only the ids the roster says
     * share the author's team — including the author, so their own line echoes back through the
     * same path everyone else sees rather than being drawn locally and hoped about.
     */
    private List<Integer> recipientsFor(ChatTarget target, int authorTeamIndex) {
        Collection<Integer> everyone = roster.playerIds();
        if (target == ChatTarget.ALL) {
            return List.copyOf(everyone);
        }
        List<Integer> teammates = new ArrayList<>();
        for (Integer id : everyone) {
            if (id != null && roster.teamIndexOf(id) == authorTeamIndex) {
                teammates.add(id);
            }
        }
        return List.copyOf(teammates);
    }

    private Result reject(Rejection rejection) {
        rejected++;
        return Result.rejected(rejection);
    }
}
