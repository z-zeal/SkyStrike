package io.github.skystrike.server.weapons;

import io.github.skystrike.shared.combat.RecoilMath;
import io.github.skystrike.shared.combat.SpreadMath;
import io.github.skystrike.shared.weapons.WeaponId;

/**
 * One player's live state for the gun in their hands.
 *
 * <p>Spread and recoil are not properties of a weapon, they are properties of a weapon
 * <i>being used by someone</i>: two players holding a SCAR-L have independent cones. This is the
 * per-player half, and it is deliberately the only mutable thing in the weapon package.
 *
 * <p>Switching weapons resets the accumulated spread and recoil (roadmap §4.3) — you cannot
 * launder a blown-out cone by tapping 2 and back.
 */
public final class GunInstance {

    private WeaponId weaponId;
    private WeaponStats stats;

    private float currentSpread;
    private float cooldownRemaining;
    private float adsBlend;
    private float visualKick;
    private long roundsFired;

    public GunInstance() {
        this(WeaponId.DEFAULT);
    }

    public GunInstance(WeaponId weaponId) {
        this.weaponId = weaponId;
        this.stats = WeaponStats.of(weaponId);
        this.currentSpread = stats.spread().baseDegrees();
    }

    /** Swaps weapon and clears accumulated spread, recoil and cooldown. No-op if unchanged. */
    public boolean switchTo(WeaponId next) {
        if (next == null || next == weaponId) {
            return false;
        }
        this.weaponId = next;
        this.stats = WeaponStats.of(next);
        this.currentSpread = stats.spread().baseDegrees();
        this.cooldownRemaining = 0f;
        this.visualKick = 0f;
        return true;
    }

    /**
     * Advances cooldown, the ADS recoil blend, spread recovery and the visual kick by one step.
     *
     * <p>Call once per tick, before resolving the trigger: a round fired this tick should see
     * this tick's stance, not the last one's.
     */
    public void update(float dt, boolean moving, boolean aiming) {
        if (dt <= 0f) {
            return;
        }
        cooldownRemaining = Math.max(0f, cooldownRemaining - dt);
        adsBlend = RecoilMath.blendAds(adsBlend, aiming, dt);
        visualKick = RecoilMath.decayVisualKick(visualKick, dt);
        currentSpread = SpreadMath.recover(
            currentSpread,
            stanceTargetSpread(moving, aiming),
            stats.spread().recoveryDegreesPerSecond(),
            aiming,
            dt);
    }

    /** The spread this stance is pulled toward (the 2×2 table of mechanics §4.3). */
    public float stanceTargetSpread(boolean moving, boolean aiming) {
        WeaponStats.SpreadProfile spread = stats.spread();
        return SpreadMath.stanceTargetSpread(
            spread.baseDegrees(), spread.adsRatio(), spread.movingMultiplier(), moving, aiming);
    }

    /** True when the weapon's cycle has elapsed and it may fire again. */
    public boolean isReady() {
        return cooldownRemaining <= 0f;
    }

    /** Starts the weapon's cycle after a trigger event. */
    public void startCooldown() {
        cooldownRemaining = stats.cooldownSeconds();
    }

    /** Adds one round's spread kick, scaled by the current recoil multiplier and capped. */
    public void addSpreadKick(float recoilMultiplier) {
        currentSpread = SpreadMath.applyShotKick(
            currentSpread,
            stats.spread().kickDegrees(),
            recoilMultiplier,
            stats.spread().ceilingDegrees());
    }

    /** Adds the visual gun-angle kick, capped at 35°. */
    public void addVisualKick(float degrees) {
        visualKick = RecoilMath.addVisualKick(visualKick, degrees);
    }

    /** Counts rounds, not trigger pulls: a shotgun shell adds six. */
    public void countRounds(int rounds) {
        roundsFired += rounds;
    }

    public WeaponId weaponId() {
        return weaponId;
    }

    public WeaponStats stats() {
        return stats;
    }

    public float currentSpread() {
        return currentSpread;
    }

    public float cooldownRemaining() {
        return cooldownRemaining;
    }

    public float adsBlend() {
        return adsBlend;
    }

    public float visualKick() {
        return visualKick;
    }

    public long roundsFired() {
        return roundsFired;
    }

    /** Test and tooling hook: force the live cone. */
    public void setCurrentSpread(float degrees) {
        this.currentSpread = Math.max(0f, degrees);
    }

    /** Test and tooling hook: force the ADS blend without waiting for it to ease. */
    public void setAdsBlend(float blend) {
        this.adsBlend = Math.max(0f, Math.min(1f, blend));
    }

    @Override
    public String toString() {
        return "GunInstance[" + weaponId
            + ", spread=" + String.format("%.2f", currentSpread)
            + "°, cooldown=" + String.format("%.3f", cooldownRemaining)
            + "s, adsBlend=" + String.format("%.2f", adsBlend)
            + ", kick=" + String.format("%.1f", visualKick) + "°]";
    }
}
