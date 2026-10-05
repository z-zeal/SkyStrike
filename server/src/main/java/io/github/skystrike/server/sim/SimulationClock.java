package io.github.skystrike.server.sim;

/**
 * The authoritative notion of time.
 *
 * <p>Simulation time is counted in whole ticks of a fixed length; it is deliberately not derived
 * from the wall clock, so a slow frame changes when a tick runs, never how long it lasted.
 */
public final class SimulationClock {

    private final double dtSeconds;
    private long tick;

    public SimulationClock(int tickRateHz) {
        if (tickRateHz <= 0) {
            throw new IllegalArgumentException("tickRateHz must be positive: " + tickRateHz);
        }
        this.dtSeconds = 1.0 / tickRateHz;
    }

    /** Ticks completed so far. Starts at zero. */
    public long tick() {
        return tick;
    }

    /** Fixed step length in seconds. */
    public double dtSeconds() {
        return dtSeconds;
    }

    /** Fixed step length as a float, which is what the physics code wants. */
    public float dt() {
        return (float) dtSeconds;
    }

    /** Simulated time elapsed, in seconds. */
    public double elapsedSeconds() {
        return tick * dtSeconds;
    }

    /** Advances by one step and returns the new tick number. */
    public long advance() {
        return ++tick;
    }
}
