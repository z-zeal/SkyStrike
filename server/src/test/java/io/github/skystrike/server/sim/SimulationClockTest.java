package io.github.skystrike.server.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SimulationClockTest {

    @Test
    void startsAtTickZero() {
        SimulationClock clock = new SimulationClock(60);
        assertEquals(0L, clock.tick());
        assertEquals(0.0, clock.elapsedSeconds(), 0.0);
    }

    @Test
    void stepLengthIsTheReciprocalOfTheRate() {
        SimulationClock clock = new SimulationClock(60);
        assertEquals(1.0 / 60.0, clock.dtSeconds(), 1e-12);
        assertEquals(1f / 60f, clock.dt(), 1e-7f);
    }

    @Test
    void advancingReturnsTheNewTick() {
        SimulationClock clock = new SimulationClock(60);
        assertEquals(1L, clock.advance());
        assertEquals(2L, clock.advance());
        assertEquals(2L, clock.tick());
    }

    @Test
    void elapsedTimeIsDerivedFromTicksNotTheWallClock() {
        SimulationClock clock = new SimulationClock(50);
        for (int i = 0; i < 100; i++) {
            clock.advance();
        }
        assertEquals(2.0, clock.elapsedSeconds(), 1e-9);
    }

    @Test
    void rejectsANonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(0));
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(-1));
    }
}
