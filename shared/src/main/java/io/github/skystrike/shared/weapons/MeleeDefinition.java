package io.github.skystrike.shared.weapons;

/**
 * Immutable definition of one melee weapon — a row of the mechanics §5.2 table.
 *
 * @param id              which melee weapon
 * @param damage          damage dealt to each target caught in the arc
 * @param swingsPerSecond swing cadence while the trigger is held
 * @param range           reach of the arc, measured from attacker centre to target centre
 * @param knockback       physics impulse applied along the attacker→target direction — a real
 *                        velocity change, not a scripted displacement, so a bat can launch an
 *                        airborne enemy off a ledge
 */
public record MeleeDefinition(
    MeleeId id,
    float damage,
    float swingsPerSecond,
    float range,
    float knockback
) {

    /** Seconds between two swings while the trigger is held. */
    public float swingCooldownSeconds() {
        return 1f / swingsPerSecond;
    }

    public String displayName() {
        return id.displayName();
    }

    /** A fresh copy; see {@link WeaponDefinition#copy()} for why this exists. */
    public MeleeDefinition copy() {
        return new MeleeDefinition(id, damage, swingsPerSecond, range, knockback);
    }
}
