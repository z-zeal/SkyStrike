package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.InputAdapter;
import java.util.function.Consumer;

/** Minimal keyboard-first shell; the router owns screen lifetime and transitions. */
public final class MainMenuScreen extends ScreenAdapter {
    private final Consumer<String> action;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private int selected;
    private final String[] items = {"Play", "Loadout", "Settings", "Quit"};

    public MainMenuScreen(Consumer<String> action) {
        this.action = action;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.UP) selected = (selected + items.length - 1) % items.length;
                if (keycode == Input.Keys.DOWN) selected = (selected + 1) % items.length;
                if (keycode == Input.Keys.ENTER) action.accept(items[selected]);
                return true;
            }
        });
    }
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(0.025f, 0.035f, 0.06f, 1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); font.draw(batch, "SKYSTRIKE", 80, 420);
        for (int i = 0; i < items.length; i++) font.draw(batch, (i == selected ? "> " : "  ") + items[i], 100, 350 - i * 32);
        batch.end();
    }
    @Override public void dispose() { batch.dispose(); font.dispose(); }
}
