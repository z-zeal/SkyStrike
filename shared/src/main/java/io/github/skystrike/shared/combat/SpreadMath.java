package io.github.skystrike.shared.combat;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.math.Lerp;
import java.util.Random;

/**
 * Dynamic, stance-driven spread (mechanics §4.3).
 *
 * <p>Spread is a live value per player per weapon, continuously pulled toward a target that
 * depends on stance, kicked upward by every round, and capped at a per-weapon ceiling. Nothing
 * here holds state — the live value lives on the gun instance that owns it — so prediction and
 * authority can run the identical arithmetic.
 *
 * <p>Two choices worth naming. Recovery is <b>linear in degrees per second</b>, not an
 * exponential ease: the plan quotes a rate in deg/s, and a linear pull makes "how long until I am
 * accurate again" a number the player can learn. Deviation is drawn from a <b>normal</b>
 * distribution, so most rounds cluster near the centre of the cone instead of painting it evenly.
 */
public final class SpreadMath {

    private SpreadMath() {
    }

    /** True when a player's horizontal speed counts as movement for spread and recoil. */
    public static boolean isMoving(float horizontalSpeed) {
        return Math.abs(horizontalSpeed) > CombatConfig.MOVING_SPEED_THRESHOLD;
    }

    /**
     * The 2×2 stance table.
     *
     * <pre>
     *   still  + hip    base
     *   still  + aiming base × adsRatio
     *   moving + hip    base × movingMultiplier
     *   moving + aiming base × adsRatio × movingMultiplier × 0.6
     * </pre>
     */
    public static float stanceTargetSpread(
            float baseSpreadDegrees,
            float adsSpreadRatio,
            float movingSpreadMultiplier,
            boolean moving,
            boolean aiming) {
        float target = baseSpreadDegrees;
        if (aiming) {
            target *= adsSpreadRatio;
        }
        if (moving) {
            target *= movingSpreadMultiplier;
            if (aiming) {
                target *= WeaponConfig.MOVING_ADS_SPREAD_FACTOR;
            }
        }
        return Math.max(0f, target);
    }

    /** The per-weapon ceiling, roughly 2.7–4× base spread. */
    public static float ceiling(float baseSpreadDegrees, float ceilingMultiplier) {
        return baseSpreadDegrees * ceilingMultiplier;
    }

    /**
     * Adds one round's kick, scaled by the current recoil multiplier and capped at the ceiling.
     *
     * <p>Scaling by the recoil multiplier is what makes aiming pay twice: the kick is smaller
     * <i>and</i> recovery from it is faster.
     */
    public static float applyShotKick(
            float currentSpread, float kickDegrees, float recoilMultiplier, float ceilingDegrees) {
        float kicked = currentSpread + kickDegrees * recoilMultiplier;
        return Math.min(kicked, ceilingDegrees);
    }

    /**
     * Pulls spread toward the stance target at {@code recoveryDegreesPerSecond}, 1.5× faster
     * while aiming. Works in both directions, so standing still after a sprint tightens the cone
     * at the same rate it opened.
     */
    public static float recover(
            float currentSpread,
            float targetSpread,
            float recoveryDegreesPerSecond,
            boolean aiming,
            float dt) {
        if (dt <= 0f) {
            return currentSpread;
        }
        float rate = recoveryDegreesPerSecond
            * (aiming ? WeaponConfig.ADS_RECOVERY_MULTIPLIER : 1f);
        float step = rate * dt;
        float delta = targetSpread - currentSpread;
        if (Math.abs(delta) <= step) {
            return targetSpread;
        }
        return currentSpread + Math.signum(delta) * step;
    }

    /**
     * Samples one round's angular deviation across the current cone, in degrees.
     *
     * <p>Normal distribution with sigma = half-cone / {@link CombatConfig#SPREAD_SIGMA_DIVISOR},
     * clamped to the cone. At the shipped divisor about 1% of rounds clamp, which keeps the cone
     * a real boundary without piling rounds on its rim.
     */
    public static float sampleDeviation(float spreadDegrees, Random random) {
        return sampleDeviation(spreadDegrees, random, CombatConfig.SPREAD_SIGMA_DIVISOR);
    }

    /** Sampling with an explicit sigma divisor, for tuning and tests. */
    public static float sampleDeviation(float spreadDegrees, Random random, float sigmaDivisor) {
        if (spreadDegrees <= 0f || random == null) {
            return 0f;
        }
        float half = spreadDegrees / 2f;
        float sigma = half / Math.max(0.0001f, sigmaDivisor);
        float deviation = (float) (random.nextGaussian() * sigma);
        return Lerp.clamp(deviation, -half, half);
    }

    /**
     * Fixed angular offset of one round within a burst, before jitter.
     *
     * <p>Centred on the aim: a three-round burst at 0.55° spacing lands at −0.55°, 0°, +0.55°,
     * which reads as a deliberate group rather than a clump.
     */
    public static float burstOffsetDegrees(int roundIndex, int burstRounds, float spacingDegrees) {
        if (burstRounds <= 1) {
            return 0f;
        }
        return (roundIndex - (burstRounds - 1) / 2f) * spacingDegrees;
    }

    /** Random jitter added to a burst round: a fraction of the current spread, both signs. */
    public static float burstJitterDegrees(float currentSpread, Random random) {
        if (random == null) {
            return 0f;
        }
        float amplitude = currentSpread * WeaponConfig.BURST_JITTER_FRACTION;
        return (float) ((random.nextDouble() * 2.0 - 1.0) * amplitude);
    }

    /**
     * Angular offset of one pellet, distributed evenly across the full cone.
     *
     * <p>Endpoints inclusive: six pellets across a 15° cone sit at ±7.5°, ±4.5° and ±1.5°.
     */
    public static float pelletOffsetDegrees(int pelletIndex, int pelletCount, float spreadDegrees) {
        if (pelletCount <= 1) {
            return 0f;
        }
        float half = spreadDegrees / 2f;
        float step = spreadDegrees / (pelletCount - 1);
        return -half + pelletIndex * step;
    }

    /** Random jitter added to a pellet: 1.5° hip, 0.8° aiming. */
    public static float pelletJitterDegrees(boolean aiming, Random random) {
        if (random == null) {
            return 0f;
        }
        float amplitude = aiming
            ? WeaponConfig.PELLET_JITTER_ADS_DEGREES
            : WeaponConfig.PELLET_JITTER_HIP_DEGREES;
        return (float) ((random.nextDouble() * 2.0 - 1.0) * amplitude);
    }
}
