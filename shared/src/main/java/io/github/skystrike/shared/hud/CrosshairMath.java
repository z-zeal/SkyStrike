package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.model.Player;

/**
 * The crosshair's geometry (playable build plan M4 §5): <i>gap = f(spread, gunKick)</i>.
 *
 * <p>Both inputs are already authoritative state on {@link Player} — {@code spread} is the live
 * cone in degrees the server resolves hits inside, {@code gunKick} the visual recoil offset the
 * gun is drawn at. The crosshair does not run its own recoil model; it reads those two numbers
 * and converts degrees to pixels, which is why what the reticle promises and where the round
 * goes cannot drift apart.
 *
 * <p>Aiming narrows the reticle by {@link #ADS_GAP_MULTIPLIER} and changes its form (the widget
 * draws a finer cross with a centre dot), interpolated by the same {@code adsAlpha} the camera
 * pan uses, so the two transitions are one motion.
 */
public final class CrosshairMath {

    /** Gap at perfect accuracy: the reticle never collapses into a single blob. */
    public static final float BASE_GAP_PIXELS = 5f;

    /** How far one degree of spread pushes each arm out. */
    public static final float PIXELS_PER_SPREAD_DEGREE = 4.5f;

    /** How far one degree of muzzle kick pushes each arm out, on top of spread. */
    public static final float PIXELS_PER_KICK_DEGREE = 2.0f;

    /** Hard ceiling, so a fully bloomed shotgun does not throw the arms off screen. */
    public static final float MAX_GAP_PIXELS = 64f;

    /** Aiming tightens the whole reticle by this factor at full ADS. */
    public static final float ADS_GAP_MULTIPLIER = 0.45f;

    /** Arm length in pixels at the base gap; grows slowly with the gap. */
    public static final float BASE_ARM_PIXELS = 7f;
    public static final float ARM_GROWTH_PER_GAP_PIXEL = 0.18f;

    /** Hit marker: how far its arms sit from the centre, and how much they expand as it fades. */
    public static final float HIT_MARKER_GAP_PIXELS = 7f;
    public static final float HIT_MARKER_EXPANSION_PIXELS = 6f;
    public static final float HIT_MARKER_ARM_PIXELS = 6f;

    private CrosshairMath() {
    }

    /**
     * Distance from the centre to the inner end of each arm, in pixels.
     *
     * @param spreadDegrees  the player's live spread cone, degrees
     * @param gunKickDegrees the visual recoil kick, degrees (sign ignored)
     * @param adsAlpha       0 hip-fire, 1 fully aimed; values outside are clamped
     */
    public static float gapPixels(float spreadDegrees, float gunKickDegrees, float adsAlpha) {
        float spread = Math.max(0f, finite(spreadDegrees));
        float kick = Math.abs(finite(gunKickDegrees));
        float raw = BASE_GAP_PIXELS
            + spread * PIXELS_PER_SPREAD_DEGREE
            + kick * PIXELS_PER_KICK_DEGREE;
        float clamped = Math.min(MAX_GAP_PIXELS, raw);
        float ads = clamp01(adsAlpha);
        return clamped * (1f - ads * (1f - ADS_GAP_MULTIPLIER));
    }

    /** The gap for a player, or the resting gap when there is no player to read. */
    public static float gapPixels(Player player, float adsAlpha) {
        if (player == null) {
            return gapPixels(0f, 0f, adsAlpha);
        }
        return gapPixels(player.spread, player.gunKick, adsAlpha);
    }

    /** Arm length for a given gap: a wider reticle gets slightly longer arms, never shorter. */
    public static float armPixels(float gapPixels) {
        float gap = Math.max(0f, finite(gapPixels));
        return BASE_ARM_PIXELS + Math.max(0f, gap - BASE_GAP_PIXELS) * ARM_GROWTH_PER_GAP_PIXEL;
    }

    /**
     * True when the reticle should draw its aimed form: a finer cross with a centre dot. The
     * threshold sits past the halfway point of the transition so the form flips once, cleanly,
     * rather than flickering through the pan.
     */
    public static boolean adsForm(float adsAlpha) {
        return clamp01(adsAlpha) >= 0.6f;
    }

    /** Hit marker arm offset: starts tight and expands outward as the marker fades out. */
    public static float hitMarkerGapPixels(float markerAlpha) {
        float alpha = clamp01(markerAlpha);
        return HIT_MARKER_GAP_PIXELS + (1f - alpha) * HIT_MARKER_EXPANSION_PIXELS;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.min(1f, Math.max(0f, value));
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0f;
    }
}
