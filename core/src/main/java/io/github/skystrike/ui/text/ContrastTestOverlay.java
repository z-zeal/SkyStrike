package io.github.skystrike.ui.text;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.ui.console.ConsoleTheme;
import java.util.List;

/**
 * {@code ui_contrast_test} (build plan M3 §4, F12): the console plan §5.2 four-background
 * legibility check, flagged missing by {@code PHASE7_AUDIT.md}.
 *
 * <p>Tiles the screen into the four mandatory previews — pure black, pure white, mid grey and
 * high-frequency noise — and on each draws the dialog's own backing panel
 * ({@link ConsoleTheme#panelFill}) and its luminance-clamped text colour
 * ({@link ConsoleTheme#clampedText}), so this exercises exactly the values the real console
 * draws rather than a separately-tuned approximation. "Readable on all four means readable in
 * the game."
 */
public final class ContrastTestOverlay implements Disposable {

    private static final String SAMPLE_LINE = "The quick brown fox jumps over 0123";
    private static final Color SHADOW_COLOR = new Color(0f, 0f, 0f, 0.5f);
    private static final Color NOISE_DARK = new Color(0.08f, 0.08f, 0.08f, 1f);
    private static final Color NOISE_LIGHT = new Color(0.92f, 0.92f, 0.92f, 1f);
    private static final float NOISE_CELL = 6f;

    private final ConsoleTheme theme = new ConsoleTheme();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    private int screenWidth = 1;
    private int screenHeight = 1;

    public ContrastTestOverlay() {
        font.setColor(Color.WHITE);
    }

    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        projection.setToOrtho2D(0f, 0f, screenWidth, screenHeight);
    }

    public void render() {
        List<TextContrast.PreviewBackground> backgrounds = TextContrast.requiredPreviewBackgrounds();
        float quadW = screenWidth / 2f;
        float quadH = screenHeight / 2f;

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < backgrounds.size(); i++) {
            float qx = (i % 2) * quadW;
            float qy = (i / 2) * quadH;
            drawQuadrantBackground(backgrounds.get(i), qx, qy, quadW, quadH);
            drawPanel(qx, qy, quadW, quadH);
        }
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        for (int i = 0; i < backgrounds.size(); i++) {
            float qx = (i % 2) * quadW;
            float qy = (i / 2) * quadH;
            drawText(backgrounds.get(i), qx, qy, quadW, quadH);
        }
        batch.end();
    }

    private void drawQuadrantBackground(
            TextContrast.PreviewBackground background, float qx, float qy, float quadW, float quadH) {
        if (background == TextContrast.PreviewBackground.NOISE) {
            drawNoise(qx, qy, quadW, quadH);
            return;
        }
        shapes.setColor(background.representativeColor());
        shapes.rect(qx, qy, quadW, quadH);
    }

    /** High-frequency noise: alternating light/dark cells, deterministic so repeated frames agree. */
    private void drawNoise(float qx, float qy, float quadW, float quadH) {
        int cols = Math.max(1, (int) (quadW / NOISE_CELL));
        int rows = Math.max(1, (int) (quadH / NOISE_CELL));
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean light = ((col * 73856093) ^ (row * 19349663)) % 2 == 0;
                shapes.setColor(light ? NOISE_LIGHT : NOISE_DARK);
                shapes.rect(qx + col * NOISE_CELL, qy + row * NOISE_CELL, NOISE_CELL, NOISE_CELL);
            }
        }
    }

    private void drawPanel(float qx, float qy, float quadW, float quadH) {
        float panelW = quadW * 0.82f;
        float panelH = quadH * 0.28f;
        float panelX = qx + (quadW - panelW) / 2f;
        float panelY = qy + (quadH - panelH) / 2f;
        shapes.setColor(theme.panelFill());
        shapes.rect(panelX, panelY, panelW, panelH);
    }

    private void drawText(
            TextContrast.PreviewBackground background, float qx, float qy, float quadW, float quadH) {
        float textX = qx + quadW * 0.12f;
        float labelY = qy + quadH * 0.72f;
        float sampleY = qy + quadH * 0.5f;

        Color clamped = theme.clampedText(theme.inputTextNormal);

        // A manual shadow/outline stand-in (console plan §5.2 layers 2-3): the real dialog bakes
        // these into the glyphs via FontManager when a custom font asset is present; the stock
        // fallback font used here and by the dialog itself has neither, so this overlay draws the
        // same one-pixel offset shadow ConsoleTheme's drop-shadow layer specifies.
        font.setColor(SHADOW_COLOR);
        font.draw(batch, background.name(), textX + 1f, labelY - 1f);
        font.draw(batch, SAMPLE_LINE, textX + 1f, sampleY - 1f);

        font.setColor(clamped);
        font.draw(batch, background.name(), textX, labelY);
        font.draw(batch, SAMPLE_LINE, textX, sampleY);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
