package io.github.skystrike.shared.math;

/**
 * Angle wrapping and conversion helpers. Degrees are the project-wide unit for gameplay angles;
 * radians only appear when calling into trigonometry.
 */
public final class Angles {

    public static final float DEGREES_PER_RADIAN = (float) (180.0 / Math.PI);
    public static final float RADIANS_PER_DEGREE = (float) (Math.PI / 180.0);

    private Angles() {
    }

    /** Wraps an angle in degrees into the half-open range {@code (-180, 180]}. */
    public static float wrap(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped <= -180f) {
            wrapped += 360f;
        } else if (wrapped > 180f) {
            wrapped -= 360f;
        }
        return wrapped;
    }

    /**
     * Signed shortest rotation in degrees that takes {@code from} to {@code to}. Always within
     * {@code (-180, 180]}, so springs and lerps never take the long way round.
     */
    public static float shortestDelta(float from, float to) {
        return wrap(to - from);
    }

    public static float toRadians(float degrees) {
        return degrees * RADIANS_PER_DEGREE;
    }

    public static float toDegrees(float radians) {
        return radians * DEGREES_PER_RADIAN;
    }

    /** Angle in degrees of the vector {@code (x, y)}, measured counter-clockwise from +X. */
    public static float ofVector(float x, float y) {
        return wrap(toDegrees((float) Math.atan2(y, x)));
    }
}
