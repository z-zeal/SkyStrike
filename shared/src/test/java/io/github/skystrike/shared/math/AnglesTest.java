package io.github.skystrike.shared.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnglesTest {

    @Test
    @DisplayName("wrap lands every angle in (-180, 180]")
    void wrapCoversTheWholeRange() {
        assertEquals(0f, Angles.wrap(0f), 1e-4f);
        assertEquals(180f, Angles.wrap(180f), 1e-4f);
        assertEquals(180f, Angles.wrap(-180f), 1e-4f);
        assertEquals(-90f, Angles.wrap(270f), 1e-4f);
        assertEquals(10f, Angles.wrap(370f), 1e-4f);
        assertEquals(-10f, Angles.wrap(-370f), 1e-4f);
        assertEquals(180f, Angles.wrap(900f), 1e-4f);
    }

    @Test
    void wrapIsIdempotent() {
        for (float angle = -1000f; angle <= 1000f; angle += 7.5f) {
            float once = Angles.wrap(angle);
            assertEquals(once, Angles.wrap(once), 1e-4f, "not idempotent at " + angle);
            assertTrue(once > -180.0001f && once <= 180.0001f, "out of range at " + angle);
        }
    }

    @Test
    @DisplayName("shortest delta never takes the long way round")
    void shortestDeltaTakesTheShortArc() {
        assertEquals(20f, Angles.shortestDelta(170f, -170f), 1e-4f);
        assertEquals(-20f, Angles.shortestDelta(-170f, 170f), 1e-4f);
        assertEquals(90f, Angles.shortestDelta(0f, 90f), 1e-4f);
        assertEquals(0f, Angles.shortestDelta(45f, 405f), 1e-4f);
    }

    @Test
    void degreeAndRadianConversionsRoundTrip() {
        assertEquals(90f, Angles.toDegrees(Angles.toRadians(90f)), 1e-3f);
        assertEquals((float) Math.PI, Angles.toRadians(180f), 1e-5f);
    }

    @Test
    void vectorAnglesMatchTheUnitCircle() {
        assertEquals(0f, Angles.ofVector(1f, 0f), 1e-4f);
        assertEquals(90f, Angles.ofVector(0f, 1f), 1e-4f);
        assertEquals(180f, Angles.ofVector(-1f, 0f), 1e-4f);
        assertEquals(-90f, Angles.ofVector(0f, -1f), 1e-4f);
    }
}
