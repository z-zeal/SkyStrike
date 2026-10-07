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
    @Override public boolean touchDown(int x, int y, int pointer, int button) {
        // The overlay menu is ordered Resume, Loadout, Settings, Disconnect.
        int row = y / 64;
        if (row == 0) { action.accept("Resume"); router.popFocus(); }
        else if (row == 1) action.accept("Loadout");
        else if (row == 2) action.accept("Settings");
        else if (row == 3) { action.accept("Disconnect"); router.popFocus(); }
        return true;
    }
}
