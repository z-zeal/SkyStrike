package io.github.skystrike.server.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.shared.text.TextLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authoritative relay's rules, exercised without a socket.
 *
 * <p>The important assertions here are the adversarial ones: team scoping must be computed from
 * the roster rather than taken from the request, and a client must not be able to spend another
 * player's rate budget or address a team it is not on.
 */
class ChatServiceTest {

    /** A roster that answers only from its own map, like the real registry does. */
    private static final class FakeRoster implements ChatService.Roster {
        private final Map<Integer, Integer> teamByPlayerId = new LinkedHashMap<>();

        FakeRoster with(int playerId, int teamIndex) {
            teamByPlayerId.put(playerId, teamIndex);
            return this;
        }

        @Override
        public Collection<Integer> playerIds() {
            return new ArrayList<>(teamByPlayerId.keySet());
        }

        @Override
        public int teamIndexOf(int playerId) {
            Integer team = teamByPlayerId.get(playerId);
            return team == null ? ChatService.NOT_JOINED : team;
        }
    }

    private FakeRoster roster;
    private ChatService chat;

    @BeforeEach
    void setUp() {
        // 1 and 2 on team A, 3 and 4 on team B.
        roster = new FakeRoster().with(1, 0).with(2, 0).with(3, 1).with(4, 1);
        chat = new ChatService(roster);
    }

    @Test
    @DisplayName("an ALL line reaches every joined player, including the author")
    void allLinesReachEveryone() {
        ChatService.Result result = chat.submit(1, "Nova", ChatTarget.ALL, "hello", 0L);

        assertTrue(result.accepted());
        assertEquals(ChatChannel.ALL, result.message().channel);
        assertEquals(List.of(1, 2, 3, 4), result.recipientIds());
        assertEquals("Nova", result.message().authorName);
        assertEquals(1, result.message().authorId);
    }

    @Test
    @DisplayName("a TEAM line is addressed only to the author's own team")
    void teamLinesAreScopedBySendList() {
        ChatService.Result result = chat.submit(1, "Nova", ChatTarget.TEAM, "rotating B", 0L);

        assertTrue(result.accepted());
        assertEquals(ChatChannel.TEAM, result.message().channel);
        assertEquals(List.of(1, 2), result.recipientIds());
        assertFalse(result.recipientIds().contains(3), "the other team is never a recipient");
        assertFalse(result.recipientIds().contains(4), "the other team is never a recipient");
    }

    @Test
    @DisplayName("the author's team comes from the roster, so a patched client cannot pick one")
    void teamComesFromTheRosterNotTheRequest() {
        // 3 is on team B. There is no field in the request that could say otherwise, and the
        // recipients prove it: the message is addressed to B, not to A.
        ChatService.Result result = chat.submit(3, "Rook", ChatTarget.TEAM, "stacked here", 0L);

        assertEquals(List.of(3, 4), result.recipientIds());
        assertEquals(1, result.message().authorTeamIndex);
    }

    @Test
    @DisplayName("a sender who is not on the roster is relayed to nobody")
    void unknownAuthorsAreRejected() {
        ChatService.Result result = chat.submit(99, "Ghost", ChatTarget.ALL, "hello", 0L);

        assertEquals(ChatService.Rejection.UNKNOWN_AUTHOR, result.rejection());
        assertNull(result.message());
        assertTrue(result.recipientIds().isEmpty());
    }

    @Test
    @DisplayName("the body is sanitised and truncated server-side, not trusted")
    void bodiesAreSanitisedServerSide() {
        String oversized = "x".repeat(TextLimits.MAX_CHAT_BODY_CODE_POINTS + 50);
        ChatService.Result result = chat.submit(1, "Nova", ChatTarget.ALL, oversized, 0L);

        assertTrue(result.accepted());
        assertEquals(
            TextLimits.MAX_CHAT_BODY_CODE_POINTS,
            result.message().body.codePointCount(0, result.message().body.length()));

        ChatService.Result markup = chat.submit(1, "Nova", ChatTarget.ALL, "[RED]danger\u0007", 5_000L);
        assertTrue(markup.accepted());
        assertEquals("REDdanger", markup.message().body);
    }

    @Test
    @DisplayName("a line that sanitises to nothing is never relayed")
    void emptyLinesAreDropped() {
        assertEquals(ChatService.Rejection.EMPTY, chat.submit(1, "Nova", ChatTarget.ALL, "   ", 0L).rejection());
        assertEquals(ChatService.Rejection.EMPTY, chat.submit(1, "Nova", ChatTarget.ALL, null, 0L).rejection());
        assertEquals(
            ChatService.Rejection.EMPTY,
            chat.submit(1, "Nova", ChatTarget.ALL, "\u0000\u200B", 0L).rejection());
    }

    @Test
    @DisplayName("a muted player is silenced at the relay, before anyone is addressed")
    void mutedPlayersAreSilencedAtTheRelay() {
        chat.moderation().mute(1);

        ChatService.Result muted = chat.submit(1, "Nova", ChatTarget.ALL, "hello", 0L);
        assertEquals(ChatService.Rejection.MUTED, muted.rejection());
        assertTrue(muted.recipientIds().isEmpty());

        chat.moderation().unmute(1);
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "hello", 0L).accepted());
    }

    @Test
    @DisplayName("spam is throttled per author, and one spammer cannot throttle anyone else")
    void rateLimitingIsPerAuthor() {
        for (int i = 0; i < TextLimits.CHAT_RATE_CAPACITY; i++) {
            assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "line " + i, 0L).accepted(), "burst " + i);
        }
        assertEquals(
            ChatService.Rejection.RATE_LIMITED,
            chat.submit(1, "Nova", ChatTarget.ALL, "one too many", 0L).rejection());

        // A different author has their own bucket.
        assertTrue(chat.submit(2, "Rook", ChatTarget.ALL, "still fine", 0L).accepted());

        // And the bucket refills with time.
        assertTrue(
            chat.submit(1, "Nova", ChatTarget.ALL, "later", TextLimits.CHAT_RATE_WINDOW_MILLIS).accepted());
    }

    @Test
    @DisplayName("the same line twice inside the duplicate window is dropped without a token")
    void duplicatesAreDroppedWithoutSpendingTokens() {
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "spam", 0L).accepted());
        assertEquals(
            ChatService.Rejection.DUPLICATE,
            chat.submit(1, "Nova", ChatTarget.ALL, "spam", 100L).rejection());

        // Two tokens should still be left, since the duplicate never reached the bucket.
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "a", 200L).accepted());
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "b", 300L).accepted());
        assertEquals(
            ChatService.Rejection.RATE_LIMITED,
            chat.submit(1, "Nova", ChatTarget.ALL, "c", 400L).rejection());
    }

    @Test
    @DisplayName("the same line again after the window is allowed")
    void duplicatesExpire() {
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "regroup", 0L).accepted());
        assertTrue(
            chat.submit(1, "Nova", ChatTarget.ALL, "regroup", TextLimits.DUPLICATE_MESSAGE_WINDOW_MILLIS + 1)
                .accepted());
    }

    @Test
    @DisplayName("disconnecting clears the author's limiter and duplicate state")
    void forgettingAPlayerResetsTheirBudget() {
        for (int i = 0; i < TextLimits.CHAT_RATE_CAPACITY; i++) {
            chat.submit(1, "Nova", ChatTarget.ALL, "line " + i, 0L);
        }
        assertEquals(
            ChatService.Rejection.RATE_LIMITED,
            chat.submit(1, "Nova", ChatTarget.ALL, "blocked", 0L).rejection());

        chat.forget(1);
        assertTrue(chat.submit(1, "Nova", ChatTarget.ALL, "fresh session", 0L).accepted());
    }

    @Test
    @DisplayName("accepted lines are transcribed; rejected ones are not")
    void historyRecordsOnlyAcceptedLines() {
        chat.submit(1, "Nova", ChatTarget.ALL, "kept", 0L);
        chat.submit(1, "Nova", ChatTarget.ALL, "   ", 0L);
        chat.submit(99, "Ghost", ChatTarget.ALL, "dropped", 0L);

        List<ChatMessage> transcript = chat.history().recent();
        assertEquals(1, transcript.size());
        assertEquals("kept", transcript.get(0).body);
        assertEquals(1, chat.acceptedCount());
        assertEquals(2, chat.rejectedCount());
    }

    @Test
    @DisplayName("a server announcement reaches everyone and is transcribed")
    void announcementsReachEveryone() {
        ChatService.Result result =
            chat.announce(ChatMessage.system(0L, ChatChannel.SERVER, "round starting"));

        assertTrue(result.accepted());
        assertEquals(List.of(1, 2, 3, 4), result.recipientIds());
        assertEquals(1, chat.history().size());
    }
}
