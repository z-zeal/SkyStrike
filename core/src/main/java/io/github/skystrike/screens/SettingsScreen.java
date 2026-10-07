package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.settings.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Persisted client settings built from Scene2D controls. The same {@link KeyBindings} instance
 * remains the source of truth for this screen, the console and gameplay.
 */
public final class SettingsScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final float LABEL_WIDTH = 180f;
    private static final float CONTROL_WIDTH = 280f;

    private final Settings settings;
    private final KeyBindings bindings;
    private final Consumer<Settings> changed;
    private final Runnable back;
    private final Skin skin;
    private final Stage stage;
    private final InputMultiplexer input = new InputMultiplexer();
    private final List<Actor> keyboardOrder = new ArrayList<>();
    private final Map<Actor, Runnable> buttonActions = new LinkedHashMap<>();
    private final Map<String, TextButton> bindingButtons = new LinkedHashMap<>();

    private final SelectBox<String> resolution;
    private final CheckBox vsync;
    private final CheckBox fullscreen;
    private final SelectBox<String> quality;
    private final Slider masterVolume;
    private final Slider musicVolume;
    private final Slider effectsVolume;
    private final Label masterValue;
    private final Label musicValue;
    private final Label effectsValue;
    private final Label status;
    private final TextButton backButton;

    private String capturingAction;
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
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.stage = new Stage(new ScreenViewport());

        resolution = new SelectBox<>(skin);
        resolution.setItems(resolutionItems().toArray(new String[0]));
        resolution.setSelected(settings.width + "x" + settings.height);
        vsync = new CheckBox("Enable VSync", skin);
        vsync.setChecked(settings.vsync);
        fullscreen = new CheckBox("Fullscreen", skin);
        fullscreen.setChecked(settings.fullscreen);
        quality = new SelectBox<>(skin);
        quality.setItems("Low", "Medium", "High", "Ultra");
        quality.setSelectedIndex(settings.qualityTier);
        masterVolume = volumeSlider(settings.masterVolume);
        musicVolume = volumeSlider(settings.musicVolume);
        effectsVolume = volumeSlider(settings.effectsVolume);
        masterValue = new Label(percent(masterVolume.getValue()), skin);
        musicValue = new Label(percent(musicVolume.getValue()), skin);
        effectsValue = new Label(percent(effectsVolume.getValue()), skin);
        status = new Label("Use Tab to move focus. Choose Rebind to capture a key.", skin);
        status.setWrap(true);
        backButton = new TextButton("Back", skin);

        buildLayout();
        listenForChanges();
        input.addProcessor(new SettingsKeyboard());
        input.addProcessor(stage);
        addInputProcessor(input);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        stage.setKeyboardFocus(resolution);
    }

    private Slider volumeSlider(float value) {
        Slider slider = new Slider(0f, 1f, .05f, false, skin);
        slider.setValue(value);
        return slider;
    }

    private List<String> resolutionItems() {
        List<String> values = new ArrayList<>();
        for (Settings.Resolution option : Settings.RESOLUTIONS) {
            values.add(option.label());
        }
        String current = settings.width + "x" + settings.height;
        if (!values.contains(current)) {
            values.add(current);
        }
        return values;
    }

    private void buildLayout() {
        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(30f);
        stage.addActor(root);

        root.add(new Label("SETTINGS", skin)).left().padBottom(12f);
        root.row();

        Table options = new Table();
        options.top().left();
        addHeading(options, "Video");
        addOption(options, "Resolution", resolution);
        addOption(options, "", vsync);
        addOption(options, "", fullscreen);
        addOption(options, "Quality", quality);
        addHeading(options, "Audio stubs");
        addVolumeOption(options, "Master", masterVolume, masterValue);
        addVolumeOption(options, "Music", musicVolume, musicValue);
        addVolumeOption(options, "Effects", effectsVolume, effectsValue);

        keyboardOrder.add(resolution);
        keyboardOrder.add(vsync);
        keyboardOrder.add(fullscreen);
        keyboardOrder.add(quality);
        keyboardOrder.add(masterVolume);
        keyboardOrder.add(musicVolume);
        keyboardOrder.add(effectsVolume);

        root.add(options).left().fillX();
        root.row();
        root.add(new Label("Controls", skin)).left().padTop(14f).padBottom(5f);
        root.row();

        Table controls = new Table();
        controls.top().left();
        for (String actionName : bindings.actionNames()) {
            addBindingRow(controls, actionName);
        }
        ScrollPane controlsScroll = new ScrollPane(controls, skin);
        controlsScroll.setFadeScrollBars(false);
        controlsScroll.setScrollingDisabled(true, false);
        root.add(controlsScroll).left().width(660f).height(250f).fill();
        root.row();

        Table footer = new Table();
        footer.add(status).left().width(520f).padRight(14f);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                back.run();
            }
        });
        buttonActions.put(backButton, back);
        keyboardOrder.add(backButton);
        footer.add(backButton).width(126f).height(36f).right();
        root.add(footer).left().fillX().padTop(12f);
    }

    private void addHeading(Table table, String heading) {
        table.add(new Label(heading, skin)).left().colspan(3).padTop(4f).padBottom(5f);
        table.row();
    }

    private void addOption(Table table, String label, Actor control) {
        table.add(new Label(label, skin)).left().width(LABEL_WIDTH).padBottom(7f);
        table.add(control).left().width(CONTROL_WIDTH).height(34f).padBottom(7f);
        table.add().width(80f);
        table.row();
    }

    private void addVolumeOption(Table table, String label, Slider slider, Label value) {
        table.add(new Label(label, skin)).left().width(LABEL_WIDTH).padBottom(7f);
        table.add(slider).left().width(CONTROL_WIDTH).height(28f).padBottom(7f);
        table.add(value).left().width(80f).padLeft(8f).padBottom(7f);
        table.row();
    }

    private void addBindingRow(Table table, String actionName) {
        TextButton rebind = new TextButton(bindings.keyNameFor(actionName), skin);
        TextButton reset = new TextButton("Reset", skin);
        rebind.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                beginCapture(actionName);
            }
        });
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                resetBinding(actionName);
            }
        });
        bindingButtons.put(actionName, rebind);
        buttonActions.put(rebind, () -> beginCapture(actionName));
        buttonActions.put(reset, () -> resetBinding(actionName));
        keyboardOrder.add(rebind);
        keyboardOrder.add(reset);

        table.add(new Label(actionName, skin)).left().expandX().fillX().padBottom(5f);
        table.add(rebind).width(220f).height(32f).padRight(6f).padBottom(5f);
        table.add(reset).width(90f).height(32f).padBottom(5f);
        table.row();
    }

    private void listenForChanges() {
        resolution.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String value = resolution.getSelected();
                int separator = value == null ? -1 : value.indexOf('x');
                if (separator <= 0 || separator == value.length() - 1) {
                    return;
                }
                try {
                    settings.width = Integer.parseInt(value.substring(0, separator));
                    settings.height = Integer.parseInt(value.substring(separator + 1));
                    saveSettings("Resolution saved.");
                } catch (NumberFormatException ignored) {
                    status.setText("Resolution selection is invalid.");
                }
            }
        });
        vsync.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.vsync = vsync.isChecked();
                saveSettings("VSync saved.");
            }
        });
        fullscreen.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.fullscreen = fullscreen.isChecked();
                saveSettings("Fullscreen saved.");
            }
        });
        quality.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.qualityTier = quality.getSelectedIndex();
                saveSettings("Quality saved.");
            }
        });
        addVolumeListener(masterVolume, masterValue, value -> settings.masterVolume = value, "Master volume saved.");
        addVolumeListener(musicVolume, musicValue, value -> settings.musicVolume = value, "Music volume saved.");
        addVolumeListener(effectsVolume, effectsValue, value -> settings.effectsVolume = value, "Effects volume saved.");
    }

    private void addVolumeListener(
        Slider slider, Label valueLabel, Consumer<Float> sink, String confirmation
    ) {
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float value = slider.getValue();
                sink.accept(value);
                valueLabel.setText(percent(value));
                saveSettings(confirmation);
            }
        });
    }

    private void saveSettings(String confirmation) {
        settings.validate();
        changed.accept(settings);
        status.setText(confirmation);
    }

    private void beginCapture(String actionName) {
        capturingAction = actionName;
        status.setText("Press a key for " + actionName + ", or Escape to cancel.");
        TextButton button = bindingButtons.get(actionName);
        if (button != null) {
            stage.setKeyboardFocus(button);
        }
    }

    private void finishCapture(int keycode) {
        String error = bindings.bindKeycode(capturingAction, keycode);
        if (error == null) {
            TextButton button = bindingButtons.get(capturingAction);
            if (button != null) {
                button.setText(bindings.keyNameFor(capturingAction));
            }
            status.setText("Bound " + capturingAction + " to "
                + bindings.keyNameFor(capturingAction) + ".");
        } else {
            status.setText(error);
        }
        capturingAction = null;
    }

    private void resetBinding(String actionName) {
        String error = bindings.resetByName(actionName);
        if (error == null) {
            TextButton button = bindingButtons.get(actionName);
            if (button != null) {
                button.setText(bindings.keyNameFor(actionName));
            }
            status.setText("Reset " + actionName + " to " + bindings.keyNameFor(actionName) + ".");
        } else {
            status.setText(error);
        }
    }

    private void moveFocus(int direction) {
        if (keyboardOrder.isEmpty()) {
            return;
        }
        int index = keyboardOrder.indexOf(stage.getKeyboardFocus());
        if (index < 0) {
            index = direction < 0 ? 0 : -1;
        }
        stage.setKeyboardFocus(keyboardOrder.get(
            Math.floorMod(index + direction, keyboardOrder.size())));
    }

    private void activateFocused() {
        Runnable action = buttonActions.get(stage.getKeyboardFocus());
        if (action != null) {
            action.run();
        }
    }

    private void adjustFocused(int direction) {
        Actor focused = stage.getKeyboardFocus();
        if (focused == resolution) {
            select(resolution, direction);
        } else if (focused == quality) {
            select(quality, direction);
        } else if (focused == masterVolume) {
            masterVolume.setValue(masterVolume.getValue() + direction * .05f);
        } else if (focused == musicVolume) {
            musicVolume.setValue(musicVolume.getValue() + direction * .05f);
        } else if (focused == effectsVolume) {
            effectsVolume.setValue(effectsVolume.getValue() + direction * .05f);
        }
    }

    private static void select(SelectBox<String> selectBox, int direction) {
        int size = selectBox.getItems().size;
        if (size > 0) {
            selectBox.setSelectedIndex(Math.floorMod(selectBox.getSelectedIndex() + direction, size));
        }
    }

    private static String percent(float value) {
        return Math.round(value * 100f) + "%";
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
        stage.dispose();
        skin.dispose();
    }

    /** Keeps controls reachable by keyboard without stealing native Scene2D control input. */
    private final class SettingsKeyboard extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            // Once a bind button has entered capture mode, the next input must be the captured
            // key rather than a click that activates another setting behind the prompt.
            return capturingAction != null;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            return capturingAction != null;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (capturingAction != null) {
                if (keycode == Input.Keys.ESCAPE) {
                    capturingAction = null;
                    status.setText("Rebind cancelled.");
                } else {
                    finishCapture(keycode);
                }
                return true;
            }
            if (keycode == Input.Keys.TAB) {
                boolean reverse = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                moveFocus(reverse ? -1 : 1);
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                back.run();
                return true;
            }
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                activateFocused();
                return buttonActions.containsKey(stage.getKeyboardFocus());
            }
            if (keycode == Input.Keys.SPACE && stage.getKeyboardFocus() == vsync) {
                vsync.setChecked(!vsync.isChecked());
                return true;
            }
            if (keycode == Input.Keys.SPACE && stage.getKeyboardFocus() == fullscreen) {
                fullscreen.setChecked(!fullscreen.isChecked());
                return true;
            }
            if (keycode == Input.Keys.LEFT) {
                adjustFocused(-1);
                return stage.getKeyboardFocus() == resolution || stage.getKeyboardFocus() == quality
                    || stage.getKeyboardFocus() instanceof Slider;
            }
            if (keycode == Input.Keys.RIGHT) {
                adjustFocused(1);
                return stage.getKeyboardFocus() == resolution || stage.getKeyboardFocus() == quality
                    || stage.getKeyboardFocus() instanceof Slider;
            }
            if (keycode == Input.Keys.UP) {
                moveFocus(-1);
                return true;
            }
            if (keycode == Input.Keys.DOWN) {
                moveFocus(1);
                return true;
            }
            return false;
        }
    }
}
