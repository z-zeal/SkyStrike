package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import java.util.List;

/**
 * A plain screen-space text readout.
 *
 * <p>Deliberately not the HUD: it exists so Phase 0 has visible proof the connection and the tick
 * loop are alive, and it is replaced wholesale by {@code ui/hud} in Phase 7.
 */
public final class StatusOverlay implements Disposable {

    private static final float MARGIN = 14f;
    private static final float LINE_SPACING = 4f;

    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    private int screenWidth = 1;
    private int screenHeight = 1;

    public StatusOverlay() {
        font.setColor(Color.WHITE);
    }

    /** The layer this overlay belongs to. */
    public RenderLayers layer() {
        return RenderLayers.HUD;
    }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        projection.setToOrtho2D(0f, 0f, screenWidth, screenHeight);
    }

    public void render(List<String> lines) {
        batch.setProjectionMatrix(projection);
        batch.begin();
        float y = screenHeight - MARGIN;
        for (String line : lines) {
            font.draw(batch, line, MARGIN, y);
            y -= font.getLineHeight() + LINE_SPACING;
        }
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
