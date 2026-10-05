package io.github.skystrike.server.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TickProfilerTest {

    @Test
    void countsSamplesPerWindow() {
        TickProfiler profiler = new TickProfiler(60);
        for (int i = 0; i < 3; i++) {
            profiler.beginTick();
            profiler.endTick();
        }
        assertEquals(3, profiler.samples());
        assertTrue(profiler.averageTickMillis() >= 0.0);
        assertTrue(profiler.worstTickMillis() >= 0.0);
    }

    @Test
    void reportingClearsTheWindowButKeepsDroppedTicks() {
        TickProfiler profiler = new TickProfiler(60);
        profiler.beginTick();
        profiler.endTick();
        profiler.recordDroppedTicks(4);

        String report = profiler.reportAndReset(99L);
        assertTrue(report.contains("tick=99"), report);
        assertTrue(report.contains("dropped=4"), report);

        assertEquals(0, profiler.samples());
        assertEquals(4L, profiler.droppedTicks());
    }

    @Test
    @DisplayName("the report line keeps a stable shape so it stays readable in a log")
    void reportShapeIsStable() {
        TickProfiler profiler = new TickProfiler(60);
        profiler.beginTick();
        profiler.endTick();

        String report = profiler.reportAndReset(1L);
        for (String field : new String[] {"rate=", "avg=", "min=", "max=", "load=", "late=", "dropped="}) {
            assertTrue(report.contains(field), "missing " + field + " in: " + report);
        }
        assertFalse(report.contains("\n"), "the report must be a single line");
    }

    @Test
    void noReportIsDueBeforeAnyTickHasRun() {
        TickProfiler profiler = new TickProfiler(60);
        assertFalse(profiler.shouldReport(0.0));
        assertFalse(profiler.shouldReport(5.0));
    }

    @Test
    void rejectsANonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> new TickProfiler(0));
    }
}
