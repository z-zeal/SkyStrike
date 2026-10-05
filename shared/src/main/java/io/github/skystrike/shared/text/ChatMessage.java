package io.github.skystrike.shared.text;

import io.github.skystrike.shared.model.Team;

/**
 * One delivered chat, system, server or console line, as it crosses the wire.
 *
 * <p>Mutable with a public no-argument constructor because the serialiser instantiates it
 * reflectively, like every other wire type in this project. It carries data only.
 *
 * <p>Everything here is already server-decided: the channel is chosen by the authority from the
 * requested target, the author name and team are read from the server's own record rather than
 * from anything the sender claimed, and the body has already been through
 * {@link TextSanitizer}. A client receiving one of these never has to validate it, only render
 * it.
 */
public final class ChatMessage {

    /** No player authored this line — it came from the system, the server or the console. */
    public static final int NO_AUTHOR = -1;

    /** Server clock at the moment the message was accepted. */
    public long timestampMillis;

    /** Delivery and presentation channel. Console-only channels never reach an unprivileged client. */
    public ChatChannel channel = ChatChannel.SYSTEM;

    /** Author's player id, or {@link #NO_AUTHOR}. */
    public int authorId = NO_AUTHOR;

    /** Author's server-accepted name. Empty for authored-by-nobody lines. */
    public String authorName = "";

    /** Author's team index, used for the name colour. {@code Team.NEUTRAL} for system lines. */
    public int authorTeamIndex = Team.NEUTRAL.index();

    /** Sanitised body. Never null; never longer than {@link TextLimits#MAX_CHAT_BODY_CODE_POINTS}. */
    public String body = "";

    public ChatMessage() {
    }

    public ChatMessage(
        long timestampMillis,
        ChatChannel channel,
        int authorId,
        String authorName,
        int authorTeamIndex,
        String body
    ) {
        this.timestampMillis = timestampMillis;
        this.channel = channel == null ? ChatChannel.SYSTEM : channel;
        this.authorId = authorId;
        this.authorName = authorName == null ? "" : authorName;
        this.authorTeamIndex = authorTeamIndex;
        this.body = body == null ? "" : body;
    }

    /** A player-authored line on {@link ChatChannel#ALL} or {@link ChatChannel#TEAM}. */
    public static ChatMessage fromPlayer(
        long timestampMillis,
        ChatTarget target,
        int authorId,
        String authorName,
        int authorTeamIndex,
        String sanitisedBody
    ) {
        ChatTarget resolved = target == null ? ChatTarget.ALL : target;
        return new ChatMessage(
            timestampMillis, resolved.channel(), authorId, authorName, authorTeamIndex, sanitisedBody);
    }

    /** A line nobody authored: a system notice, a server announcement, console or debug output. */
    public static ChatMessage system(long timestampMillis, ChatChannel channel, String body) {
        if (channel == null || channel.isChatTarget()) {
            throw new IllegalArgumentException("a system line needs a non-player channel");
        }
        return new ChatMessage(
            timestampMillis, channel, NO_AUTHOR, "", Team.NEUTRAL.index(), body);
    }

    public Team authorTeam() {
        return Team.fromIndex(authorTeamIndex);
    }

    /** True when this line has a player name to render in front of the body. */
    public boolean hasAuthor() {
        return authorId != NO_AUTHOR && !authorName.isBlank();
    }

    /** Delegates permission filtering to the canonical channel definition. */
    public boolean isVisibleTo(boolean consoleAccess) {
        return channel != null && channel.isVisibleTo(consoleAccess);
    }

    /** A defensive copy, so a retained transcript cannot be mutated by a later send. */
    public ChatMessage copy() {
        return new ChatMessage(timestampMillis, channel, authorId, authorName, authorTeamIndex, body);
    }

    @Override
    public String toString() {
        return "ChatMessage[" + channel
            + (hasAuthor() ? ", " + authorName + "#" + authorId : "")
            + ", \"" + body + "\"]";
    }
}
