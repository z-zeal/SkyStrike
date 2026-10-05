package io.github.skystrike.server.net;

import com.esotericsoftware.kryonet.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mapping between a transport connection and a joined player.
 *
 * <p>A connection exists from the moment the socket opens; a player exists only once the join
 * handshake succeeds. Keeping the two separate is what lets the server reject a join without
 * having already allocated a slot.
 *
 * <p><b>Threading.</b> Touched only from the tick thread, because the events that mutate it are
 * drained there. Deliberately unsynchronised: if that ever stops being true the fix is to move the
 * caller back onto the tick thread, not to add a lock here.
 */
public final class ConnectionRegistry {

    /** One joined player. */
    public record Entry(int connectionId, Connection connection, int playerId, String name) {
    }

    private final Map<Integer, Entry> byConnectionId = new LinkedHashMap<>();
    private final int maxPlayers;
    private int nextPlayerId = 1;

    public ConnectionRegistry(int maxPlayers) {
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive: " + maxPlayers);
        }
        this.maxPlayers = maxPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int count() {
        return byConnectionId.size();
    }

    public boolean isFull() {
        return byConnectionId.size() >= maxPlayers;
    }

    public boolean hasJoined(Connection connection) {
        return byConnectionId.containsKey(connection.getID());
    }

    public Entry byConnection(Connection connection) {
        return byConnectionId.get(connection.getID());
    }

    public Entry byPlayerId(int playerId) {
        for (Entry entry : byConnectionId.values()) {
            if (entry.playerId() == playerId) {
                return entry;
            }
        }
        return null;
    }

    /** Joined players in join order. Read-only view. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(byConnectionId.values());
    }

    /**
     * Allocates a player id for a connection.
     *
     * @return the new entry, or {@code null} if the server is full or this connection already
     *     joined
     */
    public Entry join(Connection connection, String name) {
        if (isFull() || hasJoined(connection)) {
            return null;
        }
        Entry entry = new Entry(connection.getID(), connection, nextPlayerId++, name);
        byConnectionId.put(entry.connectionId(), entry);
        return entry;
    }

    /**
     * Releases a connection's slot.
     *
     * @return the removed entry, or {@code null} if the connection had never joined
     */
    public Entry leave(Connection connection) {
        return byConnectionId.remove(connection.getID());
    }

    /** Drops everything. Used on shutdown. */
    public void clear() {
        byConnectionId.clear();
    }
}
