package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.settings.ClientPreferences;
import io.github.skystrike.shared.settings.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * In-game settings dialog opened from the pause menu. Unlike the main-menu
 * {@link SettingsScreen} which is a full {@code ManagedScreen} routed by {@code Main},
 * this dialog is a true modal overlay similar to {@link PauseOverlay}: it lives inside
 * {@link GameScreen}, owns an {@link InputRouter} focus entry, dims the game, and is
 * not draggable. It reuses the same {@link Settings}, {@link KeyBindings} and
 * {@link Skin} architecture so there is no second UI framework or parallel settings stack.
 *
 * <p>Visual hierarchy intentionally mirrors both pause and settings: dim + centred
 * {@code window}-background panel, heading, grouped Video/Audio controls, scrollable Controls,
 * status line and Back/Close. Behaviour mirrors the existing settings screen: resolution,
 * vsync/fullscreen/quality, three volume sliders, rebind capture with Escape-to-cancel,
 * and live persistence via {@link ClientPreferences} plus {@code Gdx.graphics} apply
 * (vsync/fullscreen). The shared {@code KeyBindings} instance remains the source of truth
 * for the console, gameplay and this dialog.
 */
public final class GameSettingsDialog implements Disposable {

    private static final float LABEL_WIDTH = 170f;
    private static final float CONTROL_WIDTH = 260f;
    private static final float PANEL_WIDTH = 720f;
    private static final float SCROLL_WIDTH = 660f;
    private static final float SCROLL_HEIGHT = 230f;

    private final InputRouter router;
    private final Settings settings;
    private final KeyBindings bindings;
    private final ClientPreferences preferences = new ClientPreferences();

    private final Skin skin;
    private final Stage stage;
    private final InputMultiplexer input = new InputMultiplexer();
    private final List<Actor> keyboardOrder = new ArrayList<>();
    private final List<Actor> scrollableControlButtons = new ArrayList<>();
    private final Map<Actor, Runnable> buttonActions = new LinkedHashMap<>();
    private final Map<String, TextButton> bindingButtons = new LinkedHashMap<>();
    private ScrollPane controlsScroll;

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
    private boolean open;
    private boolean disposed;

    public GameSettingsDialog(InputRouter router, Settings settings, KeyBindings bindings) {
        if (router == null || settings == null || bindings == null) {
            throw new IllegalArgumentException("router, settings and bindings are required");
        }
        this.router = router;
        this.settings = settings;
        this.bindings = bindings;
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
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
        // Dim behind the dialog, similar to PauseOverlay (60% black) but with touch disabled
        // so clicks are handled by the dialog's own stage actors; the router focus prevents
        // click-through to gameplay.
        Image dimmer = new Image(skin.getDrawable("black"));
        dimmer.setColor(1f, 1f, 1f, .60f);
        dimmer.setFillParent(true);
        dimmer.setTouchable(Touchable.disabled);
        stage.addActor(dimmer);

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Table panel = new Table();
        panel.setBackground(skin.getDrawable("window"));
        panel.pad(20f);
        // Give the dialog a clear visual hierarchy like both pause and settings: centred
        // panel with heading, grouped options, scrollable key binds, footer.
        panel.add(new Label("SETTINGS", skin)).left().padBottom(10f);
        panel.row();

        Table options = new Table();
        options.top().left();
        addHeading(options, "Video");
        addOption(options, "Resolution", resolution);
        addOption(options, "", vsync);
        addOption(options, "", fullscreen);
        addOption(options, "Quality", quality);
        addHeading(options, "Audio");
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

        panel.add(options).left().fillX();
        panel.row();
        panel.add(new Label("Controls", skin)).left().padTop(10f).padBottom(5f);
        panel.row();

        Table controls = new Table();
        controls.top().left();
        for (String actionName : bindings.actionNames()) {
            addBindingRow(controls, actionName);
        }
        controlsScroll = new ScrollPane(controls, skin);
        controlsScroll.setFadeScrollBars(false);
        controlsScroll.setScrollingDisabled(true, false);
        panel.add(controlsScroll).left().width(SCROLL_WIDTH).height(SCROLL_HEIGHT).fill();
        panel.row();

        Table footer = new Table();
        footer.add(status).left().width(500f).padRight(14f);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                close();
            }
        });
        buttonActions.put(backButton, this::close);
        keyboardOrder.add(backButton);
        footer.add(backButton).width(126f).height(36f).right();
        panel.add(footer).left().fillX().padTop(12f);

        root.add(panel).width(PANEL_WIDTH);
    }

    private void addHeading(Table table, String heading) {
        table.add(new Label(heading, skin)).left().colspan(3).padTop(4f).padBottom(5f);
        table.row();
    }

    private void addOption(Table table, String label, Actor control) {
        table.add(new Label(label, skin)).left().width(LABEL_WIDTH).padBottom(7f);
        table.add(control).left().width(CONTROL_WIDTH).height(34f).padBottom(7f);
        table.add().width(60f);
        table.row();
    }

    private void addVolumeOption(Table table, String label, Slider slider, Label value) {
        table.add(new Label(label, skin)).left().width(LABEL_WIDTH).padBottom(7f);
        table.add(slider).left().width(CONTROL_WIDTH).height(28f).padBottom(7f);
        table.add(value).left().width(60f).padLeft(8f).padBottom(7f);
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
        scrollableControlButtons.add(rebind);
        scrollableControlButtons.add(reset);

        table.add(new Label(actionName, skin)).left().expandX().fillX().padBottom(5f);
        table.add(rebind).width(210f).height(32f).padRight(6f).padBottom(5f);
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
        preferences.saveSettings(settings);
        applyGraphicsSettings(settings);
        status.setText(confirmation);
    }

    private static void applyGraphicsSettings(Settings changed) {
        if (Gdx.graphics == null) {
            return;
        }
        Gdx.graphics.setVSync(changed.vsync);
        if (changed.fullscreen) {
            Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
            if (displayMode != null) {
                Gdx.graphics.setFullscreenMode(displayMode);
            }
        } else {
            Gdx.graphics.setWindowedMode(changed.width, changed.height);
        }
    }

    private void beginCapture(String actionName) {
        capturingAction = actionName;
        status.setText("Press a key for " + actionName + ", or Escape to cancel.");
        TextButton button = bindingButtons.get(actionName);
        if (button != null) {
            setFocus(button);
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
        setFocus(keyboardOrder.get(Math.floorMod(index + direction, keyboardOrder.size())));
    }

    private void setFocus(Actor actor) {
        stage.setKeyboardFocus(actor);
        if (controlsScroll != null && scrollableControlButtons.contains(actor)) {
            controlsScroll.scrollTo(
                actor.getX(), actor.getY(), actor.getWidth(), actor.getHeight(), false, true);
        }
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

    /** Takes topmost focus, similar to {@link PauseOverlay#open()}. */
    public void open() {
        if (open || disposed) {
            return;
        }
        open = true;
        // Refresh from live settings in case they were changed elsewhere (main-menu SettingsScreen)
        resolution.setSelected(settings.width + "x" + settings.height);
        vsync.setChecked(settings.vsync);
        fullscreen.setChecked(settings.fullscreen);
        quality.setSelectedIndex(settings.qualityTier);
        masterVolume.setValue(settings.masterVolume);
        musicVolume.setValue(settings.musicVolume);
        effectsVolume.setValue(settings.effectsVolume);
        masterValue.setText(percent(masterVolume.getValue()));
        musicValue.setText(percent(musicVolume.getValue()));
        effectsValue.setText(percent(effectsVolume.getValue()));
        for (String actionName : bindings.actionNames()) {
            TextButton b = bindingButtons.get(actionName);
            if (b != null) {
                b.setText(bindings.keyNameFor(actionName));
            }
        }
        status.setText("Use Tab to move focus. Choose Rebind to capture a key.");
        capturingAction = null;
        router.pushFocus(input);
        stage.setKeyboardFocus(resolution);
    }

    /** Releases topmost focus; caller (pause) must have already been popped if needed. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        capturingAction = null;
        router.popFocus();
    }

    public boolean isOpen() {
        return open;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void render(float delta) {
        if (!open || disposed) {
            return;
        }
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (open) {
            open = false;
            router.popFocus();
        }
        stage.dispose();
        skin.dispose();
    }

    private final class SettingsKeyboard extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            return capturingAction != null;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            return capturingAction != null;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (!open) {
                return false;
            }
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
                close();
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
