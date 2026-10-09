package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.HudSurveillance;
import io.github.skystrike.shared.model.Player;

/**
 * The surveillance banner, top centre: what device the player is looking through, its remaining
 * hit points, and the controls that still work (playable build plan M4 §5's HUD family).
 *
 * <p>Everything drawn here comes from {@link HudSurveillance} reading the predicted player in
 * {@link HudFrame} — the same rule every other widget follows: the HUD cannot disagree with
 * prediction, because it has no other player to read. While the view is the body's own eyes the
 * banner is simply absent, so an unsupervised player's screen is unchanged.
 */
final class SurveillanceBanner {

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    private float centerX;
    private float topY;
    private float scale;

    SurveillanceBanner(HudTheme theme) {
        this.theme = theme;
    }

    /** Top-centre plate, laid out once per resize. */
    void layout(int screenWidth, int screenHeight, float scale) {
        this.scale = scale;
        this.centerX = screenWidth / 2f;
        this.topY = theme.margin * scale;
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        Player player = frame.player();
        if (!HudSurveillance.visible(player)) {
            return;
        }
        float width = plateWidth(font, player);
        float pad = theme.innerPadding * scale;
        float lineHeight = font.getLineHeight();
        float height = lineHeight * 2f + pad * 2f;

        shapes.setColor(theme.panelFill());
        shapes.rect(centerX - width / 2f, topY, width, height);
        float edge = Math.max(1f, 1.5f * scale);
        shapes.setColor(theme.slotEdgeActive);
        shapes.rect(centerX - width / 2f, topY, width, edge);
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        Player player = frame.player();
        if (!HudSurveillance.visible(player)) {
            return;
        }
        String title = HudSurveillance.title(player);
        String health = HudSurveillance.healthText(player);
        String controls = HudSurveillance.controlsText();
        float pad = theme.innerPadding * scale;
        float lineHeight = font.getLineHeight();
        float width = plateWidth(font, player);
        float x = centerX - width / 2f;

        float titleBaseline = topY + pad + lineHeight * 0.8f;
        font.setColor(theme.text(theme.textAccent));
        font.draw(batch, title, x + pad, titleBaseline);
        if (!health.isEmpty()) {
            font.setColor(theme.text(theme.textPrimary));
            measurer.setText(font, health);
            font.draw(batch, health, x + width - pad - measurer.width, titleBaseline);
        }

        font.setColor(theme.text(theme.textDim));
        font.draw(batch, controls, x + pad, titleBaseline + lineHeight);
    }

    /** The plate's width: the widest line it will print, plus padding. */
    private float plateWidth(BitmapFont font, Player player) {
        float pad = theme.innerPadding * scale;
        measurer.setText(font, HudSurveillance.title(player));
        float widest = measurer.width;
        measurer.setText(font, HudSurveillance.healthText(player));
        widest = Math.max(widest, measurer.width);
        measurer.setText(font, HudSurveillance.controlsText());
        widest = Math.max(widest, measurer.width);
        return widest + pad * 2f;
    }
}
