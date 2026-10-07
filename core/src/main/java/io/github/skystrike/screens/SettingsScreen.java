package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.skystrike.shared.settings.Settings;

/** Settings front-end. Video fields are validated before being persisted/applied. */
public final class SettingsScreen extends ScreenAdapter {
    private final Settings settings;
    private final Runnable back;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    public SettingsScreen(Settings settings, Runnable back) { this.settings = settings; this.back = back; }
    @Override public void show() {
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key == Input.Keys.ESCAPE) { back.run(); return true; }
                if (key == Input.Keys.LEFT) settings.qualityTier--;
                if (key == Input.Keys.RIGHT) settings.qualityTier++;
                settings.validate(); return true;
            }
        });
    }
    @Override public void render(float delta) {
        Gdx.gl.glClearColor(.025f, .035f, .06f, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); font.draw(batch, "SETTINGS", 80, 420);
        font.draw(batch, "Quality tier: " + settings.qualityTier + "  (left/right)", 80, 360);
        font.draw(batch, "Resolution: " + settings.width + "x" + settings.height, 80, 328);
        font.draw(batch, "Escape: back", 80, 260); batch.end();
    }
    @Override public void dispose() { batch.dispose(); font.dispose(); }
}
