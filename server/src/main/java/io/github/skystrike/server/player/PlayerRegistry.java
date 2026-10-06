package io.github.skystrike.server.player;

import com.esotericsoftware.kryonet.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of active player sessions indexed by player id and network connection.
 */
public final class PlayerRegistry {

    private final Map<Integer, PlayerSession> byId = new ConcurrentHashMap<>();
    private final Map<Integer, PlayerSession> byConnectionId = new ConcurrentHashMap<>();

    public PlayerSession register(Connection connection, int playerId, String name, int teamIndex, float spawnX, float spawnY) {
        PlayerSession session = new PlayerSession(connection, playerId, name, teamIndex, spawnX, spawnY);
        byId.put(playerId, session);
        byConnectionId.put(connection.getID(), session);
        return session;
    }

    public PlayerSession remove(Connection connection) {
        if (connection == null) {
            return null;
        }
        PlayerSession session = byConnectionId.remove(connection.getID());
        if (session != null) {
            byId.remove(session.playerId());
        }
        return session;
    }

    public PlayerSession remove(int playerId) {
        PlayerSession session = byId.remove(playerId);
        if (session != null) {
            byConnectionId.remove(session.connection().getID());
        }
        return session;
    }

    public PlayerSession byPlayerId(int playerId) {
        return byId.get(playerId);
    }

    public PlayerSession byConnection(Connection connection) {
        if (connection == null) {
            return null;
        }
        return byConnectionId.get(connection.getID());
    }

    public Collection<PlayerSession> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int count() {
        return byId.size();
    }

    public int teamCount(int teamIndex) {
        int count = 0;
        for (PlayerSession session : byId.values()) {
            if (session.player().teamIndex == teamIndex) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        byId.clear();
        byConnectionId.clear();
    }

    /**
     * True while any joined session has a server debug toggle on (build plan M3 §4): the
     * snapshot's {@code cheatsActive} flag reads this so the HUD can explain an immortal target.
     */
    public boolean anyCheatActive() {
        for (PlayerSession session : byId.values()) {
            if (session.noclip() || session.godmode() || session.infiniteAmmo()) {
                return true;
            }
        }
        return false;
    }
}
