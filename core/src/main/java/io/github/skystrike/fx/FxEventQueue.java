package io.github.skystrike.fx;

import io.github.skystrike.shared.effect.EffectSpawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The one door through which effect requests enter the FX layer (build plan M7 §8.1, effects
 * plan §12.1 {@code events/FxEventQueue}).
 *
 * <p>Producers enqueue; the particle system drains on the render thread once per frame. The queue
 * is the only entry point, which is what makes "never spawn from the network thread" enforceable:
 * gameplay code never sees a particle, a light or a GL object. Spawns are copied on the way in, so
 * a pooled or reused packet object can never alias live effect state.
 *
 * <p>The queue is concurrent so a producer on any thread is safe, but in this client the producer
 * is the session's packet listener, which already runs on the render thread.
 */
public final class FxEventQueue {

    private final Queue<EffectSpawn> queue = new ConcurrentLinkedQueue<>();

    /** Enqueues one effect request; null is ignored. The spawn is copied. */
    public void enqueue(EffectSpawn spawn) {
        if (spawn != null) {
            queue.add(spawn.copy());
        }
    }

    /** Enqueues every request in a received batch; null and empty batches are ignored. */
    public void enqueueAll(List<EffectSpawn> spawns) {
        if (spawns == null) {
            return;
        }
        for (EffectSpawn spawn : spawns) {
            enqueue(spawn);
        }
    }

    /**
     * Hands every queued request to the consumer and empties the queue. Render thread only — the
     * consumer spawns particles and allocates lights, which must not run concurrently with itself.
     */
    public void drain(java.util.function.Consumer<EffectSpawn> consumer) {
        EffectSpawn spawn;
        while ((spawn = queue.poll()) != null) {
            consumer.accept(spawn);
        }
    }

    /** Drains into a fresh list. Equivalent to {@link #drain} for callers that batch. */
    public List<EffectSpawn> drainToList() {
        List<EffectSpawn> drained = new ArrayList<>();
        drain(drained::add);
        return drained;
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }
}
