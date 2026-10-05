package io.github.skystrike.shared.math;

/**
 * Framerate-independent interpolation.
 *
 * <p>Always use {@link #smooth(float, float, float, float)} for anything that eases over time.
 * Raw {@code lerp(a, b, dt)} makes every eased value depend on the frame rate, which is how a
 * transition that feels right at 60 Hz turns sluggish at 30 and snappy at 144.
 */
public final class Lerp {

    private Lerp() {
    }

    /**
     * Blend weight for an exponential approach at {@code rate} per second over {@code dt} seconds.
     * Returns a value in {@code [0, 1]}.
     */
    public static float factor(float rate, float dt) {
        if (rate <= 0f || dt <= 0f) {
            return 0f;
        }
        return 1f - (float) Math.exp(-rate * dt);
    }

    /** Moves {@code from} toward {@code to} at an exponential {@code rate} per second. */
    public static float smooth(float from, float to, float rate, float dt) {
        return from + (to - from) * factor(rate, dt);
    }

    /**
     * Same as {@link #smooth} but for angles in degrees, taking the shortest way round so a value
     * easing from 170° to -170° travels 20° rather than 340°.
     */
    public static float smoothAngle(float fromDegrees, float toDegrees, float rate, float dt) {
        float delta = Angles.shortestDelta(fromDegrees, toDegrees);
        return Angles.wrap(fromDegrees + delta * factor(rate, dt));
    }

    /** Plain unclamped linear blend. Only correct when {@code t} is not a frame delta. */
    public static float mix(float from, float to, float t) {
        return from + (to - from) * t;
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
