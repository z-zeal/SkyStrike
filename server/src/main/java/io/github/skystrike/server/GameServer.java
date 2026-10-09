package io.github.skystrike.server;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.server.chat.ChatService;
import io.github.skystrike.server.command.CapabilityBroadcaster;
import io.github.skystrike.server.command.PermissionResolver;
import io.github.skystrike.server.command.ServerCommandModule;
import io.github.skystrike.server.command.ServerCommandService;
import io.github.skystrike.server.net.ConnectionRegistry;
import io.github.skystrike.server.net.NetworkEndpoint;
import io.github.skystrike.server.net.NetworkEvent;
import io.github.skystrike.server.net.PacketRouter;
import io.github.skystrike.server.combat.MeleeSystem;
import io.github.skystrike.server.fx.EffectBroadcaster;
import io.github.skystrike.server.gadget.CameraSystem;
import io.github.skystrike.server.gadget.DroneSystem;
import io.github.skystrike.server.gadget.FuelTankSystem;
import io.github.skystrike.server.gadget.ShieldSystem;
import io.github.skystrike.server.gadget.SurveillanceService;
import io.github.skystrike.server.net.handlers.ChatRequestHandler;
import io.github.skystrike.server.net.handlers.CommandRequestHandler;
import io.github.skystrike.server.net.handlers.JoinRequestHandler;
import io.github.skystrike.server.net.handlers.LeaveRequestHandler;
import io.github.skystrike.server.net.handlers.LoadoutUpdateHandler;
import io.github.skystrike.server.net.handlers.PingHandler;
import io.github.skystrike.server.net.handlers.PlayerInputHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.RespawnService;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.utility.UtilitySystem;
import io.github.skystrike.server.sim.SimulationClock;
import io.github.skystrike.server.sim.TickLoop;
import io.github.skystrike.server.weapons.FireController;
import io.github.skystrike.server.weapons.LoadoutSystem;
import io.github.skystrike.server.weapons.RecoilService;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.debug.DebugState;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.net.c2s.PacketCommandRequest;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketEffectSpawn;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.physics.PlayerMotion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
    private final UtilitySystem utilitySystem;
    /** M7 §8.1: one effect broadcaster behind every combat system's visual events. */
    private final EffectBroadcaster effectBroadcaster;
    private final ShieldSystem shieldSystem;
    private final FuelTankSystem fuelTankSystem;
    /** Phase 6: the drone and camera device systems, and the surveillance view they serve. */
    private final DroneSystem droneSystem;
    private final CameraSystem cameraSystem;
    private final SurveillanceService surveillanceService;
    private final KillFeedService killFeed;
    private final DamageService damageService;
    private final LoadoutSystem loadoutSystem;
    private final ChatService chatService;
    private final PermissionResolver permissions;
    private final CapabilityBroadcaster capabilities;
    private final ServerCommandService commandService;

    /** The debug toolkit's one piece of genuinely global state (build plan M3 §4): timescale. */
    private final DebugState debugState;

    /** Reused per tick so the combat systems do not allocate a player list 60 times a second. */
    private final List<Player> playerStates = new ArrayList<>();

    public GameServer(ServerConfig config) {
        this.config = config;
        this.debugState = new DebugState(debugCommandsEnabled(config));
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
        this.utilitySystem = new UtilitySystem(this.arena);
        // M7 §8.1: detonations, impacts and muzzle events all flow through one broadcaster,
        // culled per recipient when the snapshot broadcast runs.
        this.effectBroadcaster = new EffectBroadcaster();
        this.bulletSystem.setEffectSink(this.effectBroadcaster);
        this.utilitySystem.setEffectSink(this.effectBroadcaster);
        this.shieldSystem = new ShieldSystem();
        this.fuelTankSystem = new FuelTankSystem(this.arena);
        // Phase 6: the gadget devices. The drone and camera systems own their entity lists and
        // answer Q/E presses; the surveillance service owns the view transitions and needs both
        // to know what the player can cycle to. Rounds hit devices, so the bullet system is
        // wired to them, and their destruction visuals flow through the one broadcaster.
        this.droneSystem = new DroneSystem(this.arena);
        this.cameraSystem = new CameraSystem(this.arena);
        this.surveillanceService = new SurveillanceService(this.droneSystem, this.cameraSystem);
        this.droneSystem.setEffectSink(this.effectBroadcaster);
        this.cameraSystem.setEffectSink(this.effectBroadcaster);
        this.bulletSystem.setGadgetSystems(this.droneSystem, this.cameraSystem);
        this.killFeed = new KillFeedService();
        this.damageService = new DamageService(this.killFeed, this.fuelTankSystem);
        // sv_godmode (build plan M3 §4): per-player session state is the single source of truth.
        this.damageService.setGodmodePredicate(id -> {
            PlayerSession session = this.players.byPlayerId(id);
            return session != null && session.godmode();
        });
        RecoilService recoilService = new RecoilService();
        FireController fireController = new FireController(new Random(), recoilService);
        fireController.setEffectSink(this.effectBroadcaster);
        this.loadoutSystem = new LoadoutSystem(
            fireController,
            new MeleeSystem(),
            this.bulletSystem,
            this.utilitySystem,
            this.shieldSystem,
            this.surveillanceService,
            this.droneSystem,
            this.cameraSystem);

        // Chat identity and team scoping come from the authoritative registry, never the packet.
        this.chatService = new ChatService(new RegistryRoster(this.players));
        this.permissions = new PermissionResolver();
        this.capabilities = new CapabilityBroadcaster(this.permissions, this::sendToPlayer);

        // M1 §2.4: --dev resolves every joiner to admin; --grant promotes named players without
        // it. Both are resolver-state, so nothing downstream needs to know which mode the host is in.
        this.permissions.setDevMode(config.devMode());
        config.grants().forEach(this.permissions::grant);
        this.commandService = new ServerCommandService(
            this.players,
            this.permissions,
            new ServerCommandModule.Deps(
                this.players,
                this.respawnService,
                this.loadoutSystem,
                this.chatService,
                this::sendToPlayer,
                this::prepareForLife,
                this.debugState),
            debugCommandsEnabled(config));

        registerHandlers();
    }

    /**
     * Debug metadata is available only when the server itself opted into dev mode or configured
     * an ADMIN identity. The resolver remains the authoritative per-request permission check.
     */
    private static boolean debugCommandsEnabled(ServerConfig config) {
        if (DebugFlags.enabled() || config.devMode()) {
            return true;
        }
        for (Permission level : config.grants().values()) {
            if (level == Permission.ADMIN) {
                return true;
            }
        }
        return false;
    }

    private void registerHandlers() {
        SimulationClock clock = loop.clock();
        router.register(
                PacketJoinRequest.class,
                new JoinRequestHandler(
                    endpoint, connections, players, spawnService, clock, config.tickRateHz(), capabilities));
        router.register(PacketPing.class, new PingHandler(endpoint, clock));
        router.register(PacketLeaveRequest.class, new LeaveRequestHandler(connections, players));
        router.register(PacketPlayerInput.class, new PlayerInputHandler(players));
        router.register(PacketLoadoutUpdate.class, new LoadoutUpdateHandler(players));
        router.register(PacketChatRequest.class, new ChatRequestHandler(players, chatService, this::sendToPlayer));
        router.register(PacketCommandRequest.class,
            new CommandRequestHandler(players, commandService, this::sendToPlayer));
    }

    /**
     * The respawn-time preparation shared by the tick hook and the console commands: requested
     * loadout composition is applied and per-life state is reset. Extracted so {@code give},
     * {@code setteam} and {@code respawn} rebuild a player exactly the way respawning does —
     * one path, one place.
     */
    private void prepareForLife(PlayerSession session) {
        session.applyRequestedLoadout();
        loadoutSystem.resetForRespawn(session);
    }

    /**
     * The chat relay's view of who is connected. A separate type rather than a lambda because
     * {@code ChatService.Roster} has two methods and both must read the same registry — the one
     * the simulation uses, not a copy that can drift from it mid-tick.
     */
    private record RegistryRoster(PlayerRegistry players) implements ChatService.Roster {

        @Override
        public Collection<Integer> playerIds() {
            List<Integer> ids = new ArrayList<>(players.count());
            for (PlayerSession session : players.all()) {
                ids.add(session.playerId());
            }
            return ids;
        }

        @Override
        public int teamIndexOf(int playerId) {
            PlayerSession session = players.byPlayerId(playerId);
            return session == null ? ChatService.NOT_JOINED : session.player().teamIndex;
        }
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

    public UtilitySystem utilitySystem() {
        return utilitySystem;
    }

    public EffectBroadcaster effectBroadcaster() {
        return effectBroadcaster;
    }

    public ShieldSystem shieldSystem() {
        return shieldSystem;
    }

    public FuelTankSystem fuelTankSystem() {
        return fuelTankSystem;
    }

    public DroneSystem droneSystem() {
        return droneSystem;
    }

    public CameraSystem cameraSystem() {
        return cameraSystem;
    }

    public SurveillanceService surveillanceService() {
        return surveillanceService;
    }

    public DamageService damageService() {
        return damageService;
    }

    public KillFeedService killFeed() {
        return killFeed;
    }

    public ChatService chatService() {
        return chatService;
    }

    public PermissionResolver permissions() {
        return permissions;
    }

    public CapabilityBroadcaster capabilities() {
        return capabilities;
    }

    public ServerCommandService commandService() {
        return commandService;
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
        if (config.devMode()) {
            // Loud on purpose: a dev host must never be mistaken for a real one.
            System.out.println("[server] DEV MODE — every joined player resolves to ADMIN");
        }
        if (!config.grants().isEmpty()) {
            System.out.printf("[server] permission grants: %s%n", config.grants());
        }

        try {
            loop.run();
        } finally {
            endpoint.stop();
            connections.clear();
            players.clear();
            bulletSystem.clear();
            utilitySystem.clear();
            droneSystem.clear();
            cameraSystem.clear();
            effectBroadcaster.clear();
            chatService.clear();
            capabilities.clear();
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

        // timescale (build plan M3 §4): scales simulation time only, never the wall-clock tick
        // rate the loop itself runs at.
        float dt = clock.dt() * debugState.timescale();
        collectPlayerStates();

        // 1. Dead players count back in, getting their requested loadout composition and a
        // fresh weapon state on the way.
        respawnService.update(dt, playerStates, player -> {
            PlayerSession session = players.byPlayerId(player.id);
            if (session != null) {
                prepareForLife(session);
            }
        });

        // 2. Movement, then weapons, then the gadget devices: a round leaves the muzzle this
        //    tick's position gives it, and a piloted drone steers with this tick's input. The
        //    surveillance lock is enforced inside the shared motion and in the loadout tick, so
        //    a piloting player's body stays frozen however their input arrives.
        for (PlayerSession session : players.all()) {
            Player player = session.player();
            PlayerInput input = session.latestInput();
            PlayerMotion.stepInPlace(player, player.alive ? input : null, dt, arena, session.noclip());
            if (!player.alive && input != null) {
                // Dead players are still acknowledged, so the client's prediction queue drains
                // instead of banking three seconds of input for the respawn.
                player.lastProcessedInputSequence = input.sequence;
            }
            loadoutSystem.tick(session, dt, playerStates, damageService);
            droneSystem.stepOwned(player, input, dt);
            cameraSystem.stepOwned(player, input, dt);
        }

        // 3. Rounds and utilities already in the world, including anything thrown this tick.
        //    Rounds also resolve against gadget devices here — a drone or camera can be shot down.
        bulletSystem.step(dt, playerStates, damageService);
        utilitySystem.step(dt, playerStates, damageService);

        // 4. The gadget device sweep: spent devices are destroyed, and a device whose owner died
        //    or left is removed with them, returning any pilot to their own eyes.
        droneSystem.sweep(playerStates);
        cameraSystem.sweep(playerStates);

        // A hit can kill a player after their input was already latched this tick. Drop every
        // remaining edge now, not only at the eventual respawn, so death cannot bank Q/E or fire.
        for (PlayerSession session : players.all()) {
            if (!session.player().alive) {
                session.clearTrigger();
            }
        }

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
                PlayerSession leaving = players.remove(event.connection());
                if (leaving != null) {
                    // Session-scoped state goes with the session: a reused player id must not
                    // inherit the previous holder's token bucket or runtime promotion.
                    chatService.forget(leaving.playerId());
                    capabilities.forget(leaving.playerId());
                    commandService.forget(leaving.playerId());
                }
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
     * Broadcasts authoritative game state snapshots: every active player, every round in the
     * air, and every gadget device in the world.
     *
     * <p>None of these lists is culled by the flashlight cone. Visibility is a presentation
     * effect — culling it server-side made enemies despawn and respawn as they crossed the cone
     * edge, and a tracer flickering in and out mid-flight would be the same bug with a shorter
     * lifetime. Devices ride the same rule: a drone in the dark is still a drone.
     */
    private void broadcastSnapshot(SimulationClock clock) {
        // M7 §8.1: drain the effect window even with nobody connected, so the accumulator can
        // never grow stale between matches.
        List<EffectSpawn> effectSpawns = effectBroadcaster.drain();
        if (connections.count() == 0) {
            return;
        }

        long tick = clock.tick();
        long now = System.currentTimeMillis();
        int totalJoined = connections.count();
        List<Projectile> liveRounds = bulletSystem.active();
        List<ThrownUtility> liveUtilities = utilitySystem.active();
        List<UtilityZone> liveUtilityZones = utilitySystem.zones();
        List<DroneEntity> liveDrones = droneSystem.active();
        List<CameraEntity> liveCameras = cameraSystem.active();
        boolean cheatsActive = players.anyCheatActive();

        for (ConnectionRegistry.Entry entry : connections.entries()) {
            Connection conn = entry.connection();
            if (conn == null || !conn.isConnected()) {
                continue;
            }

            PacketGameState snapshot = new PacketGameState(tick, now, totalJoined);
            snapshot.cheatsActive = cheatsActive;
            for (PlayerSession s : players.all()) {
                snapshot.players.add(s.player().copy());
            }
            for (Projectile projectile : liveRounds) {
                snapshot.projectiles.add(projectile.copy());
            }
            for (ThrownUtility utility : liveUtilities) {
                snapshot.thrownUtilities.add(utility.copy());
            }
            for (UtilityZone zone : liveUtilityZones) {
                snapshot.utilityZones.add(zone.copy());
            }
            for (DroneEntity drone : liveDrones) {
                snapshot.drones.add(drone.copy());
            }
            for (CameraEntity camera : liveCameras) {
                snapshot.cameras.add(camera.copy());
            }
            endpoint.sendUnreliable(conn, snapshot);

            if (!effectSpawns.isEmpty()) {
                sendEffectSpawns(entry, effectSpawns, tick);
            }
        }
    }

    /**
     * Sends one recipient their culled share of the tick's effect batch (M7 §8.1). Culling uses
     * the same {@code VisionMath} the rest of the server judges visibility with, so a detonation
     * the recipient cannot see is never described to their client — and the client's own
     * per-particle occlusion test is the second line of defence. The recipient's own live devices
     * count as extra observers: an effect seen only through their drone must still arrive.
     */
    private void sendEffectSpawns(ConnectionRegistry.Entry entry, List<EffectSpawn> effectSpawns, long tick) {
        PlayerSession session = players.byPlayerId(entry.playerId());
        if (session == null) {
            return;
        }
        EffectBroadcaster.Recipient recipient = new EffectBroadcaster.Recipient(
            session.player(),
            droneSystem.ownedBy(entry.playerId()),
            cameraSystem.ownedBy(entry.playerId()));
        List<EffectSpawn> visible = effectBroadcaster.cullFor(
            effectSpawns, recipient, arena, utilitySystem.smokeVolumes());
        if (!visible.isEmpty()) {
            endpoint.sendUnreliable(entry.connection(), new PacketEffectSpawn(tick, visible));
        }
    }
}
