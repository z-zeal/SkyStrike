package io.github.skystrike.shared.utility;

/**
 * Everything the simulation needs to know about one throwable (mechanics §6 table).
 *
 * <p>A record, so unlike {@code WeaponDefinition} the registry can hand out the same instance to
 * everybody: there is no mutable field for one player's grenade to leak into another's.
 *
 * @param id              which throwable this describes
 * @param throwForce      initial speed along the aim direction, or 0 for a placed device
 * @param detonation      what sets it off
 * @param effect          what it does when it does
 * @param fuseSeconds     timer for {@link DetonationMode#FUSE}; ignored otherwise
 * @param radius          blast, cloud or stun radius in world units
 * @param damage          direct damage, or damage per {@code UtilityConfig.DOT_TICK_SECONDS}
 *                        for a damage-over-time effect
 * @param impulse         physics impulse applied at the centre of a blast
 * @param durationSeconds how long the effect lasts once it has gone off — a cloud's
 *                        lifetime, a fire's burn time, a flashbang's blindness; 0 when the
 *                        effect resolves in a single instant
 * @param cooldownSeconds minimum interval between two throws of this utility
 * @param carriedCount    how many a player starts a life with
 * @param provisional     true when the mechanics plan leaves this row's numbers blank and they
 *                        were chosen here rather than specified
 */
public record UtilityDefinition(
    UtilityId id,
    float throwForce,
    DetonationMode detonation,
    UtilityEffect effect,
    float fuseSeconds,
    float radius,
    float damage,
    float impulse,
    float durationSeconds,
    float cooldownSeconds,
    int carriedCount,
    boolean provisional
) {

    public UtilityDefinition {
        if (id == null || detonation == null || effect == null) {
            throw new IllegalArgumentException("id, detonation and effect are required");
        }
        if (radius < 0f || damage < 0f || cooldownSeconds < 0f || carriedCount < 0) {
            throw new IllegalArgumentException("utility numbers must not be negative: " + id);
        }
    }

    public String displayName() {
        return id.displayName();
    }

    /** This utility's encoding in the shared weapon-id space. */
    public int wireId() {
        return id.wireId();
    }

    /** True when the throwable goes off on first contact rather than on a timer. */
    public boolean detonatesOnContact() {
        return detonation == DetonationMode.CONTACT;
    }

    /** True when it leaves a lasting zone behind. */
    public boolean isPersistent() {
        return effect.isPersistent();
    }

    /** Damage per second for a damage-over-time effect, for the HUD and for balance checks. */
    public float damagePerSecond(float dotTickSeconds) {
        if (!effect.isDamageOverTime() || dotTickSeconds <= 0f) {
            return 0f;
        }
        return damage / dotTickSeconds;
    }
}
