package io.github.skystrike.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.skystrike.input.InputRouter;
import java.util.function.Consumer;

/** Modal pause focus. It is deliberately an InputProcessor so Escape has one topmost owner. */
public final class PauseOverlay extends InputAdapter {
    private final InputRouter router;
    private final Consumer<String> action;
    public PauseOverlay(InputRouter router, Consumer<String> action) {
        this.router = router; this.action = action;
    }
    @Override public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) { action.accept("Resume"); router.popFocus(); return true; }
        if (keycode == Input.Keys.R) { action.accept("Resume"); router.popFocus(); return true; }
        if (keycode == Input.Keys.L) { action.accept("Loadout"); return true; }
        if (keycode == Input.Keys.S) { action.accept("Settings"); return true; }
        if (keycode == Input.Keys.D) { action.accept("Disconnect"); return true; }
        return true;
    }
}
