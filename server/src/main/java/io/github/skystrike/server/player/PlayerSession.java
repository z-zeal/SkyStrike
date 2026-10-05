package io.github.skystrike.server.player;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.physics.PlayerInput;

/**
 * Server-side session binding a network {@link Connection} to its authoritative {@link Player}.
 */
public final class PlayerSession {

    private final Connection connection;
    private final int playerId;
    private final String name;
    private final Player player;

    private volatile PlayerInput latestInput = new PlayerInput();
    private volatile long lastInputTimeMillis;

    public PlayerSession(Connection connection, int playerId, String name, int teamIndex, float spawnX, float spawnY) {
        this.connection = connection;
        this.playerId = playerId;
        this.name = name;
        this.player = new Player(playerId, name, teamIndex, spawnX, spawnY);
        this.lastInputTimeMillis = System.currentTimeMillis();
    }

    public Connection connection() {
        return connection;
    }

    public int playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public Player player() {
        return player;
    }

    public PlayerInput latestInput() {
        return latestInput;
    }

    public void setInput(PacketPlayerInput packet) {
        if (packet != null && packet.sequence >= latestInput.sequence) {
            this.latestInput = PlayerInput.fromPacket(packet);
            this.lastInputTimeMillis = System.currentTimeMillis();
        }
    }

    public long lastInputTimeMillis() {
        return lastInputTimeMillis;
    }
}
