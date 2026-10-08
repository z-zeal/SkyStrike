package io.github.skystrike.shared.audio;

/**
 * Distance attenuation and stereo panning (roadmap Phase 9, project structure §8).
 *
 * <p>Pure maths, no audio backend. The mixer asks one question — "given where the listener is and
 * where this happened, what gain and pan does it deserve?" — and this class answers it, so the
 * answer is identical for every caller and testable without a device.
 *
 * <p><b>The two curves.</b> Gain is the inverse-distance rule {@code reference / (reference +
 * distance)}, which falls off fast in the first few hundred units and then gently: a fight across
 * the arena is quieter than one in the next room, but not silent. Panning is linear in the source's
 * horizontal offset from the listener, saturating at {@link #PAN_FULL_DISTANCE}.
 *
 * <p><b>Why panning uses world X, not the aim direction.</b> The camera in this game does not
 * rotate — it is a fixed-orientation top-down view that pans and zooms — so a sound to the right
 * of the player is on the right of the screen, and the stereo image must agree with that or the
 * player's eyes and ears disagree about where a shot came from. A head-relative model would be
 * correct in a first-person game and wrong in this one.
 *
 * <p><b>The edge taper.</b> Inverse-distance never reaches zero, so a hard {@code audibleRadius}
 * cut would pop. The last fifth of the radius tapers linearly to silence, which is inaudible as a
 * curve and makes the cut-off impossible to hear.
 *
 * <p><b>Occlusion.</b> A blocked source is not silenced, it is muffled: the caller supplies
 * {@code occluded} and the gain is scaled by {@link #OCCLUDED_GAIN}. A flashbang on the far side
 * of a wall should still be heard — that is information — but it must not sound like it went off
 * in the room. The mixer has no filter to apply, so the compromise is gain reduction; the
 * trade-off is documented rather than hidden.
 */
public final class SpatialAudio {

    /** Distance at which gain is half of full, for sources that do not name their own. */
    public static final float REFERENCE_DISTANCE = 900f;

    /** Sources past this distance are inaudible, for sources that do not name their own. */
    public static final float MAX_AUDIBLE_DISTANCE = 2400f;

    /** Horizontal offset at which panning is hard left or hard right. */
    public static final float PAN_FULL_DISTANCE = 900f;

    /** Gain multiplier for a source with no line of sight to the listener. */
    public static final float OCCLUDED_GAIN = 0.35f;

    /** Below this gain a source is not worth a voice; the pool spends its slots on audible ones. */
    public static final float MIN_GAIN = 0.02f;

    /** Fraction of the radius over which gain tapers to silence, avoiding an audible cut. */
    public static final float EDGE_TAPER_FRACTION = 0.8f;

    private SpatialAudio() {
    }

    /** Where the local player's ears are: their own world position, nothing more. */
    public record Listener(float x, float y) {

        /** Fallback before the local player exists, so a caller never has to check for null ears. */
        public static final Listener ORIGIN = new Listener(0f, 0f);

        public Listener {
            x = Float.isFinite(x) ? x : 0f;
            y = Float.isFinite(y) ? y : 0f;
        }
    }

    /**
     * The mix for one source: {@code gain} already includes attenuation and occlusion, {@code pan}
     * is in {@code [-1, 1]}, and {@code audible} is false when the source is too far, too quiet or
     * behind enough cover to be worth no voice at all.
     */
    public record Placement(float gain, float pan, boolean audible) {

        /** The result for a source that should not be played. */
        public static final Placement INAUDIBLE = new Placement(0f, 0f, false);

        public Placement {
            gain = clamp01(gain);
            pan = Math.max(-1f, Math.min(1f, Float.isFinite(pan) ? pan : 0f));
        }
    }

    /** Euclid. Kept here so callers do not each invent their own epsilon. */
    public static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The inverse-distance gain for one source, with the edge taper applied.
     *
     * <p>An unusable input is silence, not a guessed loudness: a non-finite or negative distance,
     * or a non-finite or non-positive reference or radius, drops the source out of the mix rather
     * than playing it at whatever level the arithmetic happens to produce. A malformed entry should
     * be inaudible, and one NaN should never make a shot across the arena as loud as one beside you.
     *
     * @param distance          listener-to-source distance in world units
     * @param referenceDistance distance at which gain is half of full; must be positive
     * @param audibleRadius     distance past which the gain is exactly {@code 0}; must be positive
     * @return a gain in {@code [0, 1]}
     */
    public static float attenuate(float distance, float referenceDistance, float audibleRadius) {
        if (!Float.isFinite(distance) || distance < 0f
            || !Float.isFinite(referenceDistance) || referenceDistance <= 0f
            || !Float.isFinite(audibleRadius) || audibleRadius <= 0f) {
            return 0f;
        }
        if (distance >= audibleRadius) {
            return 0f;
        }
        float gain = referenceDistance / (referenceDistance + distance);
        float edgeStart = audibleRadius * EDGE_TAPER_FRACTION;
        if (distance > edgeStart) {
            gain *= (audibleRadius - distance) / (audibleRadius - edgeStart);
        }
        return clamp01(gain);
    }

    /**
     * Linear pan from a horizontal offset: negative is left, positive is right, saturating at
     * {@code panFullDistance}.
     */
    public static float pan(float dx, float panFullDistance) {
        if (!Float.isFinite(dx)) {
            return 0f;
        }
        float full = Math.max(1f, finiteOr(panFullDistance, PAN_FULL_DISTANCE));
        return Math.max(-1f, Math.min(1f, dx / full));
    }

    /** Places a source for the default reach; no occlusion. */
    public static Placement place(Listener listener, float x, float y) {
        return place(listener, x, y, REFERENCE_DISTANCE, MAX_AUDIBLE_DISTANCE, false);
    }

    /** Places a source for the default reach; no occlusion. */
    public static Placement place(float listenerX, float listenerY, float x, float y) {
        return place(
            listenerX, listenerY, x, y, REFERENCE_DISTANCE, MAX_AUDIBLE_DISTANCE, false);
    }

    /**
     * Places a source for a listener.
     *
     * @param listener          the local player's ears; {@code null} means {@link Listener#ORIGIN}
     * @param x                 source world position
     * @param y                 source world position
     * @param referenceDistance half-gain distance, normally from the sound's entry
     * @param audibleRadius     silence distance, normally from the sound's entry
     * @param occluded          whether terrain blocks the straight line to the source
     */
    public static Placement place(
            Listener listener,
            float x,
            float y,
            float referenceDistance,
            float audibleRadius,
            boolean occluded) {
        Listener ears = listener == null ? Listener.ORIGIN : listener;
        return place(ears.x(), ears.y(), x, y, referenceDistance, audibleRadius, occluded);
    }

    /**
     * Places a source for a listener, without building a {@link Listener}.
     *
     * <p>This is the form the mixer calls on every effect event: the ears are two floats it
     * already holds, and a per-event record allocation on the render thread would be pure waste.
     * The record overloads exist for callers and tests that think in terms of a listener.
     *
     * @param listenerX         the local player's ears
     * @param listenerY         the local player's ears
     * @param x                 source world position
     * @param y                 source world position
     * @param referenceDistance half-gain distance, normally from the sound's entry
     * @param audibleRadius     silence distance, normally from the sound's entry
     * @param occluded          whether terrain blocks the straight line to the source
     */
    public static Placement place(
            float listenerX,
            float listenerY,
            float x,
            float y,
            float referenceDistance,
            float audibleRadius,
            boolean occluded) {
        if (!Float.isFinite(x) || !Float.isFinite(y)
            || !Float.isFinite(listenerX) || !Float.isFinite(listenerY)) {
            return Placement.INAUDIBLE;
        }
        float dx = x - listenerX;
        float gain = attenuate(distance(listenerX, listenerY, x, y), referenceDistance, audibleRadius);
        if (occluded) {
            gain *= OCCLUDED_GAIN;
        }
        if (gain < MIN_GAIN) {
            return Placement.INAUDIBLE;
        }
        return new Placement(gain, pan(dx, PAN_FULL_DISTANCE), true);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
