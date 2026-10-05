package io.github.skystrike.server;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
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
import io.github.skystrike.server.player.RespawnService;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.server.sim.TickLoop;
import io.github.skystrike.server.weapons.FireController;
import io.github.skystrike.server.weapons.GunInstance;
import io.github.skystrike.server.weapons.RecoilService;
import io.github.skystrike.shared.combat.SpreadMath;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.physics.PlayerMotion;
import io.github.skystrike.shared.weapons.WeaponId;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Composition root for the authoritative host.
 *
 * <p>Wires transport, connection and player registries, packet router and simulation tick loop.
 * Steps authoritative player physics and combat, then broadcasts snapshots to clients. Lighting
 * remains a presentation effect and never removes player state from a snapshot.
 *
 * <p>The tick runs in a fixed order and the order is load-bearing: respawns before movement so a
 * returning player moves on the tick they come back; movement before firing so a round leaves
 * the muzzle the player actually occupies; firing before the bullet step so a round fired this
 * tick travels this tick; and the snapshot last, after everything the clients are told about has
 * already happened.
 */
public final class GameServer {

    private final ServerConfig config;
    private final ArenaMap arena;
    private final NetworkEndpoint endpoint;
    private final ConnectionRegistry connections;
    private final PlayerRegistry players;
    private final SpawnService spawnService;
    private final RespawnService respawnService;
    private final PacketRouter router;
    private final TickLoop loop;
    private final int ticksPerSnapshot;

    private final BulletSystem bulletSystem;
    private final KillFeedService killFeed;
    private final DamageService damageService;
    private final RecoilService recoilService;
    private final FireController fireController;
    private final FireController.Volley volley = new FireController.Volley();

    /** Reused per tick so the combat systems do not allocate a player list 60 times a second. */
    private final List<Player> playerStates = new ArrayList<>();

    public GameServer(ServerConfig config) {
        this.config = config;
        this.arena = ArenaMap.standard();
        this.endpoint = new NetworkEndpoint(config.tcpPort(), config.udpPort());
        this.connections = new ConnectionRegistry(config.maxPlayers());
        this.players = new PlayerRegistry();
        this.spawnService = new SpawnService(this.arena);
        this.respawnService = new RespawnService(this.spawnService);
        this.router = new PacketRouter();
        this.loop = new TickLoop(config.tickRateHz(), config.profileIntervalSeconds(), this::tick);
        this.ticksPerSnapshot = NetConfig.ticksPerSnapshot(config.tickRateHz());

        this.bulletSystem = new BulletSystem(this.arena);
        this.killFeed = new KillFeedService();
        this.damageService = new DamageService(this.killFeed);
        this.recoilService = new RecoilService();
        this.fireController = new FireController(new Random(), this.recoilService);

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

    public BulletSystem bulletSystem() {
        return bulletSystem;
    }

    public DamageService damageService() {
        return damageService;
    }

    public KillFeedService killFeed() {
        return killFeed;
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
            bulletSystem.clear();
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

        float dt = clock.dt();
        collectPlayerStates();

        // 1. Dead players count back in.
        respawnService.update(dt, playerStates);

        // 2. Movement, then weapons: a round leaves the muzzle this tick's position gives it.
        for (PlayerSession session : players.all()) {
            Player player = session.player();
            PlayerInput input = session.latestInput();
            PlayerMotion.stepInPlace(player, player.alive ? input : null, dt, arena);
            if (!player.alive && input != null) {
                // Dead players are still acknowledged, so the client's prediction queue drains
                // instead of banking three seconds of input for the respawn.
                player.lastProcessedInputSequence = input.sequence;
            }
            stepWeapon(session, dt);
        }

        // 3. Rounds already in the air, including the ones fired a moment ago.
        bulletSystem.step(dt, playerStates, damageService);

        // 4. Tell the clients what happened to them.
        dispatchCombatEvents();

        if (clock.tick() % ticksPerSnapshot == 0) {
            broadcastSnapshot(clock);
        }
    }

    private void collectPlayerStates() {
        playerStates.clear();
        for (PlayerSession session : players.all()) {
            playerStates.add(session.player());
        }
    }

    /**
     * Weapon selection, spread and recoil decay, and the trigger, for one player.
     *
     * <p>The trigger edge is consumed whether or not the player is alive, so holding the mouse
     * through your own death does not bank a shot for the respawn.
     */
    private void stepWeapon(PlayerSession session, float dt) {
        Player player = session.player();
        GunInstance gun = session.gun();
        PlayerInput input = session.latestInput();

        if (input != null && WeaponId.isValidOrdinal(input.weaponSelect)) {
            gun.switchTo(WeaponId.fromOrdinal(input.weaponSelect));
        }

        gun.update(dt, SpreadMath.isMoving(player.vx), player.ads);

        boolean firePressed = session.consumeFirePressed();
        if (player.alive) {
            int rounds = fireController.fire(player, gun, session.triggerHeld(), firePressed, volley);
            for (int i = 0; i < rounds; i++) {
                bulletSystem.spawn(player, gun.weaponId(), volley.angle(i));
            }
        }

        // Mirror the authoritative gun state onto the networked player record.
        player.weaponId = gun.weaponId().ordinal();
        player.spread = gun.currentSpread();
        player.gunKick = gun.visualKick();
    }

    /** Sends damage to the two clients it concerns and kills to everyone. */
    private void dispatchCombatEvents() {
        for (DamageService.DamageResult result : damageService.drain()) {
            PacketDamageEvent packet = new PacketDamageEvent(
                result.attackerId(),
                result.targetId(),
                result.amount(),
                result.remainingHealth(),
                result.zone(),
                result.weaponId(),
                result.x(),
                result.y(),
                result.distanceTravelled(),
                result.killed());
            sendToPlayer(result.attackerId(), packet);
            if (result.targetId() != result.attackerId()) {
                sendToPlayer(result.targetId(), packet);
            }
        }

        for (KillFeedService.KillEvent event : killFeed.drain()) {
            PacketKillEvent packet = new PacketKillEvent(
                event.killerId(),
                event.killerName(),
                event.victimId(),
                event.victimName(),
                event.weaponId(),
                event.headshot(),
                event.selfInflicted(),
                event.friendlyFire());
            endpoint.broadcastReliable(packet);
            System.out.printf("[kill] %s%n", packet.feedLine());
        }
    }

    private void sendToPlayer(int playerId, Packet packet) {
        PlayerSession session = players.byPlayerId(playerId);
        if (session == null) {
            return;
        }
        Connection connection = session.connection();
        if (connection != null && connection.isConnected()) {
            endpoint.sendReliable(connection, packet);
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

    /**
     * Broadcasts authoritative game state snapshots: every active player, and every round
     * currently in the air.
     *
     * <p>Neither list is culled by the flashlight cone. Visibility is a presentation effect —
     * culling it server-side made enemies despawn and respawn as they crossed the cone edge, and
     * a tracer flickering in and out mid-flight would be the same bug with a shorter lifetime.
     */
    private void broadcastSnapshot(SimulationClock clock) {
        if (connections.count() == 0) {
            return;
        }

        long tick = clock.tick();
        long now = System.currentTimeMillis();
        int totalJoined = connections.count();
        List<Projectile> liveRounds = bulletSystem.active();

        for (ConnectionRegistry.Entry entry : connections.entries()) {
            Connection conn = entry.connection();
            if (conn == null || !conn.isConnected()) {
                continue;
            }

            PacketGameState snapshot = new PacketGameState(tick, now, totalJoined);
            for (PlayerSession s : players.all()) {
                snapshot.players.add(s.player().copy());
            }
            for (Projectile projectile : liveRounds) {
                snapshot.projectiles.add(projectile.copy());
            }
            endpoint.sendUnreliable(conn, snapshot);
        }
    }
}
