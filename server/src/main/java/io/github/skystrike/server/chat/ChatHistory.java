package io.github.skystrike.server.chat;

import io.github.skystrike.shared.text.ChatMessage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * A bounded transcript of accepted lines, kept for moderation and the server terminal
 * (console plan §10, {@code server/chat/ChatHistory}).
 *
 * <p>Records are copied on the way in. A retained transcript that aliases the object the relay
 * also handed to the serialiser would be a quiet way for a later edit to rewrite history.
 *
 * <p>This is not a delivery queue: nothing drains it, and it holds every channel including the
 * team lines a given moderator was not a recipient of.
 */
public final class ChatHistory {

    /** Enough context for a moderation decision without turning the host into a log server. */
    public static final int DEFAULT_CAPACITY = 256;

    private final Deque<ChatMessage> entries = new ArrayDeque<>();
    private final int capacity;

    public ChatHistory() {
        this(DEFAULT_CAPACITY);
    }

    public ChatHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /** Appends a copy of {@code message}, evicting the oldest entry when full. */
    public void record(ChatMessage message) {
        if (message == null) {
            return;
        }
        if (entries.size() == capacity) {
            entries.pollFirst();
        }
        entries.addLast(message.copy());
    }

    /** The transcript, oldest first. */
    public List<ChatMessage> recent() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public int capacity() {
        return capacity;
    }

    public void clear() {
        entries.clear();
    }
}
