package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.DamageVignetteMath;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;

/**
 * The direction-tinted damage vignette (playable build plan M4 §5), driven by
 * {@link PacketDamageEvent}.
 *
 * <p>A damage event carries the impact point, so the edge that lights up is the bearing from
 * the victim to that point — the direction to turn. {@link DamageVignetteMath} decides the
 * bearing, the intensity and the decay in {@code shared}; this class holds the one live tint
 * and paints it as four edge gradients weighted by that bearing.
 *
 * <p>Damage with no usable bearing (fall damage, a fuel tank going up underneath you, a
 * point-blank melee) flashes all four edges evenly rather than inventing a direction.
 */
final class DamageVignette {

    private final HudTheme theme;

    private float directionDegrees;
    private float intensity;
    private float ageSeconds = Float.MAX_VALUE;
    private boolean directional;

    private int screenWidth = 1;
    private int screenHeight = 1;

    DamageVignette(HudTheme theme) {
        this.theme = theme;
    }

    void layout(int screenWidth, int screenHeight) {
        this.screenWidth = Math.max(1, screenWidth);
        this.screenHeight = Math.max(1, screenHeight);
    }

    /**
     * Files one incoming hit against the local player. A second hit while the first is still
     * visible does not average the two bearings — the stronger one wins, because the average of
     * "shot from the left" and "shot from the right" is a direction nobody is standing in.
     */
    void onDamage(PacketDamageEvent damage, Player victim) {
        if (damage == null || victim == null || damage.targetId != victim.id) {
            return;
        }
        float incoming = DamageVignetteMath.intensity(damage.amount);
        if (incoming <= 0f) {
            return;
        }
        float current = DamageVignetteMath.fade(ageSeconds) * intensity;
        directionDegrees = DamageVignetteMath.mergeDirection(
            directionDegrees, current,
            DamageVignetteMath.directionDegrees(damage, victim), incoming);
        directional = DamageVignetteMath.isDirectional(damage, victim)
            || (directional && current > incoming);
        intensity = Math.max(incoming, current);
        ageSeconds = 0f;
    }

    void update(float deltaSeconds) {
        if (ageSeconds < DamageVignetteMath.DURATION_SECONDS) {
            ageSeconds += Math.max(0f, deltaSeconds);
        }
    }

    /** Clears the tint on respawn: a new life does not inherit the last death's screen. */
    void reset() {
        intensity = 0f;
        ageSeconds = Float.MAX_VALUE;
        directional = false;
    }

    void drawShapes(ShapeRenderer shapes, HudFrame frame) {
        float alpha = DamageVignetteMath.fade(ageSeconds) * intensity;
        if (alpha <= 0.001f) {
            return;
        }
        Color tint = theme.damageTint;
        float band = Math.min(screenWidth, screenHeight) * 0.22f;

        // 0 right, 90 up, 180 left, -90 down: the aim-angle convention the whole game uses.
        float right = directional ? edgeWeight(directionDegrees, 0f) : 1f;
        float up = directional ? edgeWeight(directionDegrees, 90f) : 1f;
        float left = directional ? edgeWeight(directionDegrees, 180f) : 1f;
        float down = directional ? edgeWeight(directionDegrees, -90f) : 1f;

        edge(shapes, tint, alpha * left, 0f, 0f, band, screenHeight, true, false);
        edge(shapes, tint, alpha * right, screenWidth - band, 0f, band, screenHeight, true, true);
        edge(shapes, tint, alpha * down, 0f, 0f, screenWidth, band, false, false);
        edge(shapes, tint, alpha * up, 0f, screenHeight - band, screenWidth, band, false, true);
    }

    /**
     * How much of the tint one screen edge takes: full when the hit came from straight that
     * way, nothing at 90 degrees off it, so two adjacent edges share a diagonal hit.
     */
    private static float edgeWeight(float bearingDegrees, float edgeDegrees) {
        double delta = Math.toRadians(bearingDegrees - edgeDegrees);
        float cosine = (float) Math.cos(delta);
        return Math.max(0f, cosine);
    }

    /**
     * One edge gradient: opaque at the screen edge, transparent at the inner lip. Drawn with
     * {@code ShapeRenderer}'s four-corner colour form rather than a texture, so the vignette
     * costs one quad and no asset.
     */
    private void edge(
            ShapeRenderer shapes, Color tint, float alpha, float x, float y, float width,
            float height, boolean horizontal, boolean farSide) {
        if (alpha <= 0.001f) {
            return;
        }
        Color strong = new Color(tint.r, tint.g, tint.b, Math.min(1f, alpha));
        Color clear = new Color(tint.r, tint.g, tint.b, 0f);
        Color bottomLeft;
        Color bottomRight;
        Color topRight;
        Color topLeft;
        if (horizontal) {
            // Gradient runs across x: strong on the screen edge side.
            bottomLeft = farSide ? clear : strong;
            topLeft = farSide ? clear : strong;
            bottomRight = farSide ? strong : clear;
            topRight = farSide ? strong : clear;
        } else {
            // Gradient runs up y: strong on the top edge when farSide, else on the bottom.
            bottomLeft = farSide ? clear : strong;
            bottomRight = farSide ? clear : strong;
            topLeft = farSide ? strong : clear;
            topRight = farSide ? strong : clear;
        }
        shapes.rect(x, y, width, height, bottomLeft, bottomRight, topRight, topLeft);
    }
}
