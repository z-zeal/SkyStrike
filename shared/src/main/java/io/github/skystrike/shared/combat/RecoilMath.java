package io.github.skystrike.shared.combat;

import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;

/**
 * The three recoil channels (mechanics §4.4).
 *
 * <ol>
 *   <li><b>Linear</b> — a real push to the player's velocity, opposite the aim. Heavy weapons
 *       shove you, and firing downward while airborne is a legitimate way to gain height.</li>
 *   <li><b>Angular</b> — a torque on the body. The gun sits above the centre of mass, so a push
 *       to the left spins the body counter-clockwise; in the air that reads as a tumble.</li>
 *   <li><b>Visual</b> — the drawn gun angle kicks up and decays at 120 deg/s, capped at 35°.</li>
 * </ol>
 *
 * <p>All three share one multiplier, built from the ADS blend, movement, whether the player is
 * airborne, and whether the volley was a burst. The ADS part is a blend and not a branch: the
 * plan is explicit that it lerps rather than snaps.
 */
public final class RecoilMath {

    private RecoilMath() {
    }

    /**
     * Eases the ADS blend toward 1 while aiming and 0 otherwise, framerate-independently.
     */
    public static float blendAds(float currentBlend, boolean aiming, float dt) {
        return Lerp.smooth(currentBlend, aiming ? 1f : 0f, WeaponConfig.ADS_RECOIL_BLEND_RATE, dt);
    }

    /**
     * The multiplier every channel is scaled by.
     *
     * @param adsBlend           0 at the hip, 1 fully aimed, interpolated in between
     * @param adsMultiplier      the weapon's ADS recoil value, 0.45–0.78
     * @param moving             horizontal speed above the stance threshold
     * @param movingMultiplier   the weapon's moving recoil value, 1.4–2.0
     * @param airborne           not grounded, which scales everything to 0.25
     * @param burst              this volley was a burst, worth 1.20× once
     */
    public static float recoilMultiplier(
            float adsBlend,
            float adsMultiplier,
            boolean moving,
            float movingMultiplier,
            boolean airborne,
            boolean burst) {
        float blend = Lerp.clamp(adsBlend, 0f, 1f);
        float multiplier = Lerp.mix(1f, adsMultiplier, blend);
        if (moving) {
            multiplier *= movingMultiplier;
        }
        if (airborne) {
            multiplier *= WeaponConfig.AIRBORNE_RECOIL_MULTIPLIER;
        }
        if (burst) {
            multiplier *= WeaponConfig.BURST_RECOIL_MULTIPLIER;
        }
        return multiplier;
    }

    /** Horizontal velocity change from one volley: opposite the aim. */
    public static float linearPushX(float aimAngleDegrees, float impulse) {
        return -impulse * (float) Math.cos(Angles.toRadians(aimAngleDegrees));
    }

    /**
     * Vertical velocity change from one volley: opposite the aim.
     *
     * <p>Aiming down therefore pushes up — the airborne height gain the plan asks for falls out
     * of the maths rather than being a special case.
     */
    public static float linearPushY(float aimAngleDegrees, float impulse) {
        return -impulse * (float) Math.sin(Angles.toRadians(aimAngleDegrees));
    }

    /**
     * Angular velocity change in deg/s from one volley.
     *
     * <p>The recoil force acts at the gun, above the centre of mass. Taking the cross product of
     * that offset with the force leaves a torque proportional to {@code cos(aim)}: firing right
     * spins the body counter-clockwise, firing left spins it clockwise, and firing straight up or
     * down applies no spin at all.
     */
    public static float angularKick(float aimAngleDegrees, float angularImpulse) {
        return angularImpulse * (float) Math.cos(Angles.toRadians(aimAngleDegrees));
    }

    /** Adds a visual gun kick, capped at 35°. */
    public static float addVisualKick(float currentKick, float kickDegrees) {
        return Lerp.clamp(
            currentKick + kickDegrees,
            -WeaponConfig.VISUAL_KICK_MAX_DEGREES,
            WeaponConfig.VISUAL_KICK_MAX_DEGREES);
    }

    /** Decays the visual gun kick toward zero at 120 deg/s. */
    public static float decayVisualKick(float currentKick, float dt) {
        if (dt <= 0f || currentKick == 0f) {
            return currentKick;
        }
        float step = WeaponConfig.VISUAL_KICK_DECAY_DEGREES_PER_SECOND * dt;
        if (Math.abs(currentKick) <= step) {
            return 0f;
        }
        return currentKick - Math.signum(currentKick) * step;
    }

    /** Seconds for a kick of {@code degrees} to decay back to zero. */
    public static float visualKickDecaySeconds(float degrees) {
        return Math.abs(degrees) / WeaponConfig.VISUAL_KICK_DECAY_DEGREES_PER_SECOND;
    }
}
