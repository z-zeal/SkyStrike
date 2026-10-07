package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.settings.Settings;
import java.util.List;
import java.util.function.Consumer;

/**
 * Clickable client settings. Video changes are applied by the platform owner, audio values are
 * explicit persisted stubs, and controls edit the exact {@link KeyBindings} instance used by the
 * console and gameplay.
 */
public final class SettingsScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final int VIDEO_CONTROL_COUNT = 7;
    private static final float LEFT = 72f;
    private static final float ROW_HEIGHT = 25f;

    private final Settings settings;
    private final KeyBindings bindings;
    private final Consumer<Settings> changed;
    private final Runnable back;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final InputAdapter input = new SettingsInput();
    private final List<String> actions;

    /** Video/audio rows first; control rows follow in {@link #actions} order. */
    private int selectedRow;
    private int controlScroll;
    private String capturingAction;
    private String status = "Click a row, or use arrows and Enter.";
    private boolean disposed;

    public SettingsScreen(
            Settings settings,
            KeyBindings bindings,
            Consumer<Settings> changed,
            Runnable back) {
        if (settings == null || bindings == null || changed == null || back == null) {
            throw new IllegalArgumentException("settings, bindings, changed and back are required");
        }
        this.settings = settings;
        this.bindings = bindings;
        this.changed = changed;
        this.back = back;
        this.actions = bindings.actionNames();
        addInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float height = Gdx.graphics.getHeight();
        float top = height - 62f;

        batch.begin();
        font.draw(batch, "SETTINGS", LEFT, top);
        font.draw(batch, "Video", LEFT, top - ROW_HEIGHT);
        drawOption(0, "Resolution", settings.width + "x" + settings.height, top - ROW_HEIGHT * 2f);
        drawOption(1, "VSync", onOff(settings.vsync), top - ROW_HEIGHT * 3f);
        drawOption(2, "Fullscreen", onOff(settings.fullscreen), top - ROW_HEIGHT * 4f);
        drawOption(3, "Quality", Settings.qualityTierLabel(settings.qualityTier), top - ROW_HEIGHT * 5f);
        font.draw(batch, "Audio stubs", LEFT, top - ROW_HEIGHT * 6f);
        drawOption(4, "Master", percent(settings.masterVolume), top - ROW_HEIGHT * 7f);
        drawOption(5, "Music", percent(settings.musicVolume), top - ROW_HEIGHT * 8f);
        drawOption(6, "Effects", percent(settings.effectsVolume), top - ROW_HEIGHT * 9f);

        float controlsTop = top - ROW_HEIGHT * 10.5f;
        font.draw(batch, "Controls - Enter rebind, Backspace reset", LEFT, controlsTop);
        int visible = visibleControlRows(controlsTop);
        keepSelectedControlVisible(visible);
        for (int row = 0; row < visible && controlScroll + row < actions.size(); row++) {
            int actionIndex = controlScroll + row;
            int absoluteRow = VIDEO_CONTROL_COUNT + actionIndex;
            String action = actions.get(actionIndex);
            String prefix = selectedRow == absoluteRow ? "> " : "  ";
            String value = action.equals(capturingAction) ? "Press a key..." : bindings.keyNameFor(action);
            font.draw(batch, prefix + action + ": " + value,
                LEFT, controlsTop - ROW_HEIGHT * (row + 1));
        }
        font.draw(batch, status, LEFT, 34f);
        batch.end();
    }

    private void drawOption(int row, String label, String value, float baseline) {
        String prefix = selectedRow == row ? "> " : "  ";
        font.draw(batch, prefix + label + ": " + value, LEFT, baseline);
    }

    private void adjustSelected(int direction) {
        switch (selectedRow) {
            case 0 -> settings.cycleResolution(direction);
            case 1 -> settings.vsync = !settings.vsync;
            case 2 -> settings.fullscreen = !settings.fullscreen;
            case 3 -> settings.cycleQualityTier(direction);
            case 4 -> settings.masterVolume = settings.adjustVolume(settings.masterVolume, direction * .1f);
            case 5 -> settings.musicVolume = settings.adjustVolume(settings.musicVolume, direction * .1f);
            case 6 -> settings.effectsVolume = settings.adjustVolume(settings.effectsVolume, direction * .1f);
            default -> beginCapture(actions.get(selectedRow - VIDEO_CONTROL_COUNT));
        }
        settings.validate();
        changed.accept(settings);
        if (selectedRow < VIDEO_CONTROL_COUNT) {
            status = "Saved " + optionName(selectedRow) + ".";
        }
    }

    private void activateSelected() {
        if (selectedRow < VIDEO_CONTROL_COUNT) {
            adjustSelected(1);
        } else {
            beginCapture(actions.get(selectedRow - VIDEO_CONTROL_COUNT));
        }
    }

    private void beginCapture(String action) {
        capturingAction = action;
        status = "Press a key for " + action + ", or Escape to cancel.";
    }

    private void finishCapture(int keycode) {
        String error = bindings.bindKeycode(capturingAction, keycode);
        status = error == null
            ? "Bound " + capturingAction + " to " + bindings.keyNameFor(capturingAction) + "."
            : error;
        capturingAction = null;
    }

    private void resetSelectedControl() {
        if (selectedRow < VIDEO_CONTROL_COUNT) {
            return;
        }
        String action = actions.get(selectedRow - VIDEO_CONTROL_COUNT);
        String error = bindings.resetByName(action);
        status = error == null ? "Reset " + action + " to " + bindings.keyNameFor(action) + "." : error;
    }

    private void moveSelection(int direction, int visible) {
        int count = VIDEO_CONTROL_COUNT + actions.size();
        selectedRow = Math.floorMod(selectedRow + direction, count);
        keepSelectedControlVisible(visible);
    }

    private int visibleControlRows(float controlsTop) {
        return Math.max(1, (int) ((controlsTop - 62f) / ROW_HEIGHT));
    }

    private void keepSelectedControlVisible(int visible) {
        if (selectedRow < VIDEO_CONTROL_COUNT) {
            return;
        }
        int control = selectedRow - VIDEO_CONTROL_COUNT;
        if (control < controlScroll) {
            controlScroll = control;
        } else if (control >= controlScroll + visible) {
            controlScroll = control - visible + 1;
        }
        controlScroll = Math.max(0, Math.min(Math.max(0, actions.size() - visible), controlScroll));
    }

    private int hitRow(float y) {
        float top = Gdx.graphics.getHeight() - 62f;
        float[] baselines = {
            top - ROW_HEIGHT * 2f,
            top - ROW_HEIGHT * 3f,
            top - ROW_HEIGHT * 4f,
            top - ROW_HEIGHT * 5f,
            top - ROW_HEIGHT * 7f,
            top - ROW_HEIGHT * 8f,
            top - ROW_HEIGHT * 9f
        };
        for (int i = 0; i < baselines.length; i++) {
            if (withinRow(y, baselines[i])) {
                return i;
            }
        }
        float controlsTop = top - ROW_HEIGHT * 10.5f;
        int visible = visibleControlRows(controlsTop);
        for (int row = 0; row < visible && controlScroll + row < actions.size(); row++) {
            if (withinRow(y, controlsTop - ROW_HEIGHT * (row + 1))) {
                return VIDEO_CONTROL_COUNT + controlScroll + row;
            }
        }
        return -1;
    }

    private static boolean withinRow(float y, float baseline) {
        return y >= baseline - ROW_HEIGHT + 2f && y <= baseline + 5f;
    }

    private static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    private static String percent(float value) {
        return Math.round(value * 100f) + "%";
    }

    private static String optionName(int row) {
        return switch (row) {
            case 0 -> "resolution";
            case 1 -> "vsync";
            case 2 -> "fullscreen";
            case 3 -> "quality";
            case 4 -> "master volume";
            case 5 -> "music volume";
            case 6 -> "effects volume";
            default -> "setting";
        };
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        batch.dispose();
        font.dispose();
    }

    private final class SettingsInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (capturingAction != null) {
                if (keycode == Input.Keys.ESCAPE) {
                    status = "Rebind cancelled.";
                    capturingAction = null;
                } else {
                    finishCapture(keycode);
                }
                return true;
            }
            float controlsTop = Gdx.graphics.getHeight() - 62f - ROW_HEIGHT * 10.5f;
            int visible = visibleControlRows(controlsTop);
            if (keycode == Input.Keys.ESCAPE) {
                back.run();
            } else if (keycode == Input.Keys.UP) {
                moveSelection(-1, visible);
            } else if (keycode == Input.Keys.DOWN) {
                moveSelection(1, visible);
            } else if (keycode == Input.Keys.LEFT) {
                adjustSelected(-1);
            } else if (keycode == Input.Keys.RIGHT) {
                adjustSelected(1);
            } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                activateSelected();
            } else if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
                resetSelectedControl();
            } else if (keycode == Input.Keys.PAGE_UP) {
                moveSelection(-visible, visible);
            } else if (keycode == Input.Keys.PAGE_DOWN) {
                moveSelection(visible, visible);
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (capturingAction != null) {
                return true;
            }
            int hit = hitRow(Gdx.graphics.getHeight() - screenY);
            if (hit >= 0) {
                selectedRow = hit;
                if (hit < VIDEO_CONTROL_COUNT) {
                    adjustSelected(1);
                } else {
                    beginCapture(actions.get(hit - VIDEO_CONTROL_COUNT));
                }
            }
            return true;
        }
    }
}
