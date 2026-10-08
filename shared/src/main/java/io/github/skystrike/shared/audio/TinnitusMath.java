package io.github.skystrike.shared.audio;

/**
 * The stun ring: the ringing a blinded player hears, and the tail that outlives the flash
 * (mechanics §6.1, roadmap Phase 9).
 *
 * <p>The mechanics plan asks for "a heavy screen effect that decays exponentially — overwhelming
 * at first, clearing quickly toward the end — ... plus an audio ringing effect". The screen half
 * is {@code StunMath.blindIntensity} driving the whiteout pass; this is the audio half, and it is
 * deliberately <b>not</b> the same curve. Real tinnitus does not stop when the light comes back:
 * it peaks with the flash and then rings on while the player is already moving and shooting, which
 * is what makes a stun grenade feel like it cost you something after it wore off.
 *
 * <p>So the envelope is asymmetric:
 *
 * <ul>
 *   <li><b>Attack</b> — while blinded, the target is {@link #ringGain(float)} of the blind
 *       intensity and the level rises at {@link #ATTACK_PER_SECOND}. Fast, so the ring is already
 *       there on the first frame a flash lands.</li>
 *   <li><b>Release</b> — when the blind decays the level follows it down over
 *       {@link #TAIL_SECONDS}, exponentially, exactly like the whiteout. The ring is the last
 *       thing that stops, several seconds after the player can see again.</li>
 * </ul>
 *
 * <p>Pure maths in {@code shared} for the same reason as the other presentation curves: the class
 * that owns the looping voice cannot be tested without an audio device, so the curve lives where
 * it can be.
 */
public final class TinnitusMath {

    /** Seconds for the ring to fall from full to ~1% of its peak after the blind clears. */
    public static final float TAIL_SECONDS = 4f;

    /** How fast the ring comes up while the player is blinded. Fast enough to feel instant. */
    public static final float ATTACK_PER_SECOND = 12f;

    /**
     * Exponential release rate. Derived from {@link #TAIL_SECONDS} rather than written as a second
     * constant, so "the tail is four seconds" stays true if the tail is ever retuned: an
     * exponential is within 1% of its target after 4.6 time constants.
     */
    public static final float RING_RELEASE_PER_SECOND = 4.6f / TAIL_SECONDS;

    /** A full flashbang rings loudly, but never so loud that it hides the fight around you. */
    public static final float MAX_RING_GAIN = 0.85f;

    /** Below this the loop is not worth holding a voice for; the mixer stops it. */
    public static final float MIN_RING_GAIN = 0.005f;

    /** Ring pitch at zero level; the ring sharpens slightly as it gets louder. */
    public static final float BASE_RING_PITCH = 0.92f;

    /** Additional pitch at full ring level, as a multiplier. */
    public static final float RING_PITCH_SPREAD = 0.16f;

    private TinnitusMath() {
    }

    /**
     * The ring level a given blindness deserves.
     *
     * <p>A square root, so the curve is steep at the bottom: the outer band of a stun grenade — a
     * fraction of a second of whiteout — still leaves an audible ring, where a linear map would
     * round it away to nothing. The result is capped by {@link #MAX_RING_GAIN}.
     *
     * @param blindIntensity the same {@code StunMath.blindIntensity} the whiteout pass uses
     * @return a ring level in {@code [0, MAX_RING_GAIN]}
     */
    public static float ringGain(float blindIntensity) {
        float intensity = clamp01(blindIntensity);
        return MAX_RING_GAIN * (float) Math.sqrt(intensity);
    }

    /** Advances the ring envelope one frame with the standard rates. */
    public static float step(float current, float target, float deltaSeconds) {
        return step(current, target, deltaSeconds, ATTACK_PER_SECOND, RING_RELEASE_PER_SECOND);
    }

    /**
     * Advances the ring envelope one frame.
     *
     * <p>Rising is linear at {@code attackPerSecond}; falling is exponential toward the target,
     * which never overshoots, never goes negative, and passes through the same kind of decay the
     * whiteout uses.
     *
     * @param current            the level last frame
     * @param target             the level this frame deserves
     * @param deltaSeconds       frame time; non-positive returns {@code current} unchanged
     * @param attackPerSecond    rise rate in levels per second
     * @param releasePerSecond   exponential fall rate, in time constants per second
     * @return the new level, clamped to {@code [0, 1]}
     */
    public static float step(
            float current,
            float target,
            float deltaSeconds,
            float attackPerSecond,
            float releasePerSecond) {
        float from = clamp01(current);
        float to = clamp01(target);
        if (!Float.isFinite(deltaSeconds) || deltaSeconds <= 0f) {
            return from;
        }
        if (to >= from) {
            return Math.min(to, from + Math.max(0f, attackPerSecond) * deltaSeconds);
        }
        float rate = Math.max(0f, Float.isFinite(releasePerSecond) ? releasePerSecond : 0f);
        return to + (from - to) * (float) Math.exp(-rate * deltaSeconds);
    }

    /**
     * The pitch the loop plays at for a given level: flat and dull at the tail, sharper at the
     * peak, so the ring reads as pressure rather than as a fixed sine tone.
     */
    public static float ringPitch(float level) {
        return BASE_RING_PITCH + RING_PITCH_SPREAD * clamp01(level);
    }

    /** True when the level is loud enough that the loop should be running. */
    public static boolean audible(float level) {
        return Float.isFinite(level) && level >= MIN_RING_GAIN;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
