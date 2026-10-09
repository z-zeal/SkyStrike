package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.List;

/**
 * The status readout, behind {@code cl_debug_overlay} (F1) — playable build plan M4 §5.
 *
 * <p>This is what {@code render/StatusOverlay} used to be, absorbed into the HUD as the plan
 * requires. Two things changed in the move. It no longer owns a batch, a font or a projection
 * matrix: it draws into the HUD's single viewport/batch pair like every other widget, so the
 * debug readout can never disagree with the HUD about where the screen is. And the lines now
 * sit on the console's panel fill in its clamped text colour, which is what makes a readout of
 * white-on-whatever legible over a bright sky.
 *
 * <p>The content is still {@code GameScreen.statusLines()} — the composition root knows what is
 * worth printing; this only prints it.
 *
 * <p>It shares the top-left corner with the minimap, so it takes an inset rather than a position:
 * when the map is on it starts below the map, and when the map is off it moves back into the
 * corner. Neither widget knows the other exists — {@link HudStage} hands down the number.
 */
final class DebugPanel {

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    private float x;
    private float top;
    private float scale;

    DebugPanel(HudTheme theme) {
        this.theme = theme;
    }

    /**
     * @param topInset how much of the top-left corner the minimap is using, so the readout starts
     *                 below it instead of drawing through it. Zero when the map is off.
     */
    void layout(int screenWidth, int screenHeight, float scale, float topInset) {
        this.scale = scale;
        this.x = theme.margin * scale;
        this.top = screenHeight - topInset - theme.margin * scale;
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        List<String> lines = frame.debugLines();
        if (!frame.debugPanelVisible() || lines.isEmpty()) {
            return;
        }
        float pad = theme.innerPadding * scale;
        float lineHeight = font.getLineHeight() + theme.lineGap * scale;
        float width = 0f;
        for (String line : lines) {
            measurer.setText(font, line);
            width = Math.max(width, measurer.width);
        }
        float height = lines.size() * lineHeight + pad;
        shapes.setColor(theme.panelFill());
        shapes.rect(x - pad * 0.5f, top - height, width + pad * 1.5f, height + pad * 0.5f);
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        List<String> lines = frame.debugLines();
        if (!frame.debugPanelVisible() || lines.isEmpty()) {
            return;
        }
        float lineHeight = font.getLineHeight() + theme.lineGap * scale;
        float y = top - theme.innerPadding * scale * 0.5f;
        font.setColor(theme.text(theme.textPrimary));
        for (String line : lines) {
            font.draw(batch, line, x, y);
            y -= lineHeight;
        }
        font.setColor(Color.WHITE);
    }
}
