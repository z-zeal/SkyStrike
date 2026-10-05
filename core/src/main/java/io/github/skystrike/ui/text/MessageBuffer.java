package io.github.skystrike.ui.text;

import io.github.skystrike.shared.text.TextLimits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, ordered sink for every player-visible line: chat, system notices, kill notices,
 * command output and debug messages.
 *
 * <p>The buffer is deliberately renderer-agnostic. Its newest line is at the end, its oldest
 * evicts first, and it applies capability filtering only when the dialog asks for a view. This
 * allows a client promoted during a match to see newly eligible console output without retaining
 * a second hidden transcript.
 */
public final class MessageBuffer {

    private final int capacity;
    private final Deque<MessageLine> lines;

    public MessageBuffer() {
        this(TextLimits.MESSAGE_BUFFER_CAPACITY);
    }

    public MessageBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
    }

    /** Appends one line, evicting exactly one oldest line when full. */
    public void add(MessageLine line) {
        Objects.requireNonNull(line, "line");
        if (lines.size() == capacity) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    /** Removes every retained line; used by the future local {@code /clear} command. */
    public void clear() {
        lines.clear();
    }

    /** Total retained records before permission filtering. */
    public int size() {
        return lines.size();
    }

    public int capacity() {
        return capacity;
    }

    /** A chronological immutable snapshot, oldest to newest, filtered for this client's access. */
    public List<MessageLine> visibleLines(boolean consoleAccess) {
        List<MessageLine> visible = new ArrayList<>(lines.size());
        for (MessageLine line : lines) {
            if (line.isVisibleTo(consoleAccess)) {
                visible.add(line);
            }
        }
        return List.copyOf(visible);
    }

    /** A chronological immutable snapshot including all channels, for internal diagnostics only. */
    public List<MessageLine> allLines() {
        return List.copyOf(lines);
    }
}
