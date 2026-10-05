package io.github.skystrike.server.sim;

import java.util.Locale;

/**
 * Rolling timing for the tick loop.
 *
 * <p>Reports how long the simulation actually spends working versus how long a tick is allowed to
 * take, which is the number that tells you whether the server is keeping up. Output goes to
 * standard output — the server has no rendering layer and never will.
 */
public final class TickProfiler {

    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final double budgetNanos;

    private long tickStartNanos;
    private long windowStartNanos;

    private int samples;
    private long busyNanos;
    private long worstNanos;
    private long bestNanos = Long.MAX_VALUE;
    private int lateTicks;
    private long droppedTicks;

    public TickProfiler(int tickRateHz) {
        if (tickRateHz <= 0) {
            throw new IllegalArgumentException("tickRateHz must be positive: " + tickRateHz);
        }
        this.budgetNanos = 1_000_000_000.0 / tickRateHz;
        this.windowStartNanos = System.nanoTime();
    }

    /** Marks the start of a tick's work. */
    public void beginTick() {
        tickStartNanos = System.nanoTime();
    }

    /** Marks the end of a tick's work and folds it into the current window. */
    public void endTick() {
        long elapsed = System.nanoTime() - tickStartNanos;
        samples++;
        busyNanos += elapsed;
        worstNanos = Math.max(worstNanos, elapsed);
        bestNanos = Math.min(bestNanos, elapsed);
        if (elapsed > budgetNanos) {
            lateTicks++;
        }
    }

    /** Records ticks the loop had to skip because it fell too far behind. */
    public void recordDroppedTicks(long count) {
        droppedTicks += count;
    }

    /** True once {@code intervalSeconds} of wall time has passed since the last report. */
    public boolean shouldReport(double intervalSeconds) {
        if (intervalSeconds <= 0 || samples == 0) {
            return false;
        }
        return System.nanoTime() - windowStartNanos >= intervalSeconds * 1_000_000_000.0;
    }

    /**
     * Formats the current window as one stable, fixed-shape line, then resets the window.
     *
     * <p>Fixed shape matters: this is the line you will be eyeballing for drift for the rest of
     * the project, and a line whose columns move is a line nobody reads.
     */
    public String reportAndReset(long tick) {
        long windowNanos = Math.max(1L, System.nanoTime() - windowStartNanos);
        int counted = Math.max(1, samples);
        double averageMillis = busyNanos / (double) counted / NANOS_PER_MILLI;
        double worstMillis = worstNanos / NANOS_PER_MILLI;
        double bestMillis = (bestNanos == Long.MAX_VALUE ? 0L : bestNanos) / NANOS_PER_MILLI;
        double effectiveRate = samples * 1_000_000_000.0 / windowNanos;
        double load = busyNanos / (counted * budgetNanos) * 100.0;

        String line = String.format(
            Locale.ROOT,
            "tick=%-9d rate=%6.2f Hz  avg=%6.3f ms  min=%6.3f ms  max=%6.3f ms  "
                + "load=%5.1f%%  late=%d  dropped=%d",
            tick, effectiveRate, averageMillis, bestMillis, worstMillis, load, lateTicks, droppedTicks);

        resetWindow();
        return line;
    }

    private void resetWindow() {
        windowStartNanos = System.nanoTime();
        samples = 0;
        busyNanos = 0L;
        worstNanos = 0L;
        bestNanos = Long.MAX_VALUE;
        lateTicks = 0;
    }

    /** Ticks measured in the current window. */
    public int samples() {
        return samples;
    }

    /** Ticks skipped since the loop started. Never reset. */
    public long droppedTicks() {
        return droppedTicks;
    }

    /** Mean work per tick in the current window, in milliseconds. */
    public double averageTickMillis() {
        return samples == 0 ? 0.0 : busyNanos / (double) samples / NANOS_PER_MILLI;
    }

    /** Longest tick in the current window, in milliseconds. */
    public double worstTickMillis() {
        return worstNanos / NANOS_PER_MILLI;
    }
}
