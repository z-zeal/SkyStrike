package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.UtilityEffect;
import io.github.skystrike.shared.utility.UtilityRegistry;
import java.util.List;

/**
 * Draws the persistent molotov fire zones from the snapshot: flickering flame tongues over a bed
 * of embers, for exactly as long as the authoritative zone lives and over exactly the circle the
 * damage-over-time resolution burns.
 *
 * <p>The detonation's own {@code FIRE_ZONE} events stay as they are — a one-shot burst of flame
 * particles and the flickering attached light that already lives for the zone's duration. What
 * they cannot do is persist: their flame particles die within 1.5 s, so a fire patch went dark
 * long before its six seconds of damage were up. This renderer is the persistent picture, drawn
 * from the same snapshot zones the smoke occlusion and the minimap read, so the fire you see is
 * the fire that hurts.
 *
 * <p>It draws in the scene pass with alpha blending, so the fog composite gates it by visibility
 * exactly like the rest of the scene: fire shows where the observer can see it and stays hidden
 * around corners, matching the occlusion-tested one-shot bursts. Smoke and poison zones are not
 * drawn here — their particle clouds already render them, and drawing them twice would double
 * the fog over an already-drawn cloud.
 */
public final class UtilityZoneRenderer implements Disposable {

    private static final Color FLAME_EDGE = new Color(0.95f, 0.35f, 0.05f, 1f);
    private static final Color FLAME_CORE = new Color(1f, 0.85f, 0.35f, 1f);
    private static final Color EMBER_BED = new Color(0.55f, 0.12f, 0.02f, 1f);

    /** Flame tongues per zone: one on the zone's centre plus a ring around it. */
    private static final int FLAMES_PER_ZONE = 7;
    /** Seconds at the start of a zone's life spent fading the fire in, instead of popping. */
    private static final float FADE_IN_SECONDS = 0.2f;
    /** Seconds at the end of a zone's life spent fading the fire out. */
    private static final float FADE_OUT_SECONDS = 1.0f;

    private final ShapeRenderer shapes = new ShapeRenderer();

    /**
     * Draws every burning fire zone.
     *
     * @param camera      the world camera
     * @param zones       the snapshot's persistent zones (smoke, poison and fire)
     * @param timeSeconds a monotonic clock in seconds, used for the flicker
     */
    public void render(GameCamera camera, List<UtilityZone> zones, float timeSeconds) {
        if (camera == null || zones == null || zones.isEmpty()) {
            return;
        }
        boolean anyBurning = false;
        for (UtilityZone zone : zones) {
            if (isBurning(zone)) {
                anyBurning = true;
                break;
            }
        }
        if (!anyBurning) {
            return;
        }
        shapes.setProjectionMatrix(camera.combined());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (UtilityZone zone : zones) {
            if (isBurning(zone)) {
                drawZone(zone, timeSeconds);
            }
        }
        shapes.end();
    }

    private static boolean isBurning(UtilityZone zone) {
        return zone != null && zone.remainingSeconds > 0f && zone.effect() == UtilityEffect.FIRE;
    }

    private void drawZone(UtilityZone zone, float timeSeconds) {
        float duration = UtilityRegistry.of(zone.utility()).durationSeconds();
        float remaining = zone.remainingSeconds;
        float elapsed = Math.max(0f, duration - remaining);
        float fade = Math.min(
            Math.min(elapsed / FADE_IN_SECONDS, remaining / FADE_OUT_SECONDS), 1f);
        float alpha = Math.max(0f, Math.min(1f, fade));
        if (alpha <= 0f) {
            return;
        }

        // The ember bed: a dim pool under the flames, covering most of the damage circle.
        shapes.setColor(EMBER_BED.r, EMBER_BED.g, EMBER_BED.b, EMBER_BED.a * 0.35f * alpha);
        shapes.circle(zone.x, zone.y, zone.radius * 0.7f, 12);

        // Flame tongues rise in world space (hot air rises whatever surface the molotov broke
        // on), anchored deterministically per zone so every client draws the same fire.
        float ringRotation = zone.id * 0.37f;
        for (int i = 0; i < FLAMES_PER_ZONE; i++) {
            double angle = ringRotation + i * (Math.PI * 2.0 / FLAMES_PER_ZONE);
            float anchorDistance = i == 0 ? 0f : zone.radius * 0.45f;
            float anchorX = zone.x + (float) Math.cos(angle) * anchorDistance;
            float anchorY = zone.y + (float) Math.sin(angle) * anchorDistance;

            float phase = zone.id * 1.7f + i * 2.3f;
            float flicker = 0.75f + 0.25f * (float) Math.sin(timeSeconds * 9f + phase);
            float height = zone.radius * (0.45f - 0.2f * i / (float) FLAMES_PER_ZONE) * flicker;
            float halfWidth = zone.radius * 0.16f * flicker;
            float tipWobble =
                (float) Math.sin(timeSeconds * 5f + phase * 1.3f) * zone.radius * 0.04f;

            shapes.setColor(
                FLAME_EDGE.r, FLAME_EDGE.g, FLAME_EDGE.b, FLAME_EDGE.a * 0.85f * alpha);
            shapes.triangle(
                anchorX - halfWidth, anchorY,
                anchorX + halfWidth, anchorY,
                anchorX + tipWobble, anchorY + height);
            shapes.setColor(FLAME_CORE.r, FLAME_CORE.g, FLAME_CORE.b, FLAME_CORE.a * alpha);
            shapes.triangle(
                anchorX - halfWidth * 0.5f, anchorY,
                anchorX + halfWidth * 0.5f, anchorY,
                anchorX + tipWobble * 0.5f, anchorY + height * 0.65f);
            shapes.circle(anchorX + tipWobble, anchorY + 2f, 1.5f, 5);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
