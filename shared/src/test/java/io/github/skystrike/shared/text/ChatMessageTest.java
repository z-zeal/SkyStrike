package io.github.skystrike.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    @DisplayName("a player line takes its channel from the requested target")
    void playerLinesDeriveTheirChannel() {
        ChatMessage all = ChatMessage.fromPlayer(1L, ChatTarget.ALL, 3, "Nova", 0, "hi");
        assertEquals(ChatChannel.ALL, all.channel);
        assertTrue(all.hasAuthor());

        ChatMessage team = ChatMessage.fromPlayer(1L, ChatTarget.TEAM, 3, "Nova", 1, "hi");
        assertEquals(ChatChannel.TEAM, team.channel);
        assertEquals(Team.TEAM_B, team.authorTeam());
    }

    @Test
    @DisplayName("a null target falls back to ALL rather than to nothing")
    void nullTargetFallsBackToAll() {
        assertEquals(ChatChannel.ALL, ChatMessage.fromPlayer(1L, null, 3, "Nova", 0, "hi").channel);
    }

    @Test
    @DisplayName("a system line cannot claim a player channel")
    void systemLinesRejectPlayerChannels() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ChatMessage.system(1L, ChatChannel.ALL, "nope"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ChatMessage.system(1L, ChatChannel.TEAM, "nope"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ChatMessage.system(1L, null, "nope"));

        ChatMessage notice = ChatMessage.system(1L, ChatChannel.SYSTEM, "server restarting");
        assertFalse(notice.hasAuthor());
        assertEquals(ChatMessage.NO_AUTHOR, notice.authorId);
        assertEquals(Team.NEUTRAL, notice.authorTeam());
    }

    @Test
    @DisplayName("console-only channels are absent without access, ordinary ones are not")
    void visibilityFollowsTheChannel() {
        ChatMessage console = ChatMessage.system(1L, ChatChannel.CONSOLE, "ok");
        assertFalse(console.isVisibleTo(false));
        assertTrue(console.isVisibleTo(true));

        ChatMessage chat = ChatMessage.fromPlayer(1L, ChatTarget.ALL, 1, "Nova", 0, "hi");
        assertTrue(chat.isVisibleTo(false));
        assertTrue(chat.isVisibleTo(true));
    }

    @Test
    @DisplayName("nulls become empty strings so the renderer never has to check")
    void nullFieldsAreNormalised() {
        ChatMessage message = new ChatMessage(0L, null, 1, null, 0, null);
        assertEquals(ChatChannel.SYSTEM, message.channel);
        assertEquals("", message.authorName);
        assertEquals("", message.body);
        assertFalse(message.hasAuthor());
    }

    @Test
    @DisplayName("a copy does not alias the original")
    void copyIsDeepEnoughForATranscript() {
        ChatMessage original = ChatMessage.fromPlayer(5L, ChatTarget.TEAM, 2, "Rook", 1, "push");
        ChatMessage copy = original.copy();
        assertNotSame(original, copy);
        original.body = "rewritten";
        assertEquals("push", copy.body);
        assertEquals(ChatChannel.TEAM, copy.channel);
        assertEquals(5L, copy.timestampMillis);
    }
}
