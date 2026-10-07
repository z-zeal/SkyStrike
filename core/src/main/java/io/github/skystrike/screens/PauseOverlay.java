package io.github.skystrike.screens;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.input.InputRouter;
import java.util.function.Consumer;

/**
 * Visible modal pause menu. It is deliberately the top {@link InputRouter} focus, so Escape has
 * one owner and none of the underlying gameplay, console or picker input can observe it.
 */
public final class PauseOverlay extends InputAdapter implements Disposable {

    private static final String[] ACTIONS = {"Resume", "Loadout", "Settings", "Disconnect"};
    private static final float PANEL_WIDTH = 260f;
    private static final float ROW_HEIGHT = 38f;

    private final InputRouter router;
    private final Consumer<String> action;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    private int width = 1;
    private int height = 1;
    private int selected;
    private boolean open;
    private boolean disposed;

    public PauseOverlay(InputRouter router, Consumer<String> action) {
        if (router == null || action == null) {
            throw new IllegalArgumentException("router and action are required");
        }
        this.router = router;
        this.action = action;
    }

    /** Takes topmost input focus. Calling it twice cannot add a second Escape owner. */
    public void open() {
        if (open) {
            return;
        }
        open = true;
        router.pushFocus(this);
    }

    public boolean isOpen() {
        return open;
    }

    public void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        projection.setToOrtho2D(0f, 0f, this.width, this.height);
    }

    /** Draws the dimmer and the four clickable actions over the paused match. */
    public void render() {
        if (!open || disposed) {
            return;
        }
        float panelHeight = ROW_HEIGHT * ACTIONS.length + 60f;
        float panelX = (width - PANEL_WIDTH) / 2f;
        float panelY = (height - panelHeight) / 2f;

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, .60f);
        shapes.rect(0f, 0f, width, height);
        shapes.setColor(.06f, .08f, .12f, .97f);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelHeight);
        shapes.setColor(.38f, .78f, 1f, 1f);
        shapes.rect(panelX, panelY + panelHeight - 2f, PANEL_WIDTH, 2f);
        for (int i = 0; i < ACTIONS.length; i++) {
            float rowY = rowY(panelY, i);
            shapes.setColor(i == selected ? .16f : .09f, i == selected ? .26f : .12f,
                i == selected ? .38f : .18f, .98f);
            shapes.rect(panelX + 12f, rowY, PANEL_WIDTH - 24f, ROW_HEIGHT - 4f);
        }
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "PAUSED", panelX + 18f, panelY + panelHeight - 20f);
        for (int i = 0; i < ACTIONS.length; i++) {
            font.setColor(i == selected ? new Color(.55f, .95f, .82f, 1f) : Color.WHITE);
            font.draw(batch, ACTIONS[i], panelX + 28f, rowY(panelY, i) + 25f);
        }
        font.setColor(Color.WHITE);
        batch.end();
    }

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
            selected = Math.floorMod(selected - 1, ACTIONS.length);
        } else if (keycode == Input.Keys.DOWN) {
            selected = (selected + 1) % ACTIONS.length;
        } else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
            choose(selected);
        }
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!open) {
            return false;
        }
        float panelHeight = ROW_HEIGHT * ACTIONS.length + 60f;
        float panelX = (width - PANEL_WIDTH) / 2f;
        float panelY = (height - panelHeight) / 2f;
        float x = screenX;
        float y = height - screenY;
        if (x < panelX + 12f || x > panelX + PANEL_WIDTH - 12f) {
            return true;
        }
        for (int i = 0; i < ACTIONS.length; i++) {
            float rowY = rowY(panelY, i);
            if (y >= rowY && y <= rowY + ROW_HEIGHT - 4f) {
                choose(i);
                return true;
            }
        }
        return true;
    }

    private float rowY(float panelY, int index) {
        return panelY + ROW_HEIGHT * (ACTIONS.length - 1 - index) + 12f;
    }

    private void choose(int index) {
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
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}
