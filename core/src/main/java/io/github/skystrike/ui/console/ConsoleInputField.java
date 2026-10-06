package io.github.skystrike.ui.console;

import io.github.skystrike.shared.text.TextLimits;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The text entry half of the dialog's input strip (console plan §3.2).
 *
 * <p>A caret-editing line buffer with a history ring — submit pushes the line, Up/Down walk the
 * ring, typing escapes back to the draft — plus live command detection delegated to the
 * caller-supplied detector (which is {@code ClientCommandService#isCommandLine} in game). It
 * never decides what a submitted line <i>means</i>; that is the dialog's job one level up.
 *
 * <p>Text is capped at the shared chat body limit regardless of mode — a command longer than a
 * chat message is an authoring accident, and the hard wire cap is checked again at submit.
 */
public final class ConsoleInputField {

    /** {@code isCommand(String)} — the same call the dialog restyle makes, injected. */
    @FunctionalInterface
    public interface CommandDetector {
        boolean isCommand(String text);
    }

    private final Deque<String> history = new ArrayDeque<>(TextLimits.INPUT_HISTORY_CAPACITY);
    private final CommandDetector detector;

    private StringBuilder text = new StringBuilder();
    private int caret;
    /** The draft interrupted by history walking; restored when they walk back past the newest. */
    private String draft;
    private int historyCursor = -1;

    /** Holding gdx Keys.TAB etc. out of here keeps the field renderer-free and testable. */
    public ConsoleInputField(CommandDetector detector) {
        if (detector == null) {
            throw new IllegalArgumentException("detector is required");
        }
        this.detector = detector;
    }

    /** Inserts one printable character at the caret. */
    public void typeChar(char c) {
        if (c < 0x20 || c == 0x7F) {
            return;
        }
        // The command line cap subsumes the chat cap; the server re-checks at submit either way.
        if (text.codePointCount(0, text.length()) >= TextLimits.MAX_COMMAND_LINE_CODE_POINTS) {
            return;
        }
        leaveHistory();
        text.insert(caret, c);
        caret++;
    }

    /** Backspace. */
    public boolean backspace() {
        if (caret == 0) {
            return false;
        }
        leaveHistory();
        text.deleteCharAt(caret - 1);
        caret--;
        return true;
    }

    /** Delete-forward. */
    public boolean delete() {
        if (caret >= text.length()) {
            return false;
        }
        leaveHistory();
        text.deleteCharAt(caret);
        return true;
    }

    public void moveCaretLeft() {
        caret = Math.max(0, caret - 1);
    }

    public void moveCaretRight() {
        caret = Math.min(text.length(), caret + 1);
    }

    public void moveCaretHome() {
        caret = 0;
    }

    public void moveCaretEnd() {
        caret = text.length();
    }

    /** Pushes a submitted line onto the history ring and clears the field. */
    public void commit(String line) {
        if (line != null && !line.isBlank()) {
            if (!line.equals(history.peekLast())) {
                history.addLast(line);
                while (history.size() > TextLimits.INPUT_HISTORY_CAPACITY) {
                    history.removeFirst();
                }
            }
        }
        text.setLength(0);
        caret = 0;
        historyCursor = -1;
        draft = null;
    }

    /** Up arrow: walk toward older entries. Nothing happens at the oldest end. */
    public void historyBack() {
        if (history.isEmpty()) {
            return;
        }
        if (historyCursor < 0) {
            draft = text.toString();
            historyCursor = history.size() - 1;
        } else if (historyCursor > 0) {
            historyCursor--;
        } else {
            return;
        }
        restore(fromHistory(historyCursor));
    }

    /** Down arrow: walk toward newer entries, then back to the interrupted draft. */
    public void historyForward() {
        if (historyCursor < 0) {
            return;
        }
        historyCursor++;
        if (historyCursor >= history.size()) {
            restore(draft == null ? "" : draft);
            historyCursor = -1;
            draft = null;
        } else {
            restore(fromHistory(historyCursor));
        }
    }

    private String fromHistory(int index) {
        int i = 0;
        for (String entry : history) {
            if (i == index) {
                return entry;
            }
            i++;
        }
        return "";
    }

    private void restore(String value) {
        text.setLength(0);
        text.append(value == null ? "" : value);
        caret = text.length();
    }

    /** Editing while walking history detaches from the ring and keeps the current recall. */
    private void leaveHistory() {
        historyCursor = -1;
        draft = null;
    }

    /** Replaces the whole field, e.g. when a completion is accepted. */
    public void setText(String value) {
        text.setLength(0);
        text.append(value == null ? "" : value);
        caret = text.length();
        historyCursor = -1;
        draft = null;
    }

    public String text() {
        return text.toString();
    }

    public int caret() {
        return caret;
    }

    /** Whether walking history (screens may draw a marker while the ring is engaged). */
    public boolean inHistory() {
        return historyCursor >= 0;
    }

    /** Live command detection — true while the text reads as a command line for this client. */
    public boolean isCommand() {
        return detector.isCommand(text.toString());
    }
}
