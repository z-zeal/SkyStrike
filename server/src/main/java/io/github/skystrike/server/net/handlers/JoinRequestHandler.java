package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.ConnectionRegistry;
import io.github.skystrike.server.net.NetworkEndpoint;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;

/**
 * The join handshake: validate, allocate a slot, register authoritative player, answer.
 *
 * <p>Every rejection path closes the connection, so a refused client cannot sit on a socket and
 * retry in a loop.
 */
public final class JoinRequestHandler implements PacketHandler<PacketJoinRequest> {

    private final NetworkEndpoint endpoint;
    private final ConnectionRegistry connections;
    private final PlayerRegistry players;
    private final SpawnService spawnService;
    private final SimulationClock clock;
    private final int tickRateHz;

    public JoinRequestHandler(
        NetworkEndpoint endpoint,
        ConnectionRegistry connections,
        PlayerRegistry players,
        SpawnService spawnService,
        SimulationClock clock,
        int tickRateHz) {
        this.endpoint = endpoint;
        this.connections = connections;
        this.players = players;
        this.spawnService = spawnService;
        this.clock = clock;
        this.tickRateHz = tickRateHz;
    }

    @Override
    public void handle(Connection connection, PacketJoinRequest packet) {
        if (packet.protocolVersion != NetConfig.PROTOCOL_VERSION) {
            reject(connection, PacketJoinReject.REASON_PROTOCOL_MISMATCH);
            return;
        }
        if (connections.hasJoined(connection)) {
            reject(connection, PacketJoinReject.REASON_ALREADY_JOINED);
            return;
        }
        if (!NetConfig.isValidPlayerName(packet.playerName)) {
            reject(connection, PacketJoinReject.REASON_INVALID_NAME);
            return;
        }

        String name = packet.playerName.trim();
        ConnectionRegistry.Entry entry = connections.join(connection, name);
        if (entry == null) {
            reject(connection, PacketJoinReject.REASON_SERVER_FULL);
            return;
        }

        if (players != null && spawnService != null) {
            int teamIndex = spawnService.selectBalancedTeam(players.teamCount(0), players.teamCount(1));
            PlayerSession session = players.register(connection, entry.playerId(), entry.name(), teamIndex, 0f, 0f);
            spawnService.spawn(session.player());
        }

        endpoint.sendReliable(
            connection,
            new PacketJoinAccept(entry.playerId(), entry.name(), tickRateHz, clock.tick()));

        System.out.printf(
            "[net] join  player=%d name=%s connection=%d (%d/%d)%n",
            entry.playerId(), entry.name(), entry.connectionId(),
            connections.count(), connections.maxPlayers());
    }

    private void reject(Connection connection, String reason) {
        endpoint.sendReliable(connection, new PacketJoinReject(reason));
        System.out.printf("[net] reject connection=%d reason=%s%n", connection.getID(), reason);
        connection.close();
    }
}
