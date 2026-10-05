package io.github.skystrike.shared.weapons;

/**
 * Weapon families, and the one thing the family alone decides: how hard the round drops
 * (mechanics §4.2).
 *
 * <p>The quoted numbers are relative weights, not accelerations — they are multiplied by
 * {@link io.github.skystrike.shared.config.CombatConfig#BULLET_GRAVITY_BASE} to get units/s².
 */
public enum WeaponClass {

    SNIPER(1.0f),
    RIFLE(2.5f),
    SMG(3.5f),
    PISTOL(4.0f),
    SHOTGUN(5.0f);

    private final float gravityWeight;

    WeaponClass(float gravityWeight) {
        this.gravityWeight = gravityWeight;
    }

    /** Relative drop weight for this family: sniper 1.0 through shotgun 5.0. */
    public float gravityWeight() {
        return gravityWeight;
    }
}
