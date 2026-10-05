package io.github.skystrike.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatTextContractTest {

    @Test
    @DisplayName("the dialog exposes exactly ALL and TEAM as selectable destinations")
    void chatTargetHasNoConsoleMode() {
        assertEquals(2, ChatTarget.values().length);
        assertEquals(ChatTarget.TEAM, ChatTarget.ALL.toggle());
        assertEquals(ChatTarget.ALL, ChatTarget.TEAM.toggle());
        assertEquals(ChatChannel.ALL, ChatTarget.ALL.channel());
        assertEquals(ChatChannel.TEAM, ChatTarget.TEAM.channel());
        assertEquals(ChatTarget.ALL, ChatTarget.fromChannel(ChatChannel.SYSTEM));
        assertEquals(ChatTarget.TEAM, ChatTarget.fromChannel(ChatChannel.TEAM));
    }

    @Test
    @DisplayName("console-only channels are absent rather than disabled without capability")
    void visibilityFollowsServerCapability() {
        assertTrue(ChatChannel.ALL.isVisibleTo(false));
        assertTrue(ChatChannel.TEAM.isVisibleTo(false));
        assertTrue(ChatChannel.SYSTEM.isVisibleTo(false));
        assertTrue(ChatChannel.SERVER.isVisibleTo(false));
        assertFalse(ChatChannel.CONSOLE.isVisibleTo(false));
        assertFalse(ChatChannel.COMMAND_ECHO.isVisibleTo(false));
        assertFalse(ChatChannel.DEBUG.isVisibleTo(false));
        assertTrue(ChatChannel.DEBUG.isVisibleTo(true));
    }
}
