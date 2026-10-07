package io.github.skystrike.fx.particle;

/**
 * The effect time base (build plan M7 §8.2, effects plan §12.1 {@code fx/FxClock}): the single
 * clock every particle, phase and light animation reads.
 *
 * <p>It is deliberately independent of game time: pausing the clock freezes every effect in place
 * without touching the simulation, and advancing it by the frame delta keeps effects frame-rate
 * independent. GPU particles read it as one {@code u_time} uniform per frame — that uniform is the
 * only thing that changes for a particle's entire life.
 */
public final class FxClock {

    private float timeSeconds;
    private boolean paused;

    /** Advances the clock. Negative or zero deltas are ignored; a paused clock does not move. */
    public void advance(float deltaSeconds) {
        if (!paused && deltaSeconds > 0f && Float.isFinite(deltaSeconds)) {
            timeSeconds += deltaSeconds;
        }
    }

    /** Current effect time in seconds. */
    public float time() {
        return timeSeconds;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Rewinds to zero; used when a match is torn down so stale times cannot leak across sessions. */
    public void reset() {
        timeSeconds = 0f;
    }
}
