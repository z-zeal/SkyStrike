package io.github.skystrike.server.sim;

/**
 * The fixed-rate authoritative loop.
 *
 * <p>Ticks are scheduled against an absolute deadline rather than by sleeping for a period, so a
 * sleep that overshoots is paid back by the next tick instead of accumulating into permanent
 * drift. If the loop falls further behind than {@link #MAX_CATCHUP_TICKS} it stops trying to catch
 * up, drops the backlog and resynchronises — a server that spirals trying to replay a lost second
 * is worse than one that admits it lost it.
 */
public final class TickLoop implements Runnable {

    /** How far behind the loop will run before it gives up and resynchronises. */
    public static final int MAX_CATCHUP_TICKS = 10;

    /** One simulation step. Implementations must not block. */
    @FunctionalInterface
    public interface Tick {
        void run(SimulationClock clock);
    }

    /** Called when a profiling window closes, on the tick thread. */
    @FunctionalInterface
    public interface ProfileSink {
        void accept(String report);
    }

    private final SimulationClock clock;
    private final TickProfiler profiler;
    private final Tick tick;
    private final long periodNanos;
    private final double profileIntervalSeconds;

    private ProfileSink profileSink = System.out::println;
    private volatile boolean running;

    public TickLoop(int tickRateHz, double profileIntervalSeconds, Tick tick) {
        if (tick == null) {
            throw new IllegalArgumentException("tick must not be null");
        }
        this.clock = new SimulationClock(tickRateHz);
        this.profiler = new TickProfiler(tickRateHz);
        this.tick = tick;
        this.periodNanos = 1_000_000_000L / tickRateHz;
        this.profileIntervalSeconds = profileIntervalSeconds;
    }

    public SimulationClock clock() {
        return clock;
    }

    public TickProfiler profiler() {
        return profiler;
    }

    /** Redirects profiling output. Defaults to standard output. */
    public void setProfileSink(ProfileSink sink) {
        this.profileSink = sink == null ? report -> { } : sink;
    }

    public boolean isRunning() {
        return running;
    }

    /** Asks the loop to finish the current tick and return. Safe from any thread. */
    public void stop() {
        running = false;
    }

    /** Runs until {@link #stop()} or interruption. Blocks the calling thread. */
    @Override
    public void run() {
        running = true;
        long deadline = System.nanoTime() + periodNanos;

        while (running) {
            if (!sleepUntil(deadline)) {
                break;
            }

            profiler.beginTick();
            tick.run(clock);
            profiler.endTick();
            clock.advance();

            deadline += periodNanos;

            long behindNanos = System.nanoTime() - deadline;
            if (behindNanos > MAX_CATCHUP_TICKS * periodNanos) {
                long dropped = behindNanos / periodNanos;
                profiler.recordDroppedTicks(dropped);
                deadline = System.nanoTime() + periodNanos;
            }

            if (profiler.shouldReport(profileIntervalSeconds)) {
                profileSink.accept(profiler.reportAndReset(clock.tick()));
            }
        }
        running = false;
    }

    /**
     * Sleeps until the deadline, coarsely at first and then spinning for the last millisecond so
     * the tick does not inherit the scheduler's granularity.
     *
     * @return false when the loop should exit instead of running another tick
     */
    private boolean sleepUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L) {
                return true;
            }
            if (remaining > 1_500_000L) {
                try {
                    Thread.sleep((remaining - 1_000_000L) / 1_000_000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            } else {
                Thread.onSpinWait();
            }
            if (!running) {
                return false;
            }
        }
    }
}
