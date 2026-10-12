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
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.BlockChip;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.s2c.PacketBlockBreak;
import io.github.skystrike.shared.net.s2c.PacketBlockDamage;
import io.github.skystrike.shared.net.s2c.PacketDoorState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;

/**
 * The join handshake: validate, allocate a slot, register authoritative player, answer.
 *
 * <p>Every rejection path closes the connection, so a refused client cannot sit on a socket and
 * retry in a loop.
 *
 * <p>Since protocol 16 the handshake also carries the map gate: the client states the map it
 * parsed and this handler refuses a join that states anything else (see {@link #mapRefusal}).
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
        if (damageState != null) {
            PacketJoinReject refusal = mapRefusal(packet.mapHashHex, damageState.authoredMap());
            if (refusal != null) {
                System.out.printf(
                    "[map] join refused connection=%d: client states %s, server plays %s%n",
                    connection.getID(), packet.mapHashHex, refusal.serverMapHashHex);
                reject(connection, refusal);
                return;
            }
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
            // The crack baseline rides the same accept (protocol 17): the current damage of every
            // block that is chipped right now, so a joiner's picture lands on the authority's
            // cracks without replaying the hits that drew them. The totals of the damage state,
            // not its chip history — the two answer different questions, and a joiner needs the
            // picture.
            for (BlockChip chip : damageState.damagedBlocks()) {
                accept.damagedBlocks.add(new PacketBlockDamage(
                    clock.tick(), damageState.authoredIndexOf(chip.block()), chip.damage()));
            }
            // The door baseline rides the same accept: every door, even one standing closed —
            // the joiner then never has to special-case "no record yet means closed".
            for (MapBlock door : damageState.doors().doors()) {
                accept.doors.add(new PacketDoorState(
                    clock.tick(),
                    door.door().id(),
                    damageState.doors().stateOf(door),
                    damageState.doors().sinceTickOf(door)));
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

    /**
     * The join-time map gate (protocol 16): the client states the map it parsed and the server
     * refuses anything but the map it is running.
     *
     * <p>This check has to be a refusal rather than a correction. Everything else about a join can
     * be answered — a name is trimmed, a team is assigned, a loadout is replaced — but the map
     * decides what a wall is, and the destruction baseline that follows names blocks by authored
     * index, an identity that only means anything when both sides parsed the same list. A client
     * on a different map would apply every index to the wrong block, silently, for the whole
     * match. The accept still states the server's hash as well, so the client's own check stays
     * the second line of the same gate rather than a rule this side alone keeps.
     *
     * <p>The comparison is the hash, not the text: {@code ArenaMap} sorts before hashing, so two
     * maps that differ only in the order their lines were authored agree, which is the same
     * tolerance the SDF guard keeps.
     *
     * @param declaredHash the client's stated map hash, in hex; empty when it stated none
     * @param authored the map this server is running
     * @return {@code null} when the join may proceed, otherwise the refusal to send
     */
    static PacketJoinReject mapRefusal(String declaredHash, ArenaMap authored) {
        String expected = authored.mapHashHex();
        if (expected.equals(declaredHash)) {
            return null;
        }
        return new PacketJoinReject(PacketJoinReject.REASON_MAP_MISMATCH, expected);
    }

    private void reject(Connection connection, String reason) {
        reject(connection, new PacketJoinReject(reason));
    }

    private void reject(Connection connection, PacketJoinReject refusal) {
        endpoint.sendReliable(connection, refusal);
        System.out.printf(
            "[net] reject connection=%d reason=%s%s%n",
            connection.getID(),
            refusal.reason,
            refusal.serverMapHashHex == null || refusal.serverMapHashHex.isBlank()
                ? "" : " (server plays " + refusal.serverMapHashHex + ")");
        connection.close();
    }
}
