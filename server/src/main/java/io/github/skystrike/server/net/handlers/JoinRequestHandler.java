package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.command.CapabilityBroadcaster;
import io.github.skystrike.server.net.ConnectionRegistry;
import io.github.skystrike.server.net.NetworkEndpoint;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.s2c.PacketBlockBreak;
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
    private final CapabilityBroadcaster capabilities;
    private final MapDamageState damageState;

    public JoinRequestHandler(
        NetworkEndpoint endpoint,
        ConnectionRegistry connections,
        PlayerRegistry players,
        SpawnService spawnService,
        SimulationClock clock,
        int tickRateHz,
        CapabilityBroadcaster capabilities,
        MapDamageState damageState) {
        this.endpoint = endpoint;
        this.connections = connections;
        this.players = players;
        this.spawnService = spawnService;
        this.clock = clock;
        this.tickRateHz = tickRateHz;
        this.capabilities = capabilities;
        this.damageState = damageState;
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

        // The destruction baseline: the map the records are indexed against, the revision, and
        // the round's break history as the same records the live path carries. Reliable and
        // ordered before every later break, so the joiner applies the baseline first and the
        // live events after it without ever replaying history.
        PacketJoinAccept accept =
            new PacketJoinAccept(entry.playerId(), entry.name(), tickRateHz, clock.tick());
        if (damageState != null) {
            accept.mapHashHex = damageState.authoredMap().mapHashHex();
            accept.mapRevision = damageState.revision();
            int revision = 0;
            for (BlockBreak broke : damageState.breaks()) {
                revision++;
                accept.destroyedBlocks.add(new PacketBlockBreak(
                    clock.tick(),
                    damageState.authoredIndexOf(broke.block()),
                    broke.cause(),
                    broke.x(),
                    broke.y(),
                    broke.shattered(),
                    revision));
            }
        }
        endpoint.sendReliable(connection, accept);

        // The capability follows the accept immediately: until it arrives the client has no
        // console, which is the correct default, but a moderator would otherwise have to wait
        // for an unrelated change to be told they have one.
        if (capabilities != null) {
            capabilities.pushOnJoin(entry.playerId(), entry.name());
        }

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
