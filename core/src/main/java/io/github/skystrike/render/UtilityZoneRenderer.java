package io.github.skystrike.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.FireSurface;
import io.github.skystrike.shared.utility.UtilityEffect;
import io.github.skystrike.shared.utility.UtilityRegistry;
import java.util.List;

/**
 * Draws the persistent molotov fire zones from the snapshot: one continuous, softly layered,
 * flickering burn per patch, for exactly as long as the authoritative zone lives and over exactly
 * the circle the damage-over-time resolution burns.
 *
 * <p>The detonation's own {@code FIRE_ZONE} events stay as they are — a one-shot burst of flame
 * particles and the flickering attached light that already lives for the zone's duration — and
 * {@code ZoneEmitter} keeps the sparks and heat smoke coming. What this renderer owns is the
 * fire's body: the picture that used to be hard-edged flat triangles per patch and is now a bed
 * of overlapping soft-gradient layers per patch — a deep ember wash that bridges the spread
 * spacing into one burning line, an ember bed inside the damage circle, an orange body, a hot
 * yellow core and a near-white heart — with soft flame tongues licking along the surface the
 * server spread the fire across (rising off a floor, climbing a wall and leaning slightly off it,
 * hanging and crawling along a ceiling).
 *
 * <p><b>Determinism.</b> Every flicker, breathe and sway is a sine of the effect clock with a
 * phase seeded from {@code zone.id} and the tongue index — no random calls anywhere in this path,
 * so every client draws the identical fire at the identical moment. The clock handed in is the
 * effect clock ({@code FxClock}): monotonic, pausable, frame-rate independent. Handing this
 * renderer the wall clock instead freezes the flicker into giant quantised steps — float32 has
 * no resolution left for a sine at epoch-second magnitudes — so the call site and this document
 * agree on the effect clock on purpose.
 *
 * <p>It draws in the scene pass with ordinary alpha blending, so the fog composite gates it by
 * visibility exactly like the rest of the scene: fire shows where the observer can see it and
 * stays hidden around corners, matching the occlusion-tested particle bursts. Smoke and poison
 * zones are not drawn here — their particle clouds already render them.
 */
public final class UtilityZoneRenderer implements Disposable {

    // Layered heat, from the wide cool rim to the hot heart. Fractions of the zone's damage
    // radius: the ember wash is the only layer allowed past it (~25%), feathering the rim so a
    // line of overlapping patches reads as one continuous burn instead of seven discs; every
    // roaring layer sits inside the circle the DOT resolves.
    private static final float WASH_RADIUS = 1.25f;
    private static final float EMBER_RADIUS = 0.95f;
    private static final float BODY_RADIUS = 0.62f;
    private static final float CORE_RADIUS = 0.32f;
    private static final float HOT_RADIUS = 0.14f;

    private static final Color WASH_COLOR = new Color(0.50f, 0.10f, 0.02f, 1f);
    private static final Color EMBER_COLOR = new Color(0.72f, 0.18f, 0.04f, 1f);
    private static final Color BODY_COLOR = new Color(1.00f, 0.42f, 0.08f, 1f);
    private static final Color CORE_COLOR = new Color(1.00f, 0.82f, 0.32f, 1f);
    private static final Color HOT_COLOR = new Color(1.00f, 0.95f, 0.78f, 1f);

    /** Flame tongues per patch: one up the middle plus a short line along the spread axis. */
    private static final int TONGUES_PER_ZONE = 6;

    /** Seconds at the start of a zone's life spent fading the fire in, instead of popping. */
    private static final float FADE_IN_SECONDS = 0.2f;

    /** Seconds at the end of a zone's life spent fading the fire out. */
    private static final float FADE_OUT_SECONDS = 1.0f;

    /** Degrees a tongue tip wanders either side of the surface's flame direction. */
    private static final float TONGUE_SWAY_DEGREES = 22f;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture glow;

    public UtilityZoneRenderer() {
        glow = createGlowTexture();
    }

    /**
     * A procedurally generated radial gradient: white, alpha smoothstepped to zero at the rim.
     * Generated in code rather than shipped as a sprite because the fire is soft geometry, and a
     * binary blob nobody can preview in the authoring sandbox is a liability for one gradient.
     */
    private static Texture createGlowTexture() {
        final int size = 128;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float half = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - half) / half;
                float dy = (y - half) / half;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float t = Math.max(0f, 1f - r);
                float alpha = t * t * (3f - 2f * t);
                pixmap.setColor(1f, 1f, 1f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /**
     * Draws every burning fire zone.
     *
     * @param camera      the world camera
     * @param zones       the snapshot's persistent zones (smoke, poison and fire)
     * @param timeSeconds the effect clock in seconds ({@code FxClock.time()}) — a monotonic,
     *                    pausable, frame-rate-independent base, never the wall clock
     * @param map         the live map, so flame direction can probe which side of the patch is
     *                    open air; null reads as open ground
     */
    public void render(GameCamera camera, List<UtilityZone> zones, float timeSeconds, ArenaMap map) {
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
        batch.setProjectionMatrix(camera.combined());
        BlendState.setAlpha();

        batch.begin();
        for (UtilityZone zone : zones) {
            if (isBurning(zone)) {
                drawZone(zone, zones, map, timeSeconds);
            }
        }
        batch.end();
    }

    private static boolean isBurning(UtilityZone zone) {
        return zone != null && zone.remainingSeconds > 0f && zone.effect() == UtilityEffect.FIRE;
    }

    private void drawZone(UtilityZone zone, List<UtilityZone> zones, ArenaMap map, float timeSeconds) {
        float duration = UtilityRegistry.of(zone.utility()).durationSeconds();
        float remaining = zone.remainingSeconds;
        float elapsed = Math.max(0f, duration - remaining);
        float fade = Math.min(
            Math.min(elapsed / FADE_IN_SECONDS, remaining / FADE_OUT_SECONDS), 1f);
        float alpha = Math.max(0f, Math.min(1f, fade));
        if (alpha <= 0f) {
            return;
        }

        // Two deterministic wobbles per patch: a slow roll under a faster crackle, both from the
        // effect clock and the zone's own phase, identical on every client.
        float phase = zone.id * 1.6180339f;
        float slowRoll = 0.55f + 0.45f * (float) Math.sin(timeSeconds * 3.7f + phase);
        float crackle = 0.62f + 0.38f * (float) Math.sin(timeSeconds * 9.1f + phase * 1.7f);
        float heart = 0.55f + 0.45f * (float) Math.sin(timeSeconds * 13.3f + phase * 2.3f);

        drawDisc(zone.x, zone.y, zone.radius * WASH_RADIUS, WASH_COLOR, 0.11f * alpha * slowRoll);
        drawDisc(zone.x, zone.y, zone.radius * EMBER_RADIUS, EMBER_COLOR, 0.22f * alpha * crackle);
        drawDisc(zone.x, zone.y, zone.radius * BODY_RADIUS, BODY_COLOR, 0.30f * alpha * crackle);
        drawDisc(zone.x, zone.y, zone.radius * CORE_RADIUS, CORE_COLOR, 0.36f * alpha * heart);
        drawDisc(zone.x, zone.y, zone.radius * HOT_RADIUS, HOT_COLOR, 0.32f * alpha * heart);

        drawTongues(zone, zones, map, timeSeconds, alpha, phase);
    }

    /**
     * The flame tongues: soft stretched glows licking along the surface's flame direction. Their
     * anchors lie along the spread line when the cast has siblings (so neighbouring patches'
     * tongues line up into one burning line), or on a small ring for a lone patch. Each tongue
     * breathes and sways on its own deterministic phase — body orange, hot core yellow.
     */
    private void drawTongues(
            UtilityZone zone,
            List<UtilityZone> zones,
            ArenaMap map,
            float timeSeconds,
            float alpha,
            float zonePhase) {
        FireSurface.Frame surface = FireSurface.of(zone, zones, map);
        float flameX = surface.flameX();
        float flameY = surface.flameY();
        boolean hasAxis = surface.axisX() != 0f || surface.axisY() != 0f;
        float perpX = -surface.axisY();
        float perpY = surface.axisX();

        for (int i = 0; i < TONGUES_PER_ZONE; i++) {
            float tonguePhase = zone.id * 1.7f + i * 2.399f;
            float anchorX;
            float anchorY;
            if (hasAxis) {
                float along = (i == 0)
                    ? 0f
                    : ((i % 2 == 0 ? -1f : 1f) * ((i + 1) / 2) * 0.30f * zone.radius);
                float lateral = (hash(zone.id, i) - 0.5f) * 0.34f * zone.radius;
                anchorX = zone.x + surface.axisX() * along + perpX * lateral;
                anchorY = zone.y + surface.axisY() * along + perpY * lateral;
            } else {
                double ringAngle = zonePhase + i * (Math.PI * 2.0 / TONGUES_PER_ZONE);
                float ringDistance = i == 0 ? 0f : zone.radius * 0.38f;
                anchorX = zone.x + (float) Math.cos(ringAngle) * ringDistance;
                anchorY = zone.y + (float) Math.sin(ringAngle) * ringDistance;
            }

            float breathe = 0.72f + 0.28f * (float) Math.sin(timeSeconds * (6.1f + 0.37f * i) + tonguePhase);
            float sway = (float) Math.sin(timeSeconds * (3.3f + 0.29f * i) + tonguePhase * 1.31f);
            float height = zone.radius * (0.82f - 0.08f * (i % 3)) * breathe;

            // The tongue's own lean: the surface's flame direction, tipped by the sway.
            double lean = Math.toRadians(sway * TONGUE_SWAY_DEGREES);
            float cosLean = (float) Math.cos(lean);
            float sinLean = (float) Math.sin(lean);
            float dirX = flameX * cosLean - flameY * sinLean;
            float dirY = flameX * sinLean + flameY * cosLean;
            float rotation = (float) Math.toDegrees(Math.atan2(dirY, dirX)) - 90f;

            float halfWidth = zone.radius * 0.17f * (0.85f + 0.30f * breathe);
            float bodyCentreX = anchorX + dirX * height * 0.42f;
            float bodyCentreY = anchorY + dirY * height * 0.42f;
            drawTongue(bodyCentreX, bodyCentreY, halfWidth, height, rotation,
                BODY_COLOR, 0.26f * alpha * breathe);

            float coreHeight = height * 0.55f;
            float coreCentreX = anchorX + dirX * coreHeight * 0.42f;
            float coreCentreY = anchorY + dirY * coreHeight * 0.42f;
            drawTongue(coreCentreX, coreCentreY, halfWidth * 0.55f, coreHeight, rotation,
                CORE_COLOR, 0.30f * alpha * breathe);
        }
    }

    /** One soft round glow, scaled to a disc of {@code radius} at {@code (x, y)}. */
    private void drawDisc(float x, float y, float radius, Color color, float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(glow, x - radius, y - radius, radius * 2f, radius * 2f);
    }

    /**
     * One soft stretched glow, centred at {@code (x, y)} with its long axis rotated
     * {@code rotationDegrees} off vertical (the sprite's local up), premultiplied-free ordinary
     * alpha like the rest of the scene pass.
     */
    private void drawTongue(
            float x, float y, float halfWidth, float height, float rotationDegrees, Color color,
            float alpha) {
        batch.setColor(color.r, color.g, color.b, alpha);
        batch.draw(
            glow,
            x - halfWidth,
            y - height * 0.5f,
            halfWidth,
            height * 0.5f,
            halfWidth * 2f,
            height,
            1f,
            1f,
            rotationDegrees);
    }

    /** Deterministic per-(zone, tongue) offset in {@code [0, 1)}; the same on every client. */
    private static float hash(int a, int b) {
        float value = (float) Math.sin(a * 12.9898f + b * 78.233f) * 43758.5453f;
        return value - (float) Math.floor(value);
    }

    @Override
    public void dispose() {
        batch.dispose();
        glow.dispose();
    }
}
