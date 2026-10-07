package io.github.skystrike.input;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Focus stack for input routing: ensures UI/dialogs consume input before gameplay polls.
 */
public final class InputRouter {

    private final InputMultiplexer multiplexer = new InputMultiplexer();
    private final Deque<InputProcessor> focusStack = new ArrayDeque<>();

    public InputRouter() {
    }

    /**
     * Pushes a UI or modal processor to the top of the focus stack.
     */
    public void pushFocus(InputProcessor processor) {
        if (processor == null) {
            return;
        }
        focusStack.push(processor);
        rebuildMultiplexer();
    }

    /**
     * Removes the topmost modal processor from the focus stack.
     */
    public InputProcessor popFocus() {
        if (focusStack.isEmpty()) {
            return null;
        }
        InputProcessor popped = focusStack.pop();
        rebuildMultiplexer();
        return popped;
    }

    /**
     * True when a modal UI or dialog is currently capturing input.
     */
    public boolean hasModalFocus() {
        return !focusStack.isEmpty();
    }

    /**
     * True when gameplay input polling is active (no modal UI is stealing focus).
     */
    public boolean isGameplayActive() {
        return focusStack.isEmpty();
    }

    public InputMultiplexer multiplexer() {
        return multiplexer;
    }

    /** Releases every modal when its owning screen is being disposed. */
    public void clearFocus() {
        if (focusStack.isEmpty()) {
            return;
        }
        focusStack.clear();
        rebuildMultiplexer();
    }

    private void rebuildMultiplexer() {
        multiplexer.clear();
        for (InputProcessor processor : focusStack) {
            multiplexer.addProcessor(processor);
        }
    }
}
