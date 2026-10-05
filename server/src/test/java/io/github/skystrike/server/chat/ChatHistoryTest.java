package io.github.skystrike.server.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.text.ChatTarget;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatHistoryTest {

    @Test
    @DisplayName("the transcript is bounded and evicts oldest first")
    void evictionIsOldestFirst() {
        ChatHistory history = new ChatHistory(3);
        for (int i = 0; i < 5; i++) {
            history.record(ChatMessage.fromPlayer(i, ChatTarget.ALL, 1, "Nova", 0, "line " + i));
        }

        List<ChatMessage> recent = history.recent();
        assertEquals(3, recent.size());
        assertEquals("line 2", recent.get(0).body);
        assertEquals("line 4", recent.get(2).body);
    }

    @Test
    @DisplayName("the transcript stores a copy, so a later edit cannot rewrite history")
    void recordsAreCopied() {
        ChatHistory history = new ChatHistory();
        ChatMessage message = ChatMessage.fromPlayer(0L, ChatTarget.ALL, 1, "Nova", 0, "original");
        history.record(message);

        message.body = "rewritten";
        assertEquals("original", history.recent().get(0).body);
    }

    @Test
    void nullsAreIgnoredAndCapacityMustBePositive() {
        ChatHistory history = new ChatHistory(2);
        history.record(null);
        assertTrue(history.recent().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new ChatHistory(0));
        assertThrows(IllegalArgumentException.class, () -> new ChatHistory(-1));
    }
}
