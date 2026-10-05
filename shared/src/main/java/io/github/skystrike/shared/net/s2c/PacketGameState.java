package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/**
 * The per-snapshot state broadcast.
 *
 * <p>Phase 0 carries only the heartbeat fields — enough to prove the pipe is live and to let the
 * client show a tick counter. Players, bullets, grenades, effects, drones and cameras are added to
 * this packet as their systems land, and the server reuses one instance per broadcast rather than
 * allocating per tick.
 */
public final class PacketGameState implements Packet {

    /** Simulation tick this snapshot describes. */
    public long tick;

    /** Server wall clock when the snapshot was built, for clock-offset estimation. */
    public long serverTimeMillis;

    /** How many clients are currently joined. */
    public int playerCount;

    public PacketGameState() {
    }

    public PacketGameState(long tick, long serverTimeMillis, int playerCount) {
        this.tick = tick;
        this.serverTimeMillis = serverTimeMillis;
        this.playerCount = playerCount;
    }

    @Override
    public String toString() {
        return "PacketGameState[tick=" + tick
            + ", serverTimeMillis=" + serverTimeMillis
            + ", playerCount=" + playerCount + "]";
    }
}
