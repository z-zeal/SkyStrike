package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * Handles incoming client input packets by updating the player's active session.
 */
public final class PlayerInputHandler implements PacketHandler<PacketPlayerInput> {

    private final PlayerRegistry players;

    public PlayerInputHandler(PlayerRegistry players) {
        this.players = players;
    }

    @Override
    public void handle(Connection connection, PacketPlayerInput packet) {
        PlayerSession session = players.byConnection(connection);
        if (session != null && packet != null) {
            session.setInput(packet);
        }
    }
}
