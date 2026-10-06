package io.github.skystrike.ui.console;

import com.badlogic.gdx.InputProcessor;
import io.github.skystrike.input.InputRouter;

/**
 * Focus ownership for the chat/console dialog (console plan §6.1).
 *
 * <p>The dialog never pushes processors onto the {@link InputRouter} directly; it takes and
 * releases focus through this class, which is the single gate the gameplay systems read while a
 * console might exist: {@link #isGameplayActive()} is false from the frame the dialog takes
 * focus until the frame it is released, and the same gate protects against a double-close
 * (Escape pressed during the close transition behaves, it never throws).
 */
public final class ConsoleFocus {

    private final InputRouter router;
    private InputProcessor heldProcessor;

    public ConsoleFocus(InputRouter router) {
        if (router == null) {
            throw new IllegalArgumentException("router is required");
        }
        this.router = router;
    }

    /** Gives {@code processor} the keyboard. Idempotent: re-taking focus with it is a no-op. */
    public void take(InputProcessor processor) {
        if (processor == null || processor == heldProcessor) {
            return;
        }
        if (heldProcessor != null) {
            release();
        }
        heldProcessor = processor;
        router.pushFocus(processor);
    }

    /** Hands focus back to whoever is below the dialog on the stack. Safe when not held. */
    public void release() {
        if (heldProcessor == null) {
            return;
        }
        heldProcessor = null;
        router.popFocus();
    }

    public boolean isFocused() {
        return heldProcessor != null;
    }

    /**
     * The single gameplay gate (console plan §6.2): when this is false, the input sampler sends
     * zero movement intent, the loadout controller ignores slot and wheel input, and the debug
     * key map chews nothing. One read, one owner — the dialog's focus.
     */
    public boolean isGameplayActive() {
        return heldProcessor == null && router.isGameplayActive();
    }
}
