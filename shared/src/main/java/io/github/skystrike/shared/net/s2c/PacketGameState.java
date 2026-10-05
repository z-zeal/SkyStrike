package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.Packet;
import java.util.ArrayList;
import java.util.List;

/**
 * The per-snapshot state broadcast.
 *
 * <p>Phase 1 carries the full state of all active players in the match.
 */
public final class PacketGameState implements Packet {

    /** Simulation tick this snapshot describes. */
    public long tick;

    /** Server wall clock when the snapshot was built, for clock-offset estimation. */
    public long serverTimeMillis;

    /** How many clients are currently joined. */
    public int playerCount;

    /** Snapshot of all active player states. */
    public List<Player> players = new ArrayList<>();

    public PacketGameState() {
    }

    public PacketGameState(long tick, long serverTimeMillis, int playerCount) {
        this.tick = tick;
        this.serverTimeMillis = serverTimeMillis;
        this.playerCount = playerCount;
    }

    public PacketGameState(long tick, long serverTimeMillis, int playerCount, List<Player> players) {
        this.tick = tick;
        this.serverTimeMillis = serverTimeMillis;
        this.playerCount = playerCount;
        if (players != null) {
            this.players = new ArrayList<>(players);
        }
    }

    @Override
    public String toString() {
        return "PacketGameState[tick=" + tick
            + ", serverTimeMillis=" + serverTimeMillis
            + ", playerCount=" + playerCount
            + ", players=" + players.size() + "]";
    }
}
