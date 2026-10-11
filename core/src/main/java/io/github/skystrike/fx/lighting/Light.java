package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.Color;

/**
 * Plain world-space light data owned by {@link LightPool}; it does not own GL resources.
 *
 * <p>Callers keep the integer handle returned by the pool, not this object. The pool only exposes
 * an active light to the package-local renderer, so gameplay/UI code cannot retain a pooled slot
 * after it has been retired and reused.
 */
public final class Light {

    /**
     * Default radius of a player light. It is the body vision bubble's radius
     * ({@code VisionConfig.BODY_BUBBLE_RADIUS}), because the lamp lights the field its owner sees:
     * same centre, same reach, same falloff.
     */
    public static final float DEFAULT_PLAYER_RADIUS = 140f;

    /**
     * Default intensity of a player light. It no longer appears as a brightness added on top of
     * the fog — it scales the illumination the lamp contributes to the sight field, so 0.35 is a
     * clearly read pool without washing the lit surfaces out.
     */
    public static final float DEFAULT_PLAYER_INTENSITY = 0.35f;

    /**
     * Falloff exponent of a vision light — the local player, a lit remote player, a device bubble.
     *
     * <p>It is applied to the sight field's own quadratic distance falloff, {@code 1 - (d /
     * radius)²} (the curve {@code light/visibility_cone.frag} gives an observer). {@code 1} is that
     * curve exactly, so the lamp and the field it lights fall off as one light; higher values
     * tighten the pool around the source. Not exposed as a cvar.
     */
    public static final float SIGHT_FALLOFF = 1f;

    /**
     * Falloff exponent of an effect light — a muzzle flash, an explosion, a flare. Effects are
     * emissive and may be tighter than the field they sit in, so 2 keeps their light from
     * spreading into a second ambient pool.
     */
    public static final float EFFECT_FALLOFF = 2f;

    private float x;
    private float y;
    private float radius;
    private float red;
    private float green;
    private float blue;
    private float intensity;
    private float falloff;
    private boolean castsShadow;

    public Light(
            float x,
            float y,
            float radius,
            Color colour,
            float intensity,
            float falloff,
            boolean castsShadow) {
        set(x, y, radius, colour, intensity, falloff, castsShadow);
    }

    void set(
            float x,
            float y,
            float radius,
            Color colour,
            float intensity,
            float falloff,
            boolean castsShadow) {
        if (colour == null) {
            throw new IllegalArgumentException("light colour is required");
        }
        set(x, y, radius, colour.r, colour.g, colour.b, intensity, falloff, castsShadow);
    }

    void set(
            float x,
            float y,
            float radius,
            float red,
            float green,
            float blue,
            float intensity,
            float falloff,
            boolean castsShadow) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("light position must be finite");
        }
        if (!Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("light radius must be positive and finite");
        }
        if (!Float.isFinite(intensity) || intensity < 0f) {
            throw new IllegalArgumentException("light intensity must be non-negative and finite");
        }
        if (!Float.isFinite(falloff) || falloff <= 0f) {
            throw new IllegalArgumentException("light falloff must be positive and finite");
        }
        if (!Float.isFinite(red) || !Float.isFinite(green) || !Float.isFinite(blue)) {
            throw new IllegalArgumentException("light colour channels must be finite");
        }

        this.x = x;
        this.y = y;
        this.radius = radius;
        this.red = clampColour(red);
        this.green = clampColour(green);
        this.blue = clampColour(blue);
        this.intensity = intensity;
        this.falloff = falloff;
        this.castsShadow = castsShadow;
    }

    void setPosition(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("light position must be finite");
        }
        this.x = x;
        this.y = y;
    }

    private static float clampColour(float channel) {
        return Math.max(0f, Math.min(1f, channel));
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float radius() {
        return radius;
    }

    public float red() {
        return red;
    }

    public float green() {
        return green;
    }

    public float blue() {
        return blue;
    }

    public float intensity() {
        return intensity;
    }

    public float falloff() {
        return falloff;
    }

    public boolean castsShadow() {
        return castsShadow;
    }
}
