package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.skystrike.input.InputRouter;
import java.util.function.Consumer;

/**
 * Visible Scene2D pause modal. Its input multiplexer is the one top focus in {@link InputRouter},
 * so Escape remains owned by the focus stack rather than by an independent pause poller.
 */
public final class PauseOverlay implements Disposable {

    private static final String[] ACTIONS = {"Resume", "Loadout", "Settings", "Disconnect"};

    private final InputRouter router;
    private final Consumer<String> action;
    private final Skin skin;
    private final Stage stage;
    private final InputMultiplexer input = new InputMultiplexer();
    private final TextButton[] buttons = new TextButton[ACTIONS.length];

    private int selected;
    private boolean open;
    private boolean disposed;

    public PauseOverlay(InputRouter router, Consumer<String> action) {
        if (router == null || action == null) {
            throw new IllegalArgumentException("router and action are required");
        }
        this.router = router;
        this.action = action;
        this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        this.stage = new Stage(new ScreenViewport());
        buildLayout();
        input.addProcessor(new PauseKeyboard());
        input.addProcessor(stage);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void buildLayout() {
        Image dimmer = new Image(skin.getDrawable("black"));
        dimmer.setColor(1f, 1f, 1f, .60f);
        dimmer.setFillParent(true);
        dimmer.setTouchable(Touchable.disabled);
        stage.addActor(dimmer);

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Table panel = new Table();
        panel.setBackground(skin.getDrawable("window"));
        panel.pad(20f);
        panel.add(new Label("PAUSED", skin)).left().padBottom(14f);
        panel.row();
        for (int i = 0; i < ACTIONS.length; i++) {
            int index = i;
            TextButton button = new TextButton(ACTIONS[i], skin);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    choose(index);
                }
            });
            buttons[i] = button;
            panel.add(button).width(236f).height(40f).padBottom(7f);
            panel.row();
        }
        root.add(panel).width(290f);
    }

    /** Takes topmost input focus. Calling it twice cannot add a second Escape owner. */
    public void open() {
        if (open || disposed) {
            return;
        }
        open = true;
        selected = 0;
        stage.setKeyboardFocus(buttons[selected]);
        router.pushFocus(input);
    }

    public boolean isOpen() {
        return open;
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    /** Draws the Scene2D dimmer, panel and clickable controls over the paused match. */
    public void render(float delta) {
        if (!open || disposed) {
            return;
        }
        stage.act(delta);
        stage.draw();
    }

    private void moveSelection(int direction) {
        selected = Math.floorMod(selected + direction, buttons.length);
        stage.setKeyboardFocus(buttons[selected]);
    }

    private void choose(int index) {
        if (!open) {
            return;
        }
        selected = index;
        // Release focus before routing. A route can synchronously hide/dispose its game screen,
        // but the pause focus must never survive that transition.
        open = false;
        router.popFocus();
        action.accept(ACTIONS[index]);
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

    /** Keyboard shortcuts are convenience routes to the same real button actions. */
    private final class PauseKeyboard extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (!open) {
                return false;
            }
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.R) {
                choose(0);
            } else if (keycode == Input.Keys.L) {
                choose(1);
            } else if (keycode == Input.Keys.S) {
                choose(2);
            } else if (keycode == Input.Keys.D) {
                choose(3);
            } else if (keycode == Input.Keys.UP) {
                moveSelection(-1);
            } else if (keycode == Input.Keys.DOWN) {
                moveSelection(1);
            } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                || keycode == Input.Keys.SPACE) {
                choose(selected);
            } else {
                return false;
            }
            return true;
        }
    }
}
