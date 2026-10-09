package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.skystrike.shared.hud.DamageNumberModel;
import io.github.skystrike.shared.hud.WorldProjection;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import java.util.List;

/**
 * Floating damage numbers, drawn where the round landed (roadmap Phase 7 HUD, mechanics §10).
 *
 * <p>The only world-anchored widget in the HUD. {@link DamageNumberModel} decides which hits
 * become numbers and how they age; this class projects each anchor through the frame's
 * {@link WorldProjection} and draws the text. A number rises a little and fades, so it reads
 * without covering the thing it describes.
 *
 * <p>Colour carries two things at once. A headshot is gold and a killing hit is red, the same
 * recolouring the hit marker uses. Any other hit is tinted by its falloff ratio, from dim at the
 * edge of the weapon's range to full white point-blank, so a long shot reads as weaker at a
 * glance. Every colour goes through {@link HudTheme#text(Color)}, the console's contrast clamp,
 * so the numbers keep the same legibility guarantee as the rest of the HUD.
 *
 * <p>Text only: the shapes pass draws nothing for this widget. Numbers have no backing plate,
 * because the font's baked outline is what keeps them legible over an arbitrary wall.
 */
final class DamageNumberWidget {

    /** Anything projected further off-screen than this (design pixels) is skipped. */
    private static final float CULL_MARGIN_DESIGN = 48f;

    /** The dim end of the falloff tint: a long-range graze. */
    private static final Color FAR_TINT = new Color(0.62f, 0.66f, 0.74f, 1f);

    private final HudTheme theme;
    private final DamageNumberModel model;
    private final GlyphLayout measurer = new GlyphLayout();

    private float scale;

    DamageNumberWidget(HudTheme theme, DamageNumberModel model) {
        this.theme = theme;
        this.model = model;
    }

    void layout(float scale) {
        this.scale = scale;
    }

    /** Files one hit the local player dealt. Self-damage and damage taken are filtered out. */
    void onDealt(PacketDamageEvent damage, long nowMillis) {
        model.add(damage, nowMillis);
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        WorldProjection view = frame.worldView();
        if (view == null) {
            return;
        }
        List<DamageNumberModel.Entry> numbers = model.visible(frame.nowMillis());
        float cull = CULL_MARGIN_DESIGN * scale;
        for (DamageNumberModel.Entry number : numbers) {
            if (!view.onScreen(number.worldX(), number.worldY(), cull)) {
                continue;
            }
            String text = String.valueOf(number.amount());
            measurer.setText(font, text);
            float x = view.toScreenX(number.worldX()) - measurer.width / 2f;
            float baseY = view.toScreenY(number.worldY() + number.riseUnits(frame.nowMillis()));

            Color base = colorFor(number);
            float alpha = number.alpha(frame.nowMillis());
            font.setColor(theme.text(new Color(base.r, base.g, base.b, base.a * alpha)));
            font.draw(batch, text, x, baseY);
        }
    }

    /** Killing blow, then headshot, then the falloff ramp from {@link #FAR_TINT} to white. */
    private Color colorFor(DamageNumberModel.Entry number) {
        if (number.killed()) {
            return theme.hitMarkerLethal;
        }
        if (number.headshot()) {
            return theme.hitMarkerHeadshot;
        }
        float t = Math.min(1f, Math.max(0f, number.falloffRatio()));
        return new Color(
            FAR_TINT.r + (theme.textPrimary.r - FAR_TINT.r) * t,
            FAR_TINT.g + (theme.textPrimary.g - FAR_TINT.g) * t,
            FAR_TINT.b + (theme.textPrimary.b - FAR_TINT.b) * t,
            1f);
    }
}
