package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.net.Packet;
import java.util.ArrayList;
import java.util.List;

/**
 * A batch of effect requests, sent unreliably at snapshot cadence (playable build plan M7 §8.1).
 *
 * <p>The server accumulates the tick's {@link EffectSpawn}s, culls them per recipient with
 * {@code VisionMath} — exactly as entity visibility is judged, but for ephemeral visuals, where
 * dropping a hidden explosion is correct rather than a despawn bug — and sends one packet per
 * recipient with whatever survived. Batching into the snapshot broadcast keeps effect traffic off
 * the reliable channel and off the per-tick event path.
 *
 * <p>Unreliable by design: the packet carries presentation only, so a lost batch costs a missing
 * muzzle flash, never a wrong game state. The client drains it on the render thread into an
 * {@code FxEventQueue}; nothing is ever spawned from the network thread.
 */
public final class PacketEffectSpawn implements Packet {

    /** Simulation tick the batch was built on, for diagnostics and stale-batch rejection. */
    public long tick;

    /** Effect requests visible to the recipient, in emission order. */
    public List<EffectSpawn> effects = new ArrayList<>();

    public PacketEffectSpawn() {
    }

    public PacketEffectSpawn(long tick, List<EffectSpawn> effects) {
        this.tick = tick;
        if (effects != null) {
            this.effects = new ArrayList<>(effects);
        }
    }

    public int effectCount() {
        return effects.size();
    }

    @Override
    public String toString() {
        return "PacketEffectSpawn[tick=" + tick + ", effects=" + effects.size() + "]";
    }
}
