package io.github.skystrike.shared.map;

import io.github.skystrike.shared.math.Geometry;

/**
 * An axis-aligned rectangle given by its bottom-left corner and its size.
 *
 * <p>Y grows upward, matching the renderer and the simulation. Every piece of arena geometry is
 * one of these: the same rectangle stops a bullet, blocks sight and gets drawn.
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

    /** Edge-inclusive overlap test. */
    public boolean overlaps(Rect other) {
        return Geometry.aabbOverlaps(this, other);
    }
}
