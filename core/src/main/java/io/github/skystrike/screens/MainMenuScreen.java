package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import java.util.function.Consumer;

/** Keyboard-first main menu. Connection fields are shown so launch properties are inspectable. */
public final class MainMenuScreen extends ScreenAdapter {
    private final Consumer<String> action;
    private final String name, host;
    private final int tcp, udp;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private int selected;
    private final String[] items = {"Play", "Loadout", "Settings", "Quit"};

    public MainMenuScreen(Consumer<String> action) {
        this(action, "Player", "127.0.0.1", 54555, 54556);
    }
    public MainMenuScreen(Consumer<String> action, String name, String host, int tcp, int udp) {
        this.action = action; this.name = name; this.host = host; this.tcp = tcp; this.udp = udp;
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Input.Keys.UP) selected = (selected + items.length - 1) % items.length;
                else if (key == Input.Keys.DOWN) selected = (selected + 1) % items.length;
                else if (key == Input.Keys.ENTER) action.accept(items[selected]);
                return true;
            }
        });
    }
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); font.draw(batch, "SKYSTRIKE", 80, 440);
        font.draw(batch, "Name: " + name + "  Host: " + host, 80, 405);
        font.draw(batch, "TCP: " + tcp + "  UDP: " + udp, 80, 380);
        for (int i = 0; i < items.length; i++) font.draw(batch,
            (i == selected ? "> " : "  ") + items[i], 100, 320 - i * 32);
        batch.end();
    }
    @Override public void dispose() { batch.dispose(); font.dispose(); }
}
