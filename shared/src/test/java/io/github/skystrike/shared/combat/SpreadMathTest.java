package io.github.skystrike.shared.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Spread is the system that makes spraying and tapping different, so these tests are mostly
 * about <i>divergence over time</i> rather than single values.
 */
class SpreadMathTest {

    private static final float EPSILON = 1e-4f;
    private static final float BASE = 4f;
    private static final float ADS_RATIO = 0.5f;
    private static final float MOVING = 2f;

    @Test
    @DisplayName("the stance table is still-hip, still-ADS, moving-hip, moving-ADS")
    void stanceTable() {
        assertEquals(BASE, SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, false, false), EPSILON);
        assertEquals(BASE * ADS_RATIO,
            SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, false, true), EPSILON);
        assertEquals(BASE * MOVING,
            SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, true, false), EPSILON);
        assertEquals(BASE * ADS_RATIO * MOVING * WeaponConfig.MOVING_ADS_SPREAD_FACTOR,
            SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, true, true), EPSILON);

        // Moving while aiming must still be tighter than moving at the hip.
        assertTrue(
            SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, true, true)
                < SpreadMath.stanceTargetSpread(BASE, ADS_RATIO, MOVING, true, false));
    }

    @Test
    @DisplayName("movement is a speed threshold, not a key press")
    void movingThreshold() {
        assertFalse(SpreadMath.isMoving(0f));
        assertFalse(SpreadMath.isMoving(CombatConfig.MOVING_SPEED_THRESHOLD));
        assertTrue(SpreadMath.isMoving(CombatConfig.MOVING_SPEED_THRESHOLD + 1f));
        assertTrue(SpreadMath.isMoving(-(CombatConfig.MOVING_SPEED_THRESHOLD + 1f)),
            "walking left is still walking");
    }

    @Test
    @DisplayName("shot kick is scaled by the recoil multiplier and stops at the ceiling")
    void shotKickIsCapped() {
        float ceiling = SpreadMath.ceiling(BASE, 3f);
        assertEquals(12f, ceiling, EPSILON);

        assertEquals(BASE + 2f, SpreadMath.applyShotKick(BASE, 2f, 1f, ceiling), EPSILON);
        assertEquals(BASE + 1f, SpreadMath.applyShotKick(BASE, 2f, 0.5f, ceiling), EPSILON,
            "aiming halves the kick");

        float sprayed = BASE;
        for (int i = 0; i < 50; i++) {
            sprayed = SpreadMath.applyShotKick(sprayed, 2f, 1f, ceiling);
        }
        assertEquals(ceiling, sprayed, EPSILON, "an endless spray saturates at the ceiling");
    }

    @Test
    @DisplayName("recovery is linear in degrees per second and 1.5x faster while aiming")
    void recoveryRate() {
        // One second of recovery at 6 deg/s moves 6 degrees.
        assertEquals(6f, SpreadMath.recover(12f, 0f, 6f, false, 1f), EPSILON);
        assertEquals(3f, SpreadMath.recover(12f, 0f, 6f, true, 1f), EPSILON,
            "aiming recovers 1.5x faster: 9 degrees in one second");

        // Recovery never overshoots the target.
        assertEquals(2f, SpreadMath.recover(3f, 2f, 100f, false, 1f), EPSILON);
        // It works upward too: standing still after a sprint tightens the cone.
        assertEquals(5f, SpreadMath.recover(2f, 8f, 3f, false, 1f), EPSILON);
        // A zero-length step changes nothing.
        assertEquals(7f, SpreadMath.recover(7f, 0f, 9f, false, 0f), EPSILON);
    }

    @Test
    @DisplayName("spraying and tapping diverge over about a second, not instantly")
    void sprayAndTapDivergeOverTime() {
        float kick = 2.6f;
        float recovery = 6f;
        float ceiling = SpreadMath.ceiling(BASE, 3f);
        float dt = 1f / 60f;

        float spray = BASE;
        float tap = BASE;
        float sprayAfterOneShot = 0f;

        // One second: the sprayer fires every 0.1 s, the tapper every 0.5 s.
        for (int tick = 0; tick < 60; tick++) {
            if (tick % 6 == 0) {
                spray = SpreadMath.applyShotKick(spray, kick, 1f, ceiling);
                if (tick == 0) {
                    sprayAfterOneShot = spray;
                }
            }
            if (tick % 30 == 0) {
                tap = SpreadMath.applyShotKick(tap, kick, 1f, ceiling);
            }
            spray = SpreadMath.recover(spray, BASE, recovery, false, dt);
            tap = SpreadMath.recover(tap, BASE, recovery, false, dt);
        }

        assertTrue(spray > tap * 1.5f,
            "after a second of spray the cone must be much wider: spray " + spray + " tap " + tap);
        assertTrue(sprayAfterOneShot < spray,
            "the gap must build up over the second rather than appear on the first round");
        assertTrue(tap < BASE + kick,
            "a tapper recovers most of each shot before the next: " + tap);
    }

    @Test
    @DisplayName("deviation stays inside the cone and clusters near its centre")
    void deviationIsNormalAndBounded() {
        Random random = new Random(20260305L);
        float spread = 6f;
        float half = spread / 2f;

        int inner = 0;
        int samples = 20000;
        for (int i = 0; i < samples; i++) {
            float deviation = SpreadMath.sampleDeviation(spread, random);
            assertTrue(Math.abs(deviation) <= half + EPSILON, "deviation escaped the cone: " + deviation);
            if (Math.abs(deviation) <= half / 2f) {
                inner++;
            }
        }

        // Normal with sigma = half/2.5 puts ~79% of rounds in the inner half of the cone;
        // a uniform distribution would put 50%.
        float innerFraction = inner / (float) samples;
        assertTrue(innerFraction > 0.70f && innerFraction < 0.88f,
            "expected a normal cluster, got " + innerFraction);

        assertEquals(0f, SpreadMath.sampleDeviation(0f, random), EPSILON);
        assertEquals(0f, SpreadMath.sampleDeviation(spread, null), EPSILON);
    }

    @Test
    @DisplayName("a three round burst is centred on the aim at 0.55 degree spacing")
    void burstPattern() {
        float spacing = WeaponConfig.BURST_SPACING_DEGREES;
        assertEquals(-spacing, SpreadMath.burstOffsetDegrees(0, 3, spacing), EPSILON);
        assertEquals(0f, SpreadMath.burstOffsetDegrees(1, 3, spacing), EPSILON);
        assertEquals(spacing, SpreadMath.burstOffsetDegrees(2, 3, spacing), EPSILON);
        assertEquals(0f, SpreadMath.burstOffsetDegrees(0, 1, spacing), EPSILON);

        float sum = 0f;
        for (int i = 0; i < 3; i++) {
            sum += SpreadMath.burstOffsetDegrees(i, 3, spacing);
        }
        assertEquals(0f, sum, EPSILON, "the pattern must be symmetric about the aim");
    }

    @Test
    @DisplayName("burst jitter is a fifth of the live spread, both signs")
    void burstJitter() {
        Random random = new Random(7L);
        float spread = 5f;
        float limit = spread * WeaponConfig.BURST_JITTER_FRACTION;
        boolean sawNegative = false;
        boolean sawPositive = false;
        for (int i = 0; i < 500; i++) {
            float jitter = SpreadMath.burstJitterDegrees(spread, random);
            assertTrue(Math.abs(jitter) <= limit + EPSILON);
            sawNegative |= jitter < 0f;
            sawPositive |= jitter > 0f;
        }
        assertTrue(sawNegative && sawPositive);
    }

    @Test
    @DisplayName("six pellets spread evenly across the whole cone, endpoints included")
    void pelletPattern() {
        float spread = 15f;
        int pellets = WeaponConfig.SHOTGUN_PELLETS;

        assertEquals(-7.5f, SpreadMath.pelletOffsetDegrees(0, pellets, spread), EPSILON);
        assertEquals(7.5f, SpreadMath.pelletOffsetDegrees(pellets - 1, pellets, spread), EPSILON);

        float step = spread / (pellets - 1);
        for (int i = 1; i < pellets; i++) {
            float delta = SpreadMath.pelletOffsetDegrees(i, pellets, spread)
                - SpreadMath.pelletOffsetDegrees(i - 1, pellets, spread);
            assertEquals(step, delta, EPSILON, "pellet spacing must be even");
        }
    }

    @Test
    @DisplayName("pellet jitter is smaller while aiming")
    void pelletJitterShrinksWhenAiming() {
        Random random = new Random(11L);
        float worstHip = 0f;
        float worstAds = 0f;
        for (int i = 0; i < 2000; i++) {
            worstHip = Math.max(worstHip, Math.abs(SpreadMath.pelletJitterDegrees(false, random)));
            worstAds = Math.max(worstAds, Math.abs(SpreadMath.pelletJitterDegrees(true, random)));
        }
        assertTrue(worstHip <= WeaponConfig.PELLET_JITTER_HIP_DEGREES + EPSILON);
        assertTrue(worstAds <= WeaponConfig.PELLET_JITTER_ADS_DEGREES + EPSILON);
        assertTrue(worstAds < worstHip);
    }
}
