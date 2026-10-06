package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import io.github.skystrike.chat.ChatClient;
import io.github.skystrike.chat.ChatMuteList;
import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandModule;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.command.DebugKeyController;
import io.github.skystrike.shared.command.Cvar;
import io.github.skystrike.fx.FxPipeline;
import io.github.skystrike.fx.lighting.VisibilitySystem.ObserverState;
import io.github.skystrike.gameplay.LoadoutController;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.InputSampler;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.net.Interpolator;
import io.github.skystrike.net.LocalPrediction;
import io.github.skystrike.net.StateBuffer;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.HitboxOverlay;
import io.github.skystrike.render.PlayerRenderer;
import io.github.skystrike.render.ProjectileRenderer;
import io.github.skystrike.render.ThrownUtilityRenderer;
import io.github.skystrike.render.TrajectoryRenderer;
import io.github.skystrike.render.StatusOverlay;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.debug.DebugState;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.settings.ClientPreferences;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.ui.console.ConsoleDialog;
import io.github.skystrike.ui.text.ContrastTestOverlay;
import io.github.skystrike.ui.text.MessageBuffer;
import io.github.skystrike.ui.text.MessageSeverity;
import io.github.skystrike.world.TerrainRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * Composition root for a match.
 *
 * <p>Routes input, drives authoritative prediction/interpolation, and coordinates multi-pass
 * rendering across terrain, entities, the occluded visibility pass and the fog composite.
 */
public final class GameScreen implements Screen {

    private static final float PAN_SPEED_UNITS_PER_SECOND = 900f;
    private static final float ZOOM_RATE_PER_SECOND = 1.6f;

    private final ArenaMap arena = ArenaMap.standard();
    private final GameCamera camera = new GameCamera(arena.width(), arena.height());
    private final TerrainRenderer terrain = new TerrainRenderer(arena);
    private final PlayerRenderer playerRenderer = new PlayerRenderer();
    private final ProjectileRenderer projectileRenderer = new ProjectileRenderer();
    private final ThrownUtilityRenderer thrownUtilityRenderer = new ThrownUtilityRenderer();
    private final TrajectoryRenderer trajectoryRenderer = new TrajectoryRenderer();
    private final StatusOverlay overlay = new StatusOverlay();
    private final HitboxOverlay hitboxOverlay = new HitboxOverlay();
    private final ContrastTestOverlay contrastTestOverlay = new ContrastTestOverlay();
    private final ClientSession session;

    /**
     * The debug toolkit's local mirror (build plan M3 §4): every F-key/cvar pair writes here
     * through {@link ClientCommandModule}'s {@code onChange} hooks, and rendering reads it back —
     * never the other way around.
     */
    private final DebugState debugState = new DebugState(DebugFlags.enabled());

    /**
     * The chat/console state (console plan §2): the scrollback ring and the locally persisted
     * chat target on the client's side, the capability level on the server's. The dialog that
     * renders them is created in the constructor once the command service exists.
     */
    private final MessageBuffer messages = new MessageBuffer();
    private final ChatMuteList muteList = new ChatMuteList();
    private final ClientCapabilities capabilities = new ClientCapabilities();
    private final ChatClient chatClient = new ChatClient(messages, muteList, capabilities);
    private final ClientPreferences preferences = new ClientPreferences();
    private final ClientCommandService commandService;
    private final ConsoleDialog consoleDialog;

    /** Frame id of the last console close, so the closing keypress cannot re-open it (§6.4). */
    private long consoleClosedFrameId = -1L;
    /** Wall-clock duration of the previous frame, for the {@code fps} command's detail. */
    private float lastDeltaMillis;

    private final KeyBindings bindings = new KeyBindings();
    private final InputRouter inputRouter = new InputRouter();
    private final LoadoutController loadoutController = new LoadoutController(bindings, inputRouter);
    private final InputSampler inputSampler = new InputSampler(bindings, inputRouter, loadoutController);
    private DebugKeyController debugKeyController;

    /** The wheel reaches the loadout controller as slot cycles; everything else goes via polling. */
    private final InputAdapter scrollForwarder = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            loadoutController.scrolled(amountY);
            return false;
        }
    };
    private final LocalPrediction prediction = new LocalPrediction();
    private final StateBuffer stateBuffer = new StateBuffer();
    private final Interpolator interpolator = new Interpolator(stateBuffer);

    private FxPipeline pipeline;
    private final List<ObserverState> observers = new ArrayList<>();

    private final String host;
    private final int tcpPort;
    private final int udpPort;

    private float adsAlpha;

    public GameScreen(String playerName, String host, int tcpPort, int udpPort) {
        this.session = new ClientSession(playerName);
        this.host = host;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;

        this.session.setSnapshotListener(this::onGameStateSnapshot);
        this.session.setKillListener(this::onKillEvent);
        this.session.setChatListener(this::onChatMessage);
        this.session.setCapabilityListener(this::onCapabilities);
        this.session.setSessionResetListener(capabilities::reset);
        this.chatClient.setSender(session::sendReliable);
        this.loadoutController.setPacketSender(session::sendReliable);

        // The M1 client command core: local commands against local state, everything else
        // forwarded as a raw line and re-authorised server-side. The dialog renders on top.
        this.commandService = new ClientCommandService(
            capabilities,
            chatClient,
            session::playerId,
            session::acceptedName,
            this::connectedPlayerNames);
        this.commandService.registerDefaults(new ClientCommandModule.Deps(
            messages,
            bindings,
            muteList,
            session::playerId,
            this::resolveNameToId,
            Gdx.graphics::getFramesPerSecond,
            () -> lastDeltaMillis,
            this::disconnectFromConsole,
            debugState));
        this.commandService.setServerSender(session::sendCommand);
        this.session.setCommandResponseListener(commandService::handleResponse);
        this.debugKeyController = new DebugKeyController(bindings, commandService);

        this.consoleDialog = new ConsoleDialog(
            commandService,
            capabilities,
            chatClient,
            messages,
            inputRouter,
            preferences,
            session::playerId,
            session::acceptedName,
            this::connectedPlayerNames);
        this.consoleDialog.setCloseListener(
            () -> consoleClosedFrameId = Gdx.graphics.getFrameId());
    }

    /** The {@code disconnect} client command: sever locally, note it locally. */
    private void disconnectFromConsole() {
        session.disconnect();
        chatClient.addSystemLine(
            System.currentTimeMillis(),
            ChatChannel.SYSTEM,
            "Disconnected.",
            MessageSeverity.INFO);
    }

    /** Names of everyone in the latest snapshot, for {@code PLAYER} argument completion. */
    private List<String> connectedPlayerNames() {
        PacketGameState snapshot = session.latestSnapshot();
        if (snapshot == null || snapshot.players == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (Player p : snapshot.players) {
            if (p.name != null && !p.name.isBlank()) {
                names.add(p.name);
            }
        }
        return names;
    }

    /**
     * Resolves a display name to a player id using the latest snapshot: exact (case-insensitive)
     * match first, then a unique prefix. -1 for no match, -2 for an ambiguous prefix — the
     * {@code mute} command reports both as "no player matches".
     */
    private int resolveNameToId(String name) {
        PacketGameState snapshot = session.latestSnapshot();
        if (snapshot == null || snapshot.players == null || name == null) {
            return -1;
        }
        String needle = name.trim().toLowerCase(java.util.Locale.ROOT);
        int prefixMatch = -1;
        for (Player p : snapshot.players) {
            String candidate = p.name == null ? "" : p.name.toLowerCase(java.util.Locale.ROOT);
            if (candidate.equals(needle)) {
                return p.id;
            }
            if (candidate.startsWith(needle)) {
                if (prefixMatch != -1 && prefixMatch != p.id) {
                    prefixMatch = -2;
                } else if (prefixMatch == -1) {
                    prefixMatch = p.id;
                }
            }
        }
        return prefixMatch;
    }

    private void onChatMessage(io.github.skystrike.shared.text.ChatMessage message) {
        chatClient.setLocalPlayerId(session.playerId());
        chatClient.receive(message);
    }

    /**
     * Reflects a capability push. The console appearing or disappearing is worth one system
     * line; being told the same thing again is not, which is why the capability reports whether
     * it actually changed.
     */
    private void onCapabilities(PacketCapabilities packet) {
        if (!capabilities.apply(packet)) {
            return;
        }
        chatClient.addSystemLine(
            System.currentTimeMillis(),
            ChatChannel.SYSTEM,
            capabilities.consoleAccess()
                ? "Console access granted (" + capabilities.level() + ")."
                : "Console access is not available on this server.",
            MessageSeverity.INFO);
    }

    private void onGameStateSnapshot(PacketGameState snapshot) {
        stateBuffer.addSnapshot(snapshot);

        int localId = session.playerId();
        if (localId >= 0 && snapshot.players != null) {
            for (Player p : snapshot.players) {
                if (p.id == localId) {
                    prediction.reconcile(p, arena);
                    loadoutController.onAuthoritativePlayer(p, prediction.predicted());
                    break;
                }
            }
        }
    }

    private void onKillEvent(PacketKillEvent kill) {
        Gdx.app.log("SkyStrike", kill.feedLine());
    }

    @Override
    public void show() {
        camera.centreOn(arena.mirrorAxisX(), camera.viewportHeight() / 2f);
        InputMultiplexer root = new InputMultiplexer();
        root.addProcessor(inputRouter.multiplexer());
        root.addProcessor(scrollForwarder);
        Gdx.input.setInputProcessor(root);
        pipeline = new FxPipeline(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        session.connect(host, tcpPort, udpPort);
    }

    @Override
    public void render(float delta) {
        session.update(delta);
        lastDeltaMillis = delta * 1000f;
        consoleDialog.update(delta);

        // Console plan §6.4: the open key is one press, one owner. While the dialog is closed
        // and gameplay owns input, it opens the dialog; the same keystroke is consumed by the
        // focus machinery before it can type, and the close frame's key state cannot re-open.
        if (consoleDialog.isGameplayActive()
            && bindings.isOpenChatJustPressed()
            && Gdx.graphics.getFrameId() != consoleClosedFrameId) {
            consoleDialog.open();
        }

        // Build plan M3 §4: every F-key submits the same command line typing it would, gated
        // identically to the console itself — one owner of the keyboard at a time.
        if (consoleDialog.isGameplayActive()) {
            debugKeyController.update();
        }

        Player localPlayer = prediction.predicted();
        float visionReach = VisionConfig.REACH_HIP;

        // cl_freecam (F2): input packets keep sending zeroed intent even while detached.
        inputSampler.setFreecamActive(debugState.freecam());

        if (localPlayer != null) {
            // 1. Loadout input first: a slot press this frame rides this frame's input packet
            loadoutController.update(localPlayer);

            // 2. Sample input and simulate predicted local motion
            PacketPlayerInput input = inputSampler.sample(localPlayer, camera);
            localPlayer = prediction.predict(input, delta, arena);
            session.sendUnreliable(input);

            if (debugState.freecam() && consoleDialog.isGameplayActive()) {
                // The camera detaches entirely: pan/zoom by hand instead of following the player.
                sampleCameraInput(delta);
            } else {
                // 2. Camera follow and smoothly interpolated ADS pan / vision reach
                adsAlpha =
                    Lerp.smooth(adsAlpha, localPlayer.ads ? 1f : 0f, VisionConfig.ADS_TRANSITION_RATE, delta);
                visionReach = Lerp.mix(VisionConfig.REACH_HIP, VisionConfig.REACH_ADS, adsAlpha);

                float panDist = PlayerConfig.ADS_CAMERA_PAN * adsAlpha;
                float aimRad = Angles.toRadians(localPlayer.aimAngle);
                float targetCamX = localPlayer.centerX() + panDist * (float) Math.cos(aimRad);
                float targetCamY = localPlayer.centerY() + panDist * (float) Math.sin(aimRad);
                camera.centreOn(targetCamX, targetCamY);
            }
        } else if (consoleDialog.isGameplayActive()) {
            // Fallback manual camera pan while waiting for join/spawn
            sampleCameraInput(delta);
        }

        // 3. Interpolate remote player and projectile states
        List<Player> remotePlayers = interpolator.interpolateRemotePlayers(session.playerId());
        List<Projectile> projectiles = interpolator.interpolateProjectiles();
        List<ThrownUtility> thrownUtilities = interpolator.interpolateThrownUtilities();
        List<UtilityZone> utilityZones = interpolator.latestUtilityZones();

        // The snapshot zones are the sole source for the shader's smoke circles. This mirrors
        // the server's UtilitySystem smokeVolumes list rather than inventing a client-only cloud.
        pipeline.smokeVolumes().clear();
        for (UtilityZone zone : utilityZones) {
            if (zone.blocksVision()) {
                pipeline.smokeVolumes().add(zone.smokeVolume());
            }
        }

        // 4. Multi-pass rendering pipeline (effects §5)
        // Pass 1: SCENE (Terrain + Entities into scene buffer)
        pipeline.beginScene();
        terrain.render(camera);
        trajectoryRenderer.render(camera, localPlayer, arena);
        playerRenderer.render(camera, remotePlayers, localPlayer);
        projectileRenderer.render(camera, projectiles);
        thrownUtilityRenderer.render(camera, thrownUtilities);
        pipeline.endScene();

        // Pass 2: VISIBILITY (Observers + SDF Soft Shadows into half-res visibility buffer)
        observers.clear();
        if (localPlayer != null) {
            observers.add(ObserverState.standardPlayer(
                    localPlayer.eyeX(), localPlayer.eyeY(), localPlayer.aimAngle, visionReach));
        }
        pipeline.renderVisibility(camera, observers, !isShadowsOn());

        // Pass 3: COMPOSITE (scene * max(visibility, ambientFloor) + light onto backbuffer)
        pipeline.composite();

        // Pass 4: DEBUG OVERLAYS
        if (debugState.sdfView()) {
            pipeline.renderSdfDebug(camera);
        }
        if (debugState.hitboxes()) {
            hitboxOverlay.render(camera, remotePlayers, localPlayer);
        }

        // Pass 5: HUD & OVERLAY (drawn unoccluded over composite)
        if (debugState.overlay()) {
            overlay.render(statusLines(localPlayer, visionReach, projectiles.size()));
        }

        // Pass 6: the chat/console dialog, above everything else (passive view when closed).
        consoleDialog.render(System.currentTimeMillis());

        // ui_contrast_test (F12): a full-screen developer test card, so it wins over everything
        // including the console — exactly the "readable on all four means readable in the game"
        // validation the console plan asks for.
        if (debugState.contrastTest()) {
            contrastTestOverlay.render();
        }
    }

    /** {@code r_shadows}: soft by default; unrouted through {@link DebugState} since it ships on. */
    private boolean isShadowsOn() {
        Cvar cvar = commandService.cvars().find("r_shadows");
        return cvar == null || Boolean.parseBoolean(cvar.value());
    }

    private void sampleCameraInput(float delta) {
        float dx = 0f;
        float dy = 0f;
        if (bindings.isMoveLeftPressed()) {
            dx -= PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (bindings.isMoveRightPressed()) {
            dx += PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (bindings.isCrouchPressed()) {
            dy -= PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (bindings.isJumpPressed()) {
            dy += PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (dx != 0f || dy != 0f) {
            camera.pan(dx, dy);
        }

        if (Gdx.input.isKeyPressed(Input.Keys.MINUS)) {
            camera.zoomBy(1f + ZOOM_RATE_PER_SECOND * delta);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.EQUALS)) {
            camera.zoomBy(1f / (1f + ZOOM_RATE_PER_SECOND * delta));
        }
    }

    private List<String> statusLines(Player localPlayer, float visionReach, int projectileCount) {
        List<String> lines = new ArrayList<>();
        lines.add("SkyStrike — Phase 5 (Throwables and Zones)");
        lines.add("server: " + session.statusLine() + cheatsTagOrEmpty());
        if (debugState.playerLight() || debugState.playerLightShadows() || debugState.fxDebug()) {
            // M6/M7 aren't built yet; the toggles are wired ahead of the pipeline (build plan M3
            // §4) so this is the only place they have anything to show right now.
            lines.add(String.format(
                "debug: r_player_light=%s  r_player_light_shadows=%s  fx_debug=%s",
                debugState.playerLight(), debugState.playerLightShadows(), debugState.fxDebug()));
        }
        if (localPlayer != null) {
            lines.add(String.format(
                    "player: pos (%.0f, %.0f)  vel (%.0f, %.0f)  fuel %.0f  rot %.1f°  aim %.1f°  %s %s",
                    localPlayer.x,
                    localPlayer.y,
                    localPlayer.vx,
                    localPlayer.vy,
                    localPlayer.fuel,
                    localPlayer.rotation,
                    localPlayer.aimAngle,
                    localPlayer.grounded ? "[GND]" : "[AIR]",
                    localPlayer.crouched ? "[CROUCH]" : "[STAND]"));
            lines.add(String.format(
                    "vision: reach %.0f u (hip %.0f / ADS %.0f)  cone %.0f°  feather %.0f°  floor %.2f  %s",
                    visionReach,
                    VisionConfig.REACH_HIP,
                    VisionConfig.REACH_ADS,
                    VisionConfig.CONE_ANGLE_DEGREES,
                    VisionConfig.FEATHER_ANGLE_DEGREES,
                    VisionConfig.AMBIENT_FLOOR,
                    localPlayer.ads ? "[ADS]" : "[HIP]"));
            lines.add(String.format(
                    "combat: %s  hp %.0f/%.0f  spread %.2f°  kick %.1f°  %d-%d  rounds %d%s",
                    localPlayer.heldWeaponDisplayName(),
                    localPlayer.health,
                    CombatConfig.MAX_HEALTH,
                    localPlayer.spread,
                    localPlayer.gunKick,
                    localPlayer.kills,
                    localPlayer.deaths,
                    projectileCount,
                    localPlayer.alive
                        ? ""
                        : String.format("  [DEAD — respawn in %.1fs]", localPlayer.respawnTimer)));
            PlayerLoadout loadout = localPlayer.loadout;
            if (loadout != null) {
                WeaponItem item = loadout.activeItem();
                int heldWireId = loadout.heldWeaponWireId();
                String utilityName = UtilityRegistry.displayNameForWireId(heldWireId);
                String held = utilityName.isEmpty()
                    ? WeaponRegistry.displayNameForWireId(heldWireId) : utilityName;
                String ammo = loadout.utilityActive()
                    ? String.format("  x%d", loadout.utilityCountForSlot(loadout.activeSlot))
                    : item == null ? "" : String.format("  %d/%d", item.magazine, item.reserve);
                String reload = loadout.reloading
                    ? String.format("  [reload %.1fs]", Math.max(0f, loadout.reloadTimer))
                    : "";
                lines.add(String.format(
                    "loadout: slot %d/5  %s%s%s  swap-from=%s",
                    loadout.activeSlot,
                    held,
                    ammo,
                    reload,
                    loadout.quickSwapOrigin == PlayerLoadout.NO_QUICK_SWAP
                        ? "-" : String.valueOf(loadout.quickSwapOrigin)));
                String pendingPress = loadoutController.debugStatusLine();
                if (!pendingPress.isEmpty()) {
                    lines.add(pendingPress);
                }
            }
            for (PacketKillEvent kill : session.killFeed()) {
                lines.add("  " + kill.feedLine());
            }
        } else {
            lines.add(String.format(
                    "camera: %.0f, %.0f  view %.0f u  |  arena %.0f x %.0f, %d solids",
                    camera.x(),
                    camera.y(),
                    camera.viewportHeight(),
                    arena.width(),
                    arena.height(),
                    arena.solids().size()));
        }
        lines.add("A/D move  W jump  Space jetpack  S crouch  LMB fire  RMB aim/ADS"
            + "  1-5 slot (tap 1/2 quick-swap)  [ / ]/wheel cycle  Enter chat/console");
        return lines;
    }

    /** {@code " [CHEATS]"} whenever the server reports any player has a debug toggle on, else "". */
    private String cheatsTagOrEmpty() {
        PacketGameState snapshot = session.latestSnapshot();
        return snapshot != null && snapshot.cheatsActive ? "  [CHEATS]" : "";
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        overlay.resize(width, height);
        contrastTestOverlay.resize(width, height);
        consoleDialog.resize(width, height);
        if (pipeline != null) {
            pipeline.resize(width, height);
        }
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        consoleDialog.dispose();
        session.disconnect();
        terrain.dispose();
        playerRenderer.dispose();
        projectileRenderer.dispose();
        thrownUtilityRenderer.dispose();
        trajectoryRenderer.dispose();
        overlay.dispose();
        hitboxOverlay.dispose();
        contrastTestOverlay.dispose();
        if (pipeline != null) {
            pipeline.dispose();
        }
    }
}
