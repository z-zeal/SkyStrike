package io.github.skystrike.net;

import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Historical ring buffer of authoritative game state snapshots for remote entity interpolation.
 */
public final class StateBuffer {

    public static final class Snapshot {
        private final long tick;
        private final long timestampMillis;
        private final Map<Integer, Player> players;

        public Snapshot(long tick, long timestampMillis, List<Player> playerList) {
            this.tick = tick;
            this.timestampMillis = timestampMillis;
            Map<Integer, Player> map = new HashMap<>();
            if (playerList != null) {
                for (Player p : playerList) {
                    map.put(p.id, p.copy());
                }
            }
            this.players = Collections.unmodifiableMap(map);
        }

        public long tick() {
            return tick;
        }

        public long timestampMillis() {
            return timestampMillis;
        }

        public Map<Integer, Player> players() {
            return players;
        }
    }

    private static final int MAX_SNAPSHOTS = 64;
    private final List<Snapshot> snapshots = new ArrayList<>();

    public StateBuffer() {
    }

    /**
     * Appends a newly arrived server state snapshot.
     */
    public synchronized void addSnapshot(PacketGameState state) {
        if (state == null) {
            return;
        }
        long time = state.serverTimeMillis > 0 ? state.serverTimeMillis : System.currentTimeMillis();
        snapshots.add(new Snapshot(state.tick, time, state.players));
        while (snapshots.size() > MAX_SNAPSHOTS) {
            snapshots.remove(0);
        }
    }

    public synchronized List<Snapshot> snapshots() {
        return new ArrayList<>(snapshots);
    }

    public synchronized void clear() {
        snapshots.clear();
    }

    public synchronized int size() {
        return snapshots.size();
    }
}
