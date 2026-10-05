package io.github.skystrike.server;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.ConnectionRegistry;
import io.github.skystrike.server.net.NetworkEndpoint;
import io.github.skystrike.server.net.NetworkEvent;
import io.github.skystrike.server.net.PacketRouter;
import io.github.skystrike.server.net.handlers.JoinRequestHandler;
import io.github.skystrike.server.net.handlers.LeaveRequestHandler;
import io.github.skystrike.server.net.handlers.PingHandler;
import io.github.skystrike.server.net.handlers.PlayerInputHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.server.sim.TickLoop;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.physics.PlayerMotion;
import java.io.IOException;

/**
 * Composition root for the authoritative host.
 *
 * <p>Wires transport, connection and player registries, packet router and simulation tick loop.
 * Steps authoritative player physics and broadcasts snapshots to clients. Lighting remains a
 * presentation effect and never removes player state from a snapshot.
 */
public final class GameServer {

    private final ServerConfig config;
    private final ArenaMap arena;
    private final NetworkEndpoint endpoint;
    private final ConnectionRegistry connections;
    private final PlayerRegistry players;
    private final SpawnService spawnService;
    private final PacketRouter router;
    private final TickLoop loop;
    private final int ticksPerSnapshot;

    public GameServer(ServerConfig config) {
        this.config = config;
        this.arena = ArenaMap.standard();
        this.endpoint = new NetworkEndpoint(config.tcpPort(), config.udpPort());
        this.connections = new ConnectionRegistry(config.maxPlayers());
        this.players = new PlayerRegistry();
        this.spawnService = new SpawnService(this.arena);
        this.router = new PacketRouter();
        this.loop = new TickLoop(config.tickRateHz(), config.profileIntervalSeconds(), this::tick);
        this.ticksPerSnapshot = NetConfig.ticksPerSnapshot(config.tickRateHz());

        registerHandlers();
    }

    private void registerHandlers() {
        SimulationClock clock = loop.clock();
        router.register(
                PacketJoinRequest.class,
                new JoinRequestHandler(endpoint, connections, players, spawnService, clock, config.tickRateHz()));
        router.register(PacketPing.class, new PingHandler(endpoint, clock));
        router.register(PacketLeaveRequest.class, new LeaveRequestHandler(connections, players));
        router.register(PacketPlayerInput.class, new PlayerInputHandler(players));
    }

    public ServerConfig config() {
        return config;
    }

    public ArenaMap arena() {
        return arena;
    }

    public ConnectionRegistry connections() {
        return connections;
    }

    public PlayerRegistry players() {
        return players;
    }

    public SpawnService spawnService() {
        return spawnService;
    }

    public PacketRouter router() {
        return router;
    }

    public TickLoop loop() {
        return loop;
    }

    /** Binds the transport and runs the tick loop. Blocks until {@link #stop()}. */
    public void run() throws IOException {
        endpoint.start();

        System.out.printf(
                "[server] SkyStrike listening on tcp/%d udp/%d%n", config.tcpPort(), config.udpPort());
        System.out.printf(
                "[server] arena %dx%d, %d solids, %d spawns%n",
                (int) arena.width(), (int) arena.height(), arena.solids().size(), arena.spawns().size());
        System.out.printf(
                "[server] tick %d Hz, snapshot %d Hz, max players %d, protocol %d%n",
                config.tickRateHz(),
                config.tickRateHz() / ticksPerSnapshot,
                config.maxPlayers(),
                NetConfig.PROTOCOL_VERSION);

        try {
            loop.run();
        } finally {
            endpoint.stop();
            connections.clear();
            players.clear();
            System.out.println("[server] stopped");
        }
    }

    /** Asks the loop to finish and the transport to close. Safe from any thread. */
    public void stop() {
        loop.stop();
    }

    /** One authoritative step. Runs on the tick thread only. */
    private void tick(SimulationClock clock) {
        endpoint.drain(this::applyNetworkEvent);

        // Step authoritative player movement
        float dt = clock.dt();
        for (PlayerSession session : players.all()) {
            PlayerMotion.stepInPlace(session.player(), session.latestInput(), dt, arena);
        }

        if (clock.tick() % ticksPerSnapshot == 0) {
            broadcastSnapshot(clock);
        }
    }

    private void applyNetworkEvent(NetworkEvent event) {
        switch (event.type()) {
            case CONNECTED -> System.out.printf(
                    "[net] open  connection=%d%n", event.connection().getID());
            case DISCONNECTED -> {
                players.remove(event.connection());
                ConnectionRegistry.Entry entry = connections.leave(event.connection());
                if (entry == null) {
                    System.out.printf("[net] close connection=%d%n", event.connection().getID());
                } else {
                    System.out.printf(
                            "[net] close player=%d name=%s (%d/%d)%n",
                            entry.playerId(), entry.name(),
                            connections.count(), connections.maxPlayers());
                }
            }
            case RECEIVED -> {
                if (!router.route(event.connection(), event.payload())) {
                    System.out.printf(
                            "[net] drop  connection=%d payload=%s%n",
                            event.connection().getID(),
                            event.payload() == null ? "null" : event.payload().getClass().getName());
                }
            }
            default -> throw new IllegalStateException("unhandled event type: " + event.type());
        }
    }

    /** Broadcasts authoritative game state snapshots containing every active player. */
    private void broadcastSnapshot(SimulationClock clock) {
        if (connections.count() == 0) {
            return;
        }

        long tick = clock.tick();
        long now = System.currentTimeMillis();
        int totalJoined = connections.count();

        for (ConnectionRegistry.Entry entry : connections.entries()) {
            Connection conn = entry.connection();
            if (conn == null || !conn.isConnected()) {
                continue;
            }

            // Flashlight visibility is visual only. Every client receives a stable player list so
            // crossing the cone edge cannot despawn and respawn an enemy between snapshots.
            PacketGameState snapshot = new PacketGameState(tick, now, totalJoined);
            for (PlayerSession s : players.all()) {
                snapshot.players.add(s.player().copy());
            }
            endpoint.sendUnreliable(conn, snapshot);
        }
    }
}
