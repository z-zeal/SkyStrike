package io.github.skystrike.shared.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LerpTest {

    @Test
    void factorIsZeroForNonPositiveRateOrDelta() {
        assertEquals(0f, Lerp.factor(0f, 0.016f), 0f);
        assertEquals(0f, Lerp.factor(-5f, 0.016f), 0f);
        assertEquals(0f, Lerp.factor(8f, 0f), 0f);
        assertEquals(0f, Lerp.factor(8f, -1f), 0f);
    }

    @Test
    void factorStaysInsideTheUnitRange() {
        assertTrue(Lerp.factor(1000f, 10f) <= 1f);
        assertTrue(Lerp.factor(0.001f, 0.001f) >= 0f);
    }

    @Test
    @DisplayName("stepping twice at dt equals stepping once at 2*dt — the whole point of the helper")
    void smoothingIsFramerateIndependent() {
        float rate = 8f;
        float target = 100f;

        float coarse = Lerp.smooth(0f, target, rate, 1f / 30f);

        float fine = Lerp.smooth(0f, target, rate, 1f / 60f);
        fine = Lerp.smooth(fine, target, rate, 1f / 60f);

        assertEquals(coarse, fine, 1e-3f);
    }

    @Test
    void smoothingConvergesOnTheTargetAndNeverOvershoots() {
        float value = 0f;
        for (int step = 0; step < 600; step++) {
            value = Lerp.smooth(value, 1f, 16f, 1f / 60f);
            assertTrue(value <= 1f, "overshot the target");
        }
        assertEquals(1f, value, 1e-4f);
    }

    @Test
    @DisplayName("angle smoothing crosses the +/-180 seam the short way")
    void angleSmoothingTakesTheShortArc() {
        float result = Lerp.smoothAngle(170f, -170f, 8f, 1f / 60f);
        assertTrue(result > 170f || result <= -179f, "went the long way round: " + result);
        assertTrue(Math.abs(Angles.shortestDelta(170f, result)) < 20f);
    }

    @Test
    void mixIsPlainLinearBlend() {
        assertEquals(5f, Lerp.mix(0f, 10f, 0.5f), 1e-5f);
        assertEquals(10f, Lerp.mix(0f, 10f, 1f), 1e-5f);
    }

    @Test
    void clampBoundsBothEnds() {
        assertEquals(0f, Lerp.clamp(-1f, 0f, 1f), 0f);
        assertEquals(1f, Lerp.clamp(2f, 0f, 1f), 0f);
        assertEquals(0.5f, Lerp.clamp(0.5f, 0f, 1f), 0f);
    }
}
