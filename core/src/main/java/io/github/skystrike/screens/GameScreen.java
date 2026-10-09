package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import io.github.skystrike.chat.ChatClient;
import io.github.skystrike.chat.ChatMuteList;
import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandModule;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.command.DebugKeyController;
import io.github.skystrike.shared.command.CommandException;
import io.github.skystrike.shared.command.Cvar;
import io.github.skystrike.fx.FxBudget;
import io.github.skystrike.fx.FxPipeline;
import io.github.skystrike.fx.FxStats;
import io.github.skystrike.fx.lighting.Light;
import io.github.skystrike.fx.lighting.VisibilitySystem.ObserverState;
import io.github.skystrike.audio.AudioSystem;
import io.github.skystrike.audio.EffectAudio;
import io.github.skystrike.audio.GunAudio;
import io.github.skystrike.audio.SoundCatalog;
import io.github.skystrike.audio.TinnitusEffect;
import io.github.skystrike.gameplay.LoadoutController;
import io.github.skystrike.gameplay.SurveillanceController;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.InputSampler;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.net.Interpolator;
import io.github.skystrike.net.LocalPrediction;
import io.github.skystrike.net.StateBuffer;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.GadgetRenderer;
import io.github.skystrike.render.HitboxOverlay;
import io.github.skystrike.render.PlayerRenderer;
import io.github.skystrike.render.ProjectileRenderer;
import io.github.skystrike.render.ThrownUtilityRenderer;
import io.github.skystrike.render.TrajectoryRenderer;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.debug.DebugState;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
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
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketEffectSpawn;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.settings.Settings;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.vision.VisionMath;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.ui.console.ConsoleDialog;
import io.github.skystrike.ui.hud.HudFrame;
import io.github.skystrike.ui.hud.HudStage;
import io.github.skystrike.ui.text.ContrastTestOverlay;
import io.github.skystrike.ui.text.MessageBuffer;
import io.github.skystrike.ui.text.MessageSeverity;
import io.github.skystrike.world.TerrainRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Composition root for a match.
 *
 * <p>Routes input, drives authoritative prediction/interpolation, and coordinates multi-pass
 * rendering across terrain, entities, occluded visibility, additive player lights and the fog
 * composite.
 */
public final class GameScreen extends de.eskalon.commons.screen.ManagedScreenAdapter {

    private static final float PAN_SPEED_UNITS_PER_SECOND = 900f;
    private static final float ZOOM_RATE_PER_SECOND = 1.6f;

    private final ArenaMap arena = ArenaMap.standard();
    private final GameCamera camera = new GameCamera(arena.width(), arena.height());
    private final TerrainRenderer terrain = new TerrainRenderer(arena);
    private final PlayerRenderer playerRenderer = new PlayerRenderer();
    /** Drones, throw cameras, shield arcs and fuel tanks, in the entity layer (roadmap §6.5). */
    private final GadgetRenderer gadgetRenderer = new GadgetRenderer();
    private final ProjectileRenderer projectileRenderer = new ProjectileRenderer();
    /**
     * Phase 9: the client's one mixer, and the three things that ask it for sound — the world
     * effect-event listener, the weapon-state bridge, and the stun ring. The mixer is owned here
     * and disposed with the screen, so a match can never leave a looping voice behind.
     */
    private final AudioSystem audio;
    private final SoundCatalog soundCatalog;
    private final GunAudio gunAudio;
    private final EffectAudio effectAudio;
    private final TinnitusEffect tinnitus;
    private final ThrownUtilityRenderer thrownUtilityRenderer = new ThrownUtilityRenderer();
    private final TrajectoryRenderer trajectoryRenderer = new TrajectoryRenderer();
    private final HitboxOverlay hitboxOverlay = new HitboxOverlay();
    private final ContrastTestOverlay contrastTestOverlay = new ContrastTestOverlay();
    private final ClientSession session;

    /**
     * The HUD (build plan M4 §5): bars, loadout bar, crosshair, kill feed, damage vignette,
     * the debug panel that absorbed {@code StatusOverlay}, and the loadout picker. Built in the
     * constructor because it needs the session's sender and the player renderer's sprite atlas.
     */
    private final HudStage hud;

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
    /** The same guard for the loadout picker: L closes it, and must not re-open it. */
    private long pickerClosedFrameId = -1L;
    /** Wall-clock duration of the previous frame, for the {@code fps} command's detail. */
    private float lastDeltaMillis;

    private final KeyBindings bindings;
    private final Settings settings;
    private final InputRouter inputRouter = new InputRouter();
    private final InputMultiplexer rootInput = new InputMultiplexer();
    private final PauseOverlay pauseOverlay;
    private final GameSettingsDialog settingsDialog;
    private final LoadoutController loadoutController;
    /**
     * The client half of the surveillance lock (mechanics §7, §9): predicts the view, the piloted
     * drone's motion and the manual-gadget presses, and retransmits the view edges until the
     * server acknowledges them. The server remains the authority; this is feel.
     */
    private final SurveillanceController surveillance;
    private final InputSampler inputSampler;
    private final DebugKeyController debugKeyController;
    private final Runnable disconnectToMenu;
    private final Consumer<GameScreen> settingsOpener;
    private boolean disposeOnHide = true;
    private boolean disposed;

    /** The viewport height before the current surveillance view, restored on exit. */
    private float viewportBeforeSurveillance = -1f;
    /** Tracks the predicted view so the zoom is applied and released exactly once per transition. */
    private boolean wasSurveilling;

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
        this(new ClientSession(playerName), host, tcpPort, udpPort, new KeyBindings(), null, null, new Settings());
    }

    /** Builds the play screen around the session accepted by {@link ConnectingScreen}. */
    public GameScreen(ClientSession session, String host, int tcpPort, int udpPort) {
        this(session, host, tcpPort, udpPort, new KeyBindings(), null, null, new Settings());
    }

    /**
     * Builds a routed match. The injected bindings are the same persistent object settings and
     * console commands edit; disconnect and settings actions go back through Main's screen router.
     */
    public GameScreen(
            ClientSession session,
            String host,
            int tcpPort,
            int udpPort,
            KeyBindings bindings,
            Runnable disconnectToMenu,
            Consumer<GameScreen> settingsOpener) {
        this(session, host, tcpPort, udpPort, bindings, disconnectToMenu, settingsOpener, new Settings());
    }

    /** Builds a routed match with the settings object already owned by the application root. */
    public GameScreen(
            ClientSession session,
            String host,
            int tcpPort,
            int udpPort,
            KeyBindings bindings,
            Runnable disconnectToMenu,
            Consumer<GameScreen> settingsOpener,
            Settings settings) {
        if (session == null || bindings == null) {
            throw new IllegalArgumentException("session and bindings are required");
        }
        this.session = session;
        this.host = host;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
        this.bindings = bindings;
        this.settings = settings == null ? new Settings() : settings;
        this.disconnectToMenu = disconnectToMenu == null ? session::disconnect : disconnectToMenu;
        this.settingsOpener = settingsOpener == null ? ignored -> { } : settingsOpener;
        this.audio = new AudioSystem();
        this.soundCatalog = new SoundCatalog();
        this.effectAudio = new EffectAudio(this.audio, this.soundCatalog, this::isAudioBlocked);
        this.gunAudio = new GunAudio(this.audio, this.soundCatalog, this::isAudioBlocked);
        this.tinnitus = new TinnitusEffect(this.audio, this.soundCatalog);
        this.surveillance = new SurveillanceController(this.bindings, inputRouter);
        this.loadoutController = new LoadoutController(this.bindings, inputRouter, this.surveillance);
        this.inputSampler = new InputSampler(this.bindings, inputRouter, loadoutController);
        this.pauseOverlay = new PauseOverlay(inputRouter, this::onPauseAction);
        this.settingsDialog = new GameSettingsDialog(inputRouter, this.settings, this.bindings);
        rootInput.addProcessor(inputRouter.multiplexer());
        rootInput.addProcessor(scrollForwarder);
        addInputProcessor(rootInput);

        this.session.setSnapshotListener(this::onGameStateSnapshot);
        this.session.setKillListener(this::onKillEvent);
        this.session.setDamageListener(this::onDamageEvent);
        this.session.setEffectListener(this::onEffectSpawn);
        this.session.setChatListener(this::onChatMessage);
        this.session.setCapabilityListener(this::onCapabilities);
        this.session.setSessionResetListener(() -> {
            capabilities.reset();
            gunAudio.reset();
            surveillance.reset();
            // Leaving a match must not leave the stun ring playing into the menu.
            tinnitus.stop();
        });
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
        this.debugKeyController = new DebugKeyController(this.bindings, commandService);

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

        // The HUD borrows the player renderer's weapon atlas rather than loading a second copy,
        // and sends picker requests down the same reliable channel as every other c2s packet.
        this.hud = new HudStage(inputRouter, session::sendReliable, playerRenderer.weaponSprites());
        this.hud.picker().setCloseRequest(() -> {
            pickerClosedFrameId = Gdx.graphics.getFrameId();
            setUiLoadout(false);
        });
    }

    /** The console disconnect command follows the same teardown route as the pause action. */
    private void disconnectFromConsole() {
        disconnectToMenu.run();
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
     * M7 §8.1: an effect batch arrived. The listener only enqueues into the pipeline's event
     * queue — the FX update drains it later in this same frame, on this same render thread, and
     * nothing is ever spawned from the network thread.
     */
    private void onEffectSpawn(PacketEffectSpawn packet) {
        if (pipeline != null) {
            pipeline.enqueueEffects(packet);
        }
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
        gunAudio.onSnapshot(snapshot);

        int localId = session.playerId();
        if (localId >= 0 && snapshot.players != null) {
            for (Player p : snapshot.players) {
                if (p.id == localId) {
                    prediction.reconcile(p, arena);
                    loadoutController.onAuthoritativePlayer(p, prediction.predicted());
                    surveillance.onAuthoritativePlayer(p, prediction.predicted());
                    break;
                }
            }
        }
        // The devices this player owns drive the surveillance prediction; the snapshot is the
        // freshest word on what is deployed, so it feeds the controller directly.
        surveillance.setDevices(snapshot.drones, snapshot.cameras, localId);
    }

    /** The kill feed is a HUD widget now (M4 §5); the log line stays for headless debugging. */
    private void onKillEvent(PacketKillEvent kill) {
        hud.onKill(kill, System.currentTimeMillis());
        Gdx.app.log("SkyStrike", kill.feedLine());
    }

    /**
     * Damage taken drives the directional vignette. Damage dealt arrives on the same event and
     * is deliberately ignored here: the hit marker already reports it, and the session owns
     * that timer.
     */
    private void onDamageEvent(PacketDamageEvent damage) {
        hud.onDamage(damage, prediction.predicted());
    }

    @Override
    public void show() {
        camera.centreOn(arena.mirrorAxisX(), camera.viewportHeight() / 2f);
        pauseOverlay.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (pipeline == null) {
            pipeline = new FxPipeline(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        // Phase 9: audio consumes the very same effect-event queue as the particles.
        pipeline.setEffectListener(effectAudio);
        // Warm the ring's asset at match start: the first stun a player takes should not also be
        // the frame a WAV is decoded on.
        audio.prepare(soundCatalog.tinnitusSpec());
        session.connect(host, tcpPort, udpPort);
    }

    @Override
    public void render(float delta) {
        // Phase 9: the three sliders, applied once per frame, then the voice pool's clock.
        audio.setVolumes(settings.masterVolume, settings.musicVolume, settings.effectsVolume);
        audio.update(delta);
        session.update(delta);
        lastDeltaMillis = delta * 1000f;
        consoleDialog.update(delta);

        // Escape is polled only while no dialog/modal owns the focus stack — the console and the
        // pause menu keep their own claim on it through that focus. What is left over routes to
        // surveillance: while piloting, Escape exits the view instead of opening the pause menu
        // (mechanics §9; playable build plan §12's single-owner rule for a shared key).
        boolean escapePressed = inputRouter.isGameplayActive()
            && (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || Gdx.input.isKeyJustPressed(bindings.viewExit));
        if (escapePressed) {
            Player predictedNow = prediction.predicted();
            if (predictedNow != null && predictedNow.isSurveillanceLocked()) {
                surveillance.exitSurveillance(predictedNow);
            } else {
                pauseOverlay.open();
            }
        }

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
            pollLoadoutPickerKey();
        }

        Player localPlayer = prediction.predicted();
        float visionReach = VisionConfig.REACH_HIP;

        // The HUD's non-drawing frame work: font lifecycle, vignette decay, and the picker's
        // open state, which follows the ui_loadout cvar rather than a flag of its own.
        hud.setLocalPlayerId(session.playerId());
        hud.update(delta, isUiLoadoutOpen(), localPlayer == null ? null : localPlayer.loadout);

        // cl_freecam (F2): input packets keep sending zeroed intent even while detached.
        inputSampler.setFreecamActive(debugState.freecam());

        if (localPlayer != null) {
            // 1. Loadout input first: a slot press this frame rides this frame's input packet
            loadoutController.update(localPlayer);
            // The surveillance controller polls the view-cycle key the same way.
            surveillance.update(localPlayer);

            // 2. Sample input and simulate predicted local motion. While surveilling, the aim is
            //    measured from the device the player is looking through, so the piloted cone
            //    follows the cursor rather than pointing from the locked body.
            SurveillanceController.ViewTarget aimAnchor = surveillance.aimAnchor(localPlayer);
            PacketPlayerInput input = inputSampler.sample(localPlayer, camera, aimAnchor.x(), aimAnchor.y());
            surveillance.stampPacket(input);
            gunAudio.onLocalInput(input, localPlayer, bindings.isFireJustPressed());
            localPlayer = prediction.predict(input, delta, arena);
            // The piloted drone is predicted locally, so the view and the cone stay crisp.
            surveillance.predictDrone(localPlayer, input, delta, arena);
            session.sendUnreliable(input);

            if (debugState.freecam() && consoleDialog.isGameplayActive()) {
                // The camera detaches entirely: pan/zoom by hand instead of following the player.
                sampleCameraInput(delta);
            } else if (localPlayer.isSurveillanceLocked()) {
                // The camera follows the device the player is looking through, zoomed for a
                // throw camera (mechanics §7.2). The pre-surveillance viewport is restored on exit.
                SurveillanceController.ViewTarget viewTarget = surveillance.viewTarget(localPlayer);
                if (!wasSurveilling) {
                    viewportBeforeSurveillance = camera.viewportHeight();
                }
                camera.centreOn(viewTarget.x(), viewTarget.y());
                camera.setViewportHeight(viewportBeforeSurveillance / viewTarget.zoom());
            } else {
                if (wasSurveilling && viewportBeforeSurveillance > 0f) {
                    camera.setViewportHeight(viewportBeforeSurveillance);
                    viewportBeforeSurveillance = -1f;
                }
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
            wasSurveilling = localPlayer.isSurveillanceLocked();
        } else if (consoleDialog.isGameplayActive()) {
            // Fallback manual camera pan while waiting for join/spawn
            sampleCameraInput(delta);
        }

        // 3. Interpolate remote player and projectile states
        List<Player> remotePlayers = interpolator.interpolateRemotePlayers(session.playerId());
        List<Projectile> projectiles = interpolator.interpolateProjectiles();
        List<ThrownUtility> thrownUtilities = interpolator.interpolateThrownUtilities();
        List<UtilityZone> utilityZones = interpolator.latestUtilityZones();
        List<DroneEntity> drones = interpolator.interpolateDrones();
        List<CameraEntity> cameras = interpolator.interpolateCameras();

        // The snapshot zones are the sole source for the shader's smoke circles. This mirrors
        // the server's UtilitySystem smokeVolumes list rather than inventing a client-only cloud.
        pipeline.smokeVolumes().clear();
        for (UtilityZone zone : utilityZones) {
            if (zone.blocksVision()) {
                pipeline.smokeVolumes().add(zone.smokeVolume());
            }
        }

        // Phase 9: the mixer's ears follow the local player — or the camera before a spawn — so
        // every sound the drain asks for this frame is placed from the right position. While
        // surveilling, the ears follow the device: the player is listening through it.
        float listenerX = localPlayer == null ? camera.x() : localPlayer.centerX();
        float listenerY = localPlayer == null ? camera.y() : localPlayer.centerY();
        if (localPlayer != null && localPlayer.isSurveillanceLocked()) {
            SurveillanceController.ViewTarget ears = surveillance.viewTarget(localPlayer);
            listenerX = ears.x();
            listenerY = ears.y();
        }
        audio.updateListener(listenerX, listenerY);

        // M7: the effects tier follows the quality cvar live (falling back to the settings
        // object's tier), and the FX update drains the effect queue — once — handing each event
        // to the particles and to audio, then fires due phases.
        pipeline.setQualityTier(resolveFxTier());
        pipeline.updateFx(delta);

        // Player-light radius and intensity come from the same live cvar registry as the other
        // graphics controls. Remote sources are filtered by shared vision/LOS before entering
        // the pool, then LightPass masks each fragment against the rendered visibility texture.
        float playerLightRadius = floatCvar("r_player_light_radius", Light.DEFAULT_PLAYER_RADIUS);
        float playerLightIntensity = floatCvar("r_player_light_intensity", Light.DEFAULT_PLAYER_INTENSITY);
        pipeline.syncPlayerLights(
                localPlayer,
                remotePlayers,
                arena,
                visionReach,
                debugState.playerLight(),
                playerLightRadius,
                playerLightIntensity,
                debugState.playerLightShadows());

        // 4. Multi-pass rendering pipeline (effects §5)
        // Pass 1: SCENE (Terrain + Entities into scene buffer)
        pipeline.beginScene();
        terrain.render(camera);
        trajectoryRenderer.render(camera, localPlayer, arena);
        playerRenderer.render(camera, remotePlayers, localPlayer);
        // The piloted drone is drawn from the local prediction so it never lags the view.
        gadgetRenderer.render(camera, remotePlayers, localPlayer, renderedDrones(localPlayer, drones), cameras);
        projectileRenderer.render(camera, projectiles);
        thrownUtilityRenderer.render(camera, thrownUtilities);
        // M7: the alpha particle batch joins the scene, so the fog darkens smoke and dust.
        pipeline.renderAlphaParticles(camera);
        pipeline.endScene();

        // Pass 2: VISIBILITY (Observers + SDF Soft Shadows into half-res visibility buffer)
        // The observer set is the player's own cone plus every device they own: a deployed drone
        // projects its own (narrower, dimmer) cone even when nobody is piloting it, and a stuck
        // camera's cone is an observation post. While piloting, the body's cone is replaced by
        // the piloted device's — the point of view has genuinely moved.
        observers.clear();
        if (localPlayer != null) {
            int localId = session.playerId();
            if (!localPlayer.isSurveillanceLocked()) {
                observers.add(ObserverState.standardPlayer(
                        localPlayer.eyeX(), localPlayer.eyeY(), localPlayer.aimAngle, visionReach));
            }
            DroneEntity piloted = localPlayer.surveillance() == SurveillanceView.DRONE
                ? surveillance.predictedDrone()
                : null;
            for (DroneEntity drone : drones) {
                if (drone.ownerId != localId) {
                    continue;
                }
                if (piloted != null && drone.id == piloted.id) {
                    continue; // drawn from the local prediction below, so the cone matches the view
                }
                observers.add(ObserverState.gadget(
                    drone.x, drone.y, drone.aimAngle,
                    GadgetConfig.DRONE_VISION_RANGE,
                    GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f,
                    VisionConfig.FEATHER_ANGLE_DEGREES,
                    GadgetConfig.DRONE_VISION_BRIGHTNESS));
            }
            if (piloted != null) {
                observers.add(ObserverState.gadget(
                    piloted.x, piloted.y, piloted.aimAngle,
                    GadgetConfig.DRONE_VISION_RANGE,
                    GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f,
                    VisionConfig.FEATHER_ANGLE_DEGREES,
                    GadgetConfig.DRONE_VISION_BRIGHTNESS));
            }
            for (CameraEntity camera : cameras) {
                if (camera.ownerId != localId || !camera.stuck) {
                    continue;
                }
                observers.add(ObserverState.gadget(
                    camera.x, camera.y, camera.aimAngle,
                    GadgetConfig.CAMERA_VISION_RANGE,
                    GadgetConfig.CAMERA_VISION_ANGLE_DEGREES / 2f,
                    VisionConfig.FEATHER_ANGLE_DEGREES,
                    1f));
            }
        }
        pipeline.renderVisibility(camera, observers, !isShadowsOn());

        // Pass 3: LIGHTS (half-resolution additive player lights, SDF-shadowed and vision-gated)
        pipeline.renderLights(camera, localPlayer, !isShadowsOn());

        // Pass 4: COMPOSITE (scene * max(visibility, ambientFloor) + safe light buffer)
        pipeline.composite();

        // M7: the additive particle batch glows through the fog, gated by the visibility
        // texture; the flashbang whiteout rides above it, below the HUD.
        pipeline.renderAdditiveParticles(camera);
        // Phase 9: the ring rides the same authoritative blind state as the whiteout above, so
        // the player's ears and eyes recover together (and the ring outlasts the light).
        float blindIntensity = session.blindIntensity();
        tinnitus.update(delta, blindIntensity);
        pipeline.renderBlindness(blindIntensity);

        // Pass 5: DEBUG OVERLAYS
        if (debugState.sdfView()) {
            pipeline.renderSdfDebug(camera);
        }
        if (debugState.hitboxes()) {
            hitboxOverlay.render(camera, remotePlayers, localPlayer);
        }

        // Pass 6: the HUD, drawn unoccluded over the composite and under the console (M4 §5).
        // The debug readout is one widget inside it now, gated by cl_debug_overlay, so there is
        // a single screen-space projection for everything the player reads.
        hud.render(new HudFrame(
            localPlayer,
            adsAlpha,
            session.hitMarkerActive(),
            session.hitMarkerAlpha(),
            session.hitMarkerHeadshot(),
            session.hitMarkerLethal(),
            System.currentTimeMillis(),
            delta,
            debugState.overlay(),
            debugState.overlay()
                ? statusLines(localPlayer, visionReach, projectiles.size())
                : List.of()));

        // Pass 7: the chat/console dialog, above everything else (passive view when closed).
        consoleDialog.render(System.currentTimeMillis());

        // The visible pause modal is above the HUD and console. Its InputRouter focus owns Escape.
        pauseOverlay.render(delta);
        // In-game settings dialog: true-modal, dim+window, same grouped controls as
        // SettingsScreen but without routing away from the match.
        settingsDialog.render(delta);

        // ui_contrast_test (F12): a full-screen developer test card, so it wins over everything
        // including the console — exactly the "readable on all four means readable in the game"
        // validation the console plan asks for.
        if (debugState.contrastTest()) {
            contrastTestOverlay.render();
        }
    }

    /**
     * The loadout-picker key (build plan M4 §5: {@code ui_loadout}, default {@code L}). It only
     * ever opens the picker: once open, the picker holds input focus and owns the key that
     * closes it, exactly as the console dialog does with Enter.
     */
    private void pollLoadoutPickerKey() {
        if (bindings.isUiLoadoutJustPressed()
            && Gdx.graphics.getFrameId() != pickerClosedFrameId) {
            setUiLoadout(true);
        }
    }

    /** {@code ui_loadout}: the cvar is the picker's open flag, so this is the only read. */
    private boolean isUiLoadoutOpen() {
        Cvar cvar = commandService.cvars().find("ui_loadout");
        return cvar != null && Boolean.parseBoolean(cvar.value());
    }

    /**
     * Writes {@code ui_loadout}. Unlike the M3 debug keys this does not go through
     * {@code commandService.submit}: the picker is a normal UI feature that must work on a
     * server granting no console at all, and echoing {@code /ui_loadout true} into the chat
     * scrollback every time a player glances at their guns would be noise, not transparency.
     */
    private void setUiLoadout(boolean value) {
        Cvar cvar = commandService.cvars().find("ui_loadout");
        if (cvar == null) {
            return;
        }
        try {
            cvar.set(String.valueOf(value));
        } catch (CommandException impossible) {
            // A BOOL cvar cannot reject "true"/"false"; nothing useful to tell the player.
            Gdx.app.error("SkyStrike", "ui_loadout rejected a boolean", impossible);
        }
    }

    /** {@code r_shadows}: soft by default; unrouted through {@link DebugState} since it ships on. */
    private boolean isShadowsOn() {
        Cvar cvar = commandService.cvars().find("r_shadows");
        return cvar == null || Boolean.parseBoolean(cvar.value());
    }

    /** Reads a validated float cvar, with the feature's documented default when debug is locked. */
    private float floatCvar(String name, float fallback) {
        Cvar cvar = commandService.cvars().find(name);
        if (cvar == null) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(cvar.value());
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException invalidValue) {
            return fallback;
        }
    }

    /**
     * The effects quality tier (M7 §8.2): the live {@code quality} cvar wins, the settings
     * object's tier is the fallback. Re-resolved every frame, so a settings change applies
     * without leaving the match.
     */
    private FxBudget.Tier resolveFxTier() {
        Cvar quality = commandService.cvars().find("quality");
        if (quality != null) {
            FxBudget.Tier tier = FxBudget.parseTier(quality.value());
            if (tier != null) {
                return tier;
            }
        }
        return FxBudget.fromSettingsTier(settings.qualityTier);
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
        lines.add("SkyStrike - M10 (Gadgets: drone, throw camera, shield, fuel tank)");
        lines.add("server: " + session.statusLine() + cheatsTagOrEmpty());
        if (debugState.playerLight() || debugState.playerLightShadows() || debugState.fxDebug()) {
            lines.add(String.format(
                "debug: playerLight=%s  radius=%.0f  intensity=%.2f  shadows=%s  fx_debug=%s",
                debugState.playerLight(),
                floatCvar("r_player_light_radius", Light.DEFAULT_PLAYER_RADIUS),
                floatCvar("r_player_light_intensity", Light.DEFAULT_PLAYER_INTENSITY),
                debugState.playerLightShadows(),
                debugState.fxDebug()));
        }
        if (debugState.fxDebug()) {
            // Phase 9 gate: the mixer's pool and the effect channel's counters, plus the ring.
            lines.add(String.format(
                "sfx: %s  %s  tinnitus %.2f",
                audio.statusLine(),
                effectAudio.statusLine(),
                tinnitus.level()));
        }
        if (debugState.fxDebug() && pipeline != null) {
            // The M7 gate: particle and light counts staying inside the tier budget.
            FxStats stats = pipeline.fxStats();
            lines.add(String.format(
                "fx: particles %d/%d alpha + %d/%d add + %d/%d cpu  lights %d/%d  phases %d  events %d  tier %s",
                stats.alphaParticles(),
                stats.alphaCapacity(),
                stats.additiveParticles(),
                stats.additiveCapacity(),
                stats.cpuParticles(),
                stats.cpuCapacity(),
                stats.effectLights(),
                stats.effectLightCap(),
                stats.pendingPhases(),
                session.effectsReceived(),
                pipeline.particles().budget().tier()));
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
                        : String.format("  [DEAD - respawn in %.1fs]", localPlayer.respawnTimer)));
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
                if (localPlayer.isSurveillanceLocked()) {
                    DroneEntity piloted = surveillance.predictedDrone();
                    lines.add(String.format(
                        "surveillance: %s  %s",
                        localPlayer.surveillance(),
                        piloted == null
                            ? "device position unavailable"
                            : String.format(
                                "drone (%.0f, %.0f) hp %.0f", piloted.x, piloted.y, piloted.health)));
                }
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
            + "  1-5 slot (tap 1/2 quick-swap)  [ / ]/wheel cycle  Q/E gadget  6 view cycle"
            + "  Esc exit/pause  Enter chat/console");
        return lines;
    }

    /** {@code " [CHEATS]"} whenever the server reports any player has a debug toggle on, else "". */
    private String cheatsTagOrEmpty() {
        PacketGameState snapshot = session.latestSnapshot();
        return snapshot != null && snapshot.cheatsActive ? "  [CHEATS]" : "";
    }

    /**
     * The drone list to render: the interpolated drones, with the piloted one replaced by the
     * local prediction, so the device the camera is following never lags behind the view.
     */
    private List<DroneEntity> renderedDrones(Player localPlayer, List<DroneEntity> drones) {
        DroneEntity piloted = localPlayer != null
                && localPlayer.surveillance() == SurveillanceView.DRONE
            ? surveillance.predictedDrone()
            : null;
        if (piloted == null) {
            return drones;
        }
        List<DroneEntity> rendered = new ArrayList<>(drones);
        boolean replaced = false;
        for (int i = 0; i < rendered.size(); i++) {
            if (rendered.get(i).id == piloted.id) {
                rendered.set(i, piloted.copy());
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            rendered.add(piloted.copy());
        }
        return rendered;
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        hud.resize(width, height);
        contrastTestOverlay.resize(width, height);
        consoleDialog.resize(width, height);
        pauseOverlay.resize(width, height);
        settingsDialog.resize(width, height);
        if (pipeline != null) {
            pipeline.resize(width, height);
        }
    }

    /** Opens the existing M4 picker, including its weapon artwork and focus handling. */
    public void openLoadout() {
        setUiLoadout(true);
    }

    /** Sends a menu-authored picker request only after ConnectingScreen has transferred ownership. */
    public void requestLoadout(io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate request) {
        if (request != null && !request.isEmpty()) {
            session.sendReliable(request);
        }
    }

    /** Keep GL and session resources alive for the temporary Game -> Settings -> Game route. */
    public void suspendForSettings() {
        disposeOnHide = false;
    }

    private void onPauseAction(String action) {
        if ("Disconnect".equals(action)) {
            disconnectToMenu.run();
        } else if ("Loadout".equals(action)) {
            setUiLoadout(true);
        } else if ("Settings".equals(action)) {
            // In-game settings no longer routes to a full SettingsScreen. The pause
            // Settings entry now opens a true-modal settings dialog that mirrors the
            // visual hierarchy of PauseOverlay and SettingsScreen (dim + window panel,
            // grouped Video/Audio/Controls, scrollable binds) but stays inside the
            // match. This preserves suspendForSettings/retainedGame for the main-menu
            // path while keeping the match alive.
            openInGameSettings();
        }
    }

    private void openInGameSettings() {
        settingsDialog.open();
    }

    @Override
    public void hide() {
        if (disposeOnHide) {
            dispose();
        } else {
            // The next hide is a real route away from the retained match.
            disposeOnHide = true;
        }
    }

    /**
     * Phase 9: whether the world blocks a sound.
     *
     * <p>Deliberately the same shared {@link VisionMath} call with the same live smoke volumes the
     * stun bands, the remote-player light gate and the server's own effect culling use. One
     * answer to "is there a wall between us" means what a player hears cannot contradict what they
     * can see — the same parity rule the M7 effects gate holds the CPU and GPU paths to.
     */
    private boolean isAudioBlocked(float x0, float y0, float x1, float y1) {
        if (pipeline == null) {
            return false;
        }
        return !VisionMath.hasLineOfSight(x0, y0, x1, y1, arena, pipeline.smokeVolumes().all());
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        hud.picker().setOpen(false, null);
        consoleDialog.dispose();
        pauseOverlay.dispose();
        settingsDialog.dispose();
        inputRouter.clearFocus();
        session.disconnect();
        gunAudio.dispose();
        // Order matters: stop the loops and clear the voices, then release the assets.
        tinnitus.dispose();
        audio.dispose();
        terrain.dispose();
        playerRenderer.dispose();
        gadgetRenderer.dispose();
        projectileRenderer.dispose();
        thrownUtilityRenderer.dispose();
        trajectoryRenderer.dispose();
        hud.dispose();
        hitboxOverlay.dispose();
        contrastTestOverlay.dispose();
        if (pipeline != null) {
            pipeline.dispose();
            pipeline = null;
        }
    }
}
