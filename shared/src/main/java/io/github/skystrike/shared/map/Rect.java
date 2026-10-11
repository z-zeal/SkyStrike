package io.github.skystrike.shared.map;

import io.github.skystrike.shared.math.Geometry;

/**
 * An axis-aligned rectangle given by its bottom-left corner and its size.
 *
 * <p>Y grows upward, matching the renderer and the simulation. A {@link MapBlock} owns a
 * rectangle; its {@link BlockRules} row, rather than the rectangle itself, decides whether it
 * blocks bullets or sight and how it is drawn.
 */
public record Rect(float x, float y, float width, float height) {

    public Rect {
        if (width < 0f || height < 0f) {
            throw new IllegalArgumentException("rect size must not be negative: " + width + "x" + height);
        }
    }

    public float left() {
        return x;
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y;
    }

    public float top() {
        return y + height;
    }

    public float centerX() {
        return x + width / 2f;
    }

    public float centerY() {
        return y + height / 2f;
    }

    /** Reflects this rectangle about the vertical line {@code x = axis}. */
    public Rect mirror(float axis) {
        return new Rect(2f * axis - x - width, y, width, height);
    }

    /** Translated copy. */
    public Rect translated(float dx, float dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    /** Edge-inclusive point test. */
    public boolean contains(float px, float py) {
        return px >= left() && px <= right() && py >= bottom() && py <= top();
    }

    /**
     * X of the point inside this rectangle nearest to {@code px}: the clamp, so a point already
     * inside is kept as it is.
     *
     * <p>Used wherever a distance is measured to a surface rather than to its centre — a blast
     * arriving at a block's nearest face, a swing landing on the edge a body can reach — because
     * a rectangle's centre is not where either of those touches it.
     */
    public float clampX(float px) {
        return Math.min(right(), Math.max(left(), px));
    }

    /** Y of the point inside this rectangle nearest to {@code py}: the clamp. */
    public float clampY(float py) {
        return Math.min(top(), Math.max(bottom(), py));
    }

    /** Edge-inclusive overlap test. */
    public boolean overlaps(Rect other) {
        return Geometry.aabbOverlaps(this, other);
    }
}
