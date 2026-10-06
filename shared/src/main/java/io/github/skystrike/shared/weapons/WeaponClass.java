package io.github.skystrike.shared.weapons;

/**
 * Weapon families — the twelve classes of the {@code assets/sprites/guns.json} catalog that
 * have game mechanics — and the one thing the family alone decides: how hard the round drops
 * (mechanics §4.2).
 *
 * <p>The quoted numbers are relative weights, not accelerations — they are multiplied by
 * {@link io.github.skystrike.shared.config.CombatConfig#BULLET_GRAVITY_BASE} to get units/s².
 * Launcher and special-class catalog entries have no mechanics yet and no member here.
 */
public enum WeaponClass {

    SNIPER(1.0f),
    PISTOL(1.5f),
    DMR(1.8f),
    REVOLVER(2.0f),
    BATTLE_RIFLE(2.2f),
    LMG(2.4f),
    ASSAULT_RIFLE(2.5f),
    PDW(3.2f),
    SMG(3.5f),
    SHOTGUN(5.0f);

    private final float gravityWeight;

    WeaponClass(float gravityWeight) {
        this.gravityWeight = gravityWeight;
    }

    /** Relative drop weight for this family: sniper 1.0 through shotgun 5.0. */
    public float gravityWeight() {
        return gravityWeight;
    }

    /** True for the families that fit the handgun slot (loadout slot 2). */
    public boolean isSidearm() {
        return this == PISTOL || this == REVOLVER;
    }
}
