package io.github.skystrike.fx.particle;

import com.badlogic.gdx.graphics.Color;

/**
 * One emitter burst, as data (build plan M7 §8.2/§8.3, effects plan §7.1/§9): everything a single
 * particle burst needs, so presets live in {@link EmitterLibrary} rather than in code.
 *
 * <p>Every particle of a burst is described once, at spawn, and never touched again: the vertex
 * shader integrates ballistic motion with exponential drag analytically from these values plus a
 * single {@code u_time} uniform, which is what makes tier 1 "GPU stateless". Per-particle
 * variation (lifetime, speed, direction jitter) comes from the deterministic seed, not from
 * per-particle state.
 *
 * <p>Instances are immutable; the builder exists because a seventeen-field constructor call would
 * be unreadable at every preset site.
 *
 * @param name               preset label, also mixed into the deterministic seed
 * @param count              particles per burst, before the event's scale multiplier
 * @param lifetimeMinSeconds shortest particle life
 * @param lifetimeMaxSeconds longest particle life
 * @param startSize          quad edge at birth, world units, before scale
 * @param endSize            quad edge at death, world units, before scale
 * @param startColor         colour (and alpha) at birth
 * @param endColor           colour (and alpha) at death
 * @param speedMin           initial speed along the burst direction, units/s, before scale
 * @param speedMax           initial speed ceiling
 * @param spreadDegrees      full cone width around the event angle; 360 is omnidirectional
 * @param gravity            y acceleration in units/s² (negative pulls down); 0 for no gravity
 * @param drag               exponential drag coefficient per second; 0 for none
 * @param turbulence         curl-noise amplitude in world units; 0 for none
 * @param blend              ALPHA draws inside the scene pass (fog darkens it), ADDITIVE draws
 *                           after the composite (it glows through darkness)
 * @param tier               GPU (stateless shader) or CPU (colliding simulation)
 * @param occlusionTested    spawn positions are tested against the SDF: no particle on the far
 *                           side of a wall from its source
 * @param restitution        CPU tier only: bounce restitution against the SDF, 0..1
 */
public record EmitterConfig(
    String name,
    int count,
    float lifetimeMinSeconds,
    float lifetimeMaxSeconds,
    float startSize,
    float endSize,
    Color startColor,
    Color endColor,
    float speedMin,
    float speedMax,
    float spreadDegrees,
    float gravity,
    float drag,
    float turbulence,
    Blend blend,
    Tier tier,
    boolean occlusionTested,
    float restitution) {

    /** Which pass a burst's particles draw in; the pipeline owns the order, this picks the side. */
    public enum Blend {
        /** Inside the scene pass, so the fog composite darkens it. Smoke, dust, debris. */
        ALPHA,
        /** After the composite, so it glows through darkness. Sparks, fire, flash. */
        ADDITIVE
    }

    /** Which particle tier simulates the burst. */
    public enum Tier {
        /** Stateless GPU particles: descriptors written once, motion integrated in the shader. */
        GPU,
        /** CPU particles colliding with the SDF: shell casings. The tier-2 proof. */
        CPU
    }

    public EmitterConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("emitter name is required");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + name);
        }
        if (lifetimeMinSeconds < 0f || lifetimeMaxSeconds <= 0f
            || lifetimeMinSeconds > lifetimeMaxSeconds) {
            throw new IllegalArgumentException("lifetimes out of order: " + name);
        }
        if (startSize < 0f || endSize < 0f) {
            throw new IllegalArgumentException("sizes must not be negative: " + name);
        }
        if (startColor == null || endColor == null) {
            throw new IllegalArgumentException("colours are required: " + name);
        }
        if (speedMin < 0f || speedMax < speedMin) {
            throw new IllegalArgumentException("speeds out of order: " + name);
        }
        if (spreadDegrees <= 0f || spreadDegrees > 360f) {
            throw new IllegalArgumentException("spread must be in (0, 360]: " + name);
        }
        if (blend == null || tier == null) {
            throw new IllegalArgumentException("blend and tier are required: " + name);
        }
        if (restitution < 0f || restitution > 1f) {
            throw new IllegalArgumentException("restitution must be in [0, 1]: " + name);
        }
        // Copy the colours: a preset must not share mutable state with whatever built it.
        startColor = new Color(startColor);
        endColor = new Color(endColor);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Fluent builder with the effects-plan defaults: no gravity, no drag, no turbulence. */
    public static final class Builder {
        private final String name;
        private int count;
        private float lifetimeMinSeconds;
        private float lifetimeMaxSeconds;
        private float startSize;
        private float endSize;
        private Color startColor;
        private Color endColor;
        private float speedMin;
        private float speedMax;
        private float spreadDegrees = 360f;
        private float gravity;
        private float drag;
        private float turbulence;
        private Blend blend = Blend.ADDITIVE;
        private Tier tier = Tier.GPU;
        private boolean occlusionTested;
        private float restitution = 0.5f;

        private Builder(String name) {
            this.name = name;
        }

        public Builder count(int count) {
            this.count = count;
            return this;
        }

        public Builder lifetime(float minSeconds, float maxSeconds) {
            this.lifetimeMinSeconds = minSeconds;
            this.lifetimeMaxSeconds = maxSeconds;
            return this;
        }

        public Builder size(float startSize, float endSize) {
            this.startSize = startSize;
            this.endSize = endSize;
            return this;
        }

        public Builder color(Color start, Color end) {
            this.startColor = start;
            this.endColor = end;
            return this;
        }

        public Builder speed(float min, float max) {
            this.speedMin = min;
            this.speedMax = max;
            return this;
        }

        public Builder spread(float degrees) {
            this.spreadDegrees = degrees;
            return this;
        }

        public Builder gravity(float unitsPerSecondSquared) {
            this.gravity = unitsPerSecondSquared;
            return this;
        }

        public Builder drag(float perSecond) {
            this.drag = perSecond;
            return this;
        }

        public Builder turbulence(float worldUnits) {
            this.turbulence = worldUnits;
            return this;
        }

        public Builder blend(Blend blend) {
            this.blend = blend;
            return this;
        }

        public Builder tier(Tier tier) {
            this.tier = tier;
            return this;
        }

        public Builder occlusionTested(boolean tested) {
            this.occlusionTested = tested;
            return this;
        }

        public Builder restitution(float restitution) {
            this.restitution = restitution;
            return this;
        }

        public EmitterConfig build() {
            return new EmitterConfig(
                name,
                count,
                lifetimeMinSeconds,
                lifetimeMaxSeconds,
                startSize,
                endSize,
                startColor,
                endColor,
                speedMin,
                speedMax,
                spreadDegrees,
                gravity,
                drag,
                turbulence,
                blend,
                tier,
                occlusionTested,
                restitution);
        }
    }
}
