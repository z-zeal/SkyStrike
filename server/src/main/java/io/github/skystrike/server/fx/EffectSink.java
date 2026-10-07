package io.github.skystrike.server.fx;

import io.github.skystrike.shared.effect.EffectSpawn;

/**
 * Where gameplay systems hand their visual events (playable build plan M7 §8.1).
 *
 * <p>Emission is fire-and-forget: the sink batches, the snapshot broadcast culls and sends. A
 * system that has no sink (unit tests, geometry-only simulations) simply emits nothing — every
 * call site null-checks, and the sink being absent must never change gameplay behaviour.
 */
@FunctionalInterface
public interface EffectSink {

    /** Records one effect request for the current snapshot window. Never blocks, never throws. */
    void emit(EffectSpawn spawn);
}
