package io.github.skystrike.shared.utility;

/**
 * What a throwable actually does when it goes off (mechanics §6).
 *
 * <p>This is the gameplay resolution kind, not the visual. One effect kind can drive several
 * presentations, and the effects layer keys off the utility id rather than this enum.
 */
public enum UtilityEffect {

    /** Occluded radial damage plus a physics impulse. Frag and impact. */
    BLAST,

    /** Registers a vision-blocking volume for the visibility pass and for the shared sight query. */
    SMOKE_CLOUD,

    /** Distance-banded, sight-dependent blind and slow. */
    STUN,

    /** A screen-filling blind with no damage and no slow. */
    FLASH,

    /** Persistent ground zones dealing damage on the shared damage-over-time clock. */
    FIRE,

    /** A vision-blocking volume that also damages anyone inside it. */
    TOXIC_CLOUD,

    /** Occluded damage confined to a cone facing the way the device was placed. */
    DIRECTIONAL_BLAST;

    /** True when this effect subtracts from visibility for both the shader and the sight query. */
    public boolean blocksVision() {
        return this == SMOKE_CLOUD || this == TOXIC_CLOUD;
    }

    /** True when this effect leaves a lasting zone behind rather than resolving instantly. */
    public boolean isPersistent() {
        return this == SMOKE_CLOUD || this == TOXIC_CLOUD || this == FIRE;
    }

    /** True when damage is applied repeatedly on the damage-over-time clock. */
    public boolean isDamageOverTime() {
        return this == FIRE || this == TOXIC_CLOUD;
    }
}
