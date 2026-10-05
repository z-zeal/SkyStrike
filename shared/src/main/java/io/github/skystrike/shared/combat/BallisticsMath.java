package io.github.skystrike.shared.combat;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.WeaponBallistics;

/**
 * Projectile flight and damage falloff — the one copy both sides integrate (mechanics §4.2).
 *
 * <p>Rounds are physical: they travel, they slow down, and they drop. Three details matter and
 * all three are here rather than in a system class, so the server and the client cannot disagree
 * about where a bullet is.
 *
 * <ol>
 *   <li><b>Drag is framerate-independent.</b> The weapon table quotes a per-tick coefficient; this
 *       raises it to {@code dt × tickRate} so a 30 Hz and a 60 Hz step bleed the same speed over
 *       the same wall-clock time.</li>
 *   <li><b>Gravity ramps in</b> over the weapon's ramp time with a smoothstep, so there is no
 *       kink in the trajectory when the ramp completes and short-range shots are genuinely flat.</li>
 *   <li><b>Damage falls off linearly</b> with path length travelled, not with straight-line
 *       distance from the shooter — a round that arcs across the arena has flown further than the
 *       gap it crossed.</li>
 * </ol>
 */
public final class BallisticsMath {

    private BallisticsMath() {
    }

    /**
     * Fraction of the weapon's drop that applies at {@code ageSeconds}, in {@code [0, 1]}.
     *
     * <p>Smoothstep rather than a linear ramp: the first derivative is continuous at both ends,
     * so the trajectory has no visible corner where the ramp finishes.
     */
    public static float gravityRampFactor(float ageSeconds, float rampSeconds) {
        if (rampSeconds <= 0f) {
            return 1f;
        }
        float t = Lerp.clamp(ageSeconds / rampSeconds, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Signed drop acceleration in units/s² at {@code ageSeconds}. Always ≤ 0. */
    public static float gravityAcceleration(WeaponBallistics ballistics, float ageSeconds) {
        return ballistics.terminalGravity() * gravityRampFactor(ageSeconds, ballistics.gravityRampSeconds());
    }

    /**
     * Speed multiplier for one step of {@code dt} seconds given a per-tick drag coefficient.
     *
     * <p>{@code drag^(dt × tickRate)}: at exactly one tick this is the quoted coefficient, and at
     * any other step length it is the same bleed per unit of time.
     */
    public static float dragDecay(float dragPerTick, float dt) {
        if (dt <= 0f) {
            return 1f;
        }
        if (dragPerTick >= 1f) {
            return 1f;
        }
        return (float) Math.pow(dragPerTick, dt * WorldConfig.TICK_RATE_HZ);
    }

    /**
     * Damage retained after travelling {@code distanceTravelled}, as a ratio in
     * {@code [minDamageRatio, 1]}. Linear to the floor at maximum range, flat beyond it.
     */
    public static float damageRatio(float distanceTravelled, float maxRange, float minDamageRatio) {
        if (maxRange <= 0f) {
            return minDamageRatio;
        }
        float t = Lerp.clamp(distanceTravelled / maxRange, 0f, 1f);
        return Lerp.mix(1f, minDamageRatio, t);
    }

    /** Damage actually dealt by a round of {@code baseDamage} after {@code distanceTravelled}. */
    public static float damageAfterFalloff(
            float baseDamage, float distanceTravelled, WeaponBallistics ballistics) {
        return baseDamage
            * damageRatio(distanceTravelled, ballistics.maxRange(), ballistics.minDamageRatio());
    }

    /**
     * True when a round is fast enough that point-testing its new position would let it pass
     * through thin geometry, and the previous-to-current segment has to be swept instead.
     *
     * <p>Every gun in the table clears this by an order of magnitude; the threshold exists so
     * slow projectiles added later (and bounced grenades in Phase 5) are not swept needlessly.
     */
    public static boolean requiresSweep(float speed) {
        return speed > CombatConfig.SWEEP_SPEED_THRESHOLD;
    }

    /** Convenience overload for a live round. */
    public static boolean requiresSweep(Projectile projectile) {
        return requiresSweep(projectile.speed());
    }

    /** Muzzle velocity component along {@code angleDegrees}. */
    public static float muzzleVelocityX(float angleDegrees, float muzzleSpeed) {
        return muzzleSpeed * (float) Math.cos(Angles.toRadians(angleDegrees));
    }

    /** Muzzle velocity component along {@code angleDegrees}. */
    public static float muzzleVelocityY(float angleDegrees, float muzzleSpeed) {
        return muzzleSpeed * (float) Math.sin(Angles.toRadians(angleDegrees));
    }

    /**
     * Advances one round by {@code dt}, recording where it started the step.
     *
     * <p>Order is drag, then gravity, then integration — gravity is applied to the post-drag
     * velocity so a stalling round still accelerates downward at the full rate.
     */
    public static void step(Projectile projectile, WeaponBallistics ballistics, float dt) {
        if (projectile == null || ballistics == null || dt <= 0f) {
            return;
        }

        projectile.prevX = projectile.x;
        projectile.prevY = projectile.y;

        float decay = dragDecay(ballistics.dragPerTick(), dt);
        projectile.vx *= decay;
        projectile.vy *= decay;

        projectile.vy += gravityAcceleration(ballistics, projectile.age) * dt;

        float dx = projectile.vx * dt;
        float dy = projectile.vy * dt;
        projectile.x += dx;
        projectile.y += dy;

        projectile.distanceTravelled += (float) Math.sqrt(dx * dx + dy * dy);
        projectile.age += dt;
    }

    /**
     * True when a round has stopped being a bullet: out of time, out of energy, or so far past
     * its maximum range that it is only doing floor damage to things it was never aimed at.
     */
    public static boolean isExpired(Projectile projectile, WeaponBallistics ballistics) {
        if (projectile.age >= CombatConfig.MAX_PROJECTILE_LIFETIME) {
            return true;
        }
        if (projectile.speed() <= CombatConfig.PROJECTILE_MIN_SPEED) {
            return true;
        }
        return projectile.distanceTravelled
            >= ballistics.maxRange() * CombatConfig.MAX_RANGE_OVERSHOOT;
    }
}
