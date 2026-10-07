package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.CrosshairMath;
import io.github.skystrike.shared.model.Player;

/**
 * The crosshair: four arms whose gap tracks where the next round can actually go, plus the hit
 * marker (playable build plan M4 §5).
 *
 * <p>The gap is {@code f(Player.spread, Player.gunKick)} — the same two numbers the server's
 * ballistics perturb a shot by — so widening is not decoration: a crosshair that has bloomed is
 * telling the truth about the cone. {@link CrosshairMath} owns that function in {@code shared},
 * next to the tests that pin it; this class turns the result into four rectangles.
 *
 * <p>ADS has its own form: the arms draw thinner and closer (the multiplier, not a second
 * formula) and a centre dot appears, so the sighted state is unmistakable at a glance.
 */
final class Crosshair {

    private final HudTheme theme;

    private float centerX;
    private float centerY;
    private float scale;

    Crosshair(HudTheme theme) {
        this.theme = theme;
    }

    void layout(int screenWidth, int screenHeight, float scale) {
        this.centerX = screenWidth / 2f;
        this.centerY = screenHeight / 2f;
        this.scale = scale;
    }

    void drawShapes(ShapeRenderer shapes, HudFrame frame) {
        Player player = frame.player();
        if (player == null || !player.alive) {
            return;
        }
        float adsAlpha = frame.adsAlpha();
        boolean ads = CrosshairMath.adsForm(adsAlpha);
        // The gap is quoted in design pixels, so the arms grow with it before scaling.
        float designGap = CrosshairMath.gapPixels(player, adsAlpha);
        float gap = designGap * scale;
        float arm = CrosshairMath.armPixels(designGap) * scale;
        float thickness = Math.max(1f, theme.crosshairThickness * scale * (ads ? 0.75f : 1f));

        Color color = ads ? theme.crosshairAds : theme.crosshair;
        shapes.setColor(color);
        arms(shapes, gap, arm, thickness);

        if (ads) {
            float dot = Math.max(1f, thickness);
            shapes.rect(centerX - dot / 2f, centerY - dot / 2f, dot, dot);
        }

        if (frame.hitMarkerActive()) {
            drawHitMarker(shapes, frame);
        }
    }

    private void arms(ShapeRenderer shapes, float gap, float arm, float thickness) {
        // Left, right, down, up. Rectangles rather than lines: ShapeRenderer line width is not
        // portable, and the HUD must look identical on every backend.
        shapes.rect(centerX - gap - arm, centerY - thickness / 2f, arm, thickness);
        shapes.rect(centerX + gap, centerY - thickness / 2f, arm, thickness);
        shapes.rect(centerX - thickness / 2f, centerY - gap - arm, thickness, arm);
        shapes.rect(centerX - thickness / 2f, centerY + gap, thickness, arm);
    }

    /**
     * The confirmation X. It expands as it fades, which is what makes a hit register
     * peripherally; a headshot and a kill recolour it rather than redrawing it bigger, so the
     * shape stays one thing the eye has learned.
     */
    private void drawHitMarker(ShapeRenderer shapes, HudFrame frame) {
        float alpha = Math.min(1f, Math.max(0f, frame.hitMarkerAlpha()));
        Color base = frame.hitMarkerLethal() ? theme.hitMarkerLethal
            : frame.hitMarkerHeadshot() ? theme.hitMarkerHeadshot : theme.hitMarker;
        shapes.setColor(base.r, base.g, base.b, base.a * alpha);

        float gap = CrosshairMath.hitMarkerGapPixels(alpha) * scale;
        float arm = CrosshairMath.HIT_MARKER_ARM_PIXELS * scale;
        float thickness = Math.max(1f, theme.crosshairThickness * scale);
        float diagonal = (float) (1.0 / Math.sqrt(2.0));
        float step = thickness * 0.5f;

        // Four diagonal strokes, stepped out of small squares so no rotation matrix is needed.
        for (int corner = 0; corner < 4; corner++) {
            float dirX = (corner == 0 || corner == 3) ? -1f : 1f;
            float dirY = (corner < 2) ? 1f : -1f;
            for (float d = 0f; d <= arm; d += step) {
                float px = centerX + dirX * (gap + d) * diagonal;
                float py = centerY + dirY * (gap + d) * diagonal;
                shapes.rect(px - thickness / 2f, py - thickness / 2f, thickness, thickness);
            }
        }
    }
}
