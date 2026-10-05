package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.ConnectionRegistry;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;

/**
 * Handles a clean departure: free the slot now rather than waiting for the keep-alive to lapse.
 *
 * <p>The transport-level disconnect that follows is handled separately, and releasing a slot twice
 * is a no-op by design.
 */
public final class LeaveRequestHandler implements PacketHandler<PacketLeaveRequest> {

    private final ConnectionRegistry connections;
    private final PlayerRegistry players;

    public LeaveRequestHandler(ConnectionRegistry connections) {
        this(connections, null);
    }

    public LeaveRequestHandler(ConnectionRegistry connections, PlayerRegistry players) {
        this.connections = connections;
        this.players = players;
    }

    @Override
    public void handle(Connection connection, PacketLeaveRequest packet) {
        if (players != null) {
            players.remove(connection);
        }
        ConnectionRegistry.Entry entry = connections.leave(connection);
        if (entry != null) {
            System.out.printf(
                "[net] leave player=%d name=%s (%d/%d)%n",
                entry.playerId(), entry.name(), connections.count(), connections.maxPlayers());
        }
        connection.close();
    }
}
