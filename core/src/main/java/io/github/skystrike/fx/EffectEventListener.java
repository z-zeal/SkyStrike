package io.github.skystrike.fx;

import io.github.skystrike.shared.effect.EffectSpawn;

/**
 * A second consumer of the effect-event channel (roadmap Phase 9).
 *
 * <p>The effects plan gives the FX layer one entry point: a thread-safe queue that gameplay and
 * network code only ever enqueue into, drained once per frame on the render thread. Phase 9 needed
 * audio to react to exactly the same events — one gameplay event, both layers reacting — and the
 * cheapest correct way to get that is not a second queue, or a second gameplay hook, but a second
 * consumer of the one drain.
 *
 * <p>So {@code FxPipeline} hands every drained spawn to the particle system and then to whatever
 * {@link EffectEventListener} is installed. The listener is optional (a headless or test pipeline
 * has none), it is called on the render thread, and it must never mutate the spawn: the same
 * object is handed on, and gameplay state is already settled by the time an event arrives.
 *
 * <p>The interface lives here rather than in {@code audio} on purpose: {@code fx} must not know
 * that sound exists, and the audio layer is the one that depends on this package, not the reverse.
 */
@FunctionalInterface
public interface EffectEventListener {

    /** Called once per drained effect request, on the render thread, in emission order. */
    void onEffect(EffectSpawn spawn);
}
