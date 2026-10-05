package io.github.skystrike.shared.weapons;

import io.github.skystrike.shared.config.WeaponConfig;

/**
 * Immutable definition of one gun: the weapon table of mechanics §5.1 plus the spread and recoil
 * tuning of §4.3 and §4.4, in a form both sides may read.
 *
 * <p>The client needs the ammunition numbers (magazine, reserve, reload) and the ADS spread ratio
 * to mirror the server: its HUD and its predicted loadout must agree with the authoritative
 * numbers exactly, so there is one table, here, and no second copy anywhere. The server remains
 * authoritative for <i>live</i> state — accumulated spread, cooldowns and ammunition in flight —
 * which never leaves the host except as snapshot values.
 *
 * @param id            which gun
 * @param damage        muzzle damage of one round (one pellet, for shotguns)
 * @param fireRateHz    trigger events per second — bursts per second for {@link FireMode#BURST}
 *                      and shells per second for {@link FireMode#SHOTGUN}
 * @param magazineSize  rounds per magazine (shells, for shotguns)
 * @param reserveAmmo   rounds carried beyond the magazine
 * @param reloadSeconds reload time
 * @param fireMode      how a trigger pull becomes rounds
 * @param pelletCount   rounds produced per trigger event by a shell
 * @param spread        stance spread tuning
 * @param recoil        the three recoil channels
 * @param ballistics    flight and falloff
 */
public record WeaponDefinition(
    WeaponId id,
    float damage,
    float fireRateHz,
    int magazineSize,
    int reserveAmmo,
    float reloadSeconds,
    FireMode fireMode,
    int pelletCount,
    SpreadProfile spread,
    RecoilProfile recoil,
    WeaponBallistics ballistics
) {

    /**
     * Stance spread tuning (mechanics §4.3).
     *
     * @param baseDegrees        hip-fire spread while standing still
     * @param adsRatio           fraction of base spread while aiming — AWP 18%, P90 42%
     * @param movingMultiplier   1.8–2.8× while moving
     * @param kickDegrees        2.1–4.8° added per round, before the recoil multiplier
     * @param ceilingMultiplier  2.7–4× base spread
     * @param recoveryDegreesPerSecond 4–9.5 deg/s, 1.5× faster while aiming
     */
    public record SpreadProfile(
        float baseDegrees,
        float adsRatio,
        float movingMultiplier,
        float kickDegrees,
        float ceilingMultiplier,
        float recoveryDegreesPerSecond
    ) {
        /** The absolute ceiling in degrees. */
        public float ceilingDegrees() {
            return baseDegrees * ceilingMultiplier;
        }
    }

    /**
     * The three recoil channels (mechanics §4.4).
     *
     * @param adsMultiplier     0.45–0.78, lerped toward while aiming
     * @param movingMultiplier  1.4–2.0× while moving
     * @param linearImpulse     units/s added to the player's velocity, opposite the aim
     * @param angularDegreesPerSecond spin added to the body
     * @param visualKickDegrees gun-angle offset added per volley
     */
    public record RecoilProfile(
        float adsMultiplier,
        float movingMultiplier,
        float linearImpulse,
        float angularDegreesPerSecond,
        float visualKickDegrees
    ) {
    }

    /** Seconds between two trigger events. */
    public float cooldownSeconds() {
        return 1f / fireRateHz;
    }

    /** Rounds produced by one trigger event. */
    public int roundsPerTriggerEvent() {
        return switch (fireMode) {
            case BURST -> WeaponConfig.BURST_ROUNDS;
            case SHOTGUN -> pelletCount;
            default -> 1;
        };
    }

    /**
     * Magazine units spent by one trigger event. A burst spends its three rounds; every other
     * mode spends one unit per event — a shotgun magazine counts <i>shells</i>, not pellets.
     */
    public int magazineCostPerTriggerEvent() {
        return fireMode == FireMode.BURST ? WeaponConfig.BURST_ROUNDS : 1;
    }

    /** Full damage of one trigger event at the muzzle, ignoring zones. */
    public float volleyDamage() {
        return damage * roundsPerTriggerEvent();
    }

    public String displayName() {
        return id.displayName();
    }

    /**
     * A fresh copy. Definitions are deeply immutable records, so this is cheap and currently a
     * formality — but {@link WeaponRegistry} promises copies, and the promise must survive the
     * day someone adds a mutable component to a definition.
     */
    public WeaponDefinition copy() {
        return new WeaponDefinition(
            id,
            damage,
            fireRateHz,
            magazineSize,
            reserveAmmo,
            reloadSeconds,
            fireMode,
            pelletCount,
            spread,
            recoil,
            ballistics);
    }
}
