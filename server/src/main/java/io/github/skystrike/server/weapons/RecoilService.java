package io.github.skystrike.server.weapons;

import io.github.skystrike.shared.combat.RecoilMath;
import io.github.skystrike.shared.combat.SpreadMath;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponDefinition;

/**
 * Applies the three recoil channels of mechanics §4.4 to an authoritative player.
 *
 * <p>The maths is in {@code shared/combat/RecoilMath} so prediction can run it too; what lives
 * here is the authority: which multipliers are in force for <i>this</i> player right now, and
 * the actual mutation of their velocity, spin and gun angle.
 *
 * <p>Recoil is applied <b>once per volley</b>, not once per round. A shotgun shell shoves you as
 * one shell rather than six pellets, and a burst applies its 1.20× once.
 */
public final class RecoilService {

    /**
     * The multiplier in force for one volley.
     *
     * @param burst whether this volley is a burst, worth 1.20×
     */
    public float multiplier(Player player, GunInstance gun, boolean burst) {
        WeaponDefinition.RecoilProfile recoil = gun.definition().recoil();
        boolean moving = SpreadMath.isMoving(player.vx);
        boolean airborne = !player.grounded;
        return RecoilMath.recoilMultiplier(
            gun.adsBlend(),
            recoil.adsMultiplier(),
            moving,
            recoil.movingMultiplier(),
            airborne,
            burst);
    }

    /**
     * Applies all three channels at an already-computed multiplier.
     *
     * <p>The multiplier is passed in rather than recomputed because the spread kick is scaled by
     * the same number, and the two must not be able to drift apart.
     */
    public void apply(Player player, GunInstance gun, float aimAngleDegrees, float multiplier) {
        WeaponDefinition.RecoilProfile recoil = gun.definition().recoil();

        // 1. Linear: a real push to velocity, opposite the aim. Firing down in the air lifts you.
        player.vx += RecoilMath.linearPushX(aimAngleDegrees, recoil.linearImpulse() * multiplier);
        player.vy += RecoilMath.linearPushY(aimAngleDegrees, recoil.linearImpulse() * multiplier);

        // 2. Angular: spin on the body, which in the air reads as a tumble.
        player.angularVelocity +=
            RecoilMath.angularKick(aimAngleDegrees, recoil.angularDegreesPerSecond() * multiplier);

        // 3. Visual: the drawn gun angle kicks and decays at 120 deg/s, capped at 35°.
        gun.addVisualKick(recoil.visualKickDegrees() * multiplier);
    }

    /** Convenience: compute the multiplier, apply the channels, and hand the multiplier back. */
    public float applyVolley(Player player, GunInstance gun, float aimAngleDegrees, boolean burst) {
        float multiplier = multiplier(player, gun, burst);
        apply(player, gun, aimAngleDegrees, multiplier);
        return multiplier;
    }
}
