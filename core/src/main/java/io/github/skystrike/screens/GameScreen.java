package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.Screen;
import io.github.skystrike.chat.ChatClient;
import io.github.skystrike.chat.ChatMuteList;
import io.github.skystrike.command.ClientCapabilities;
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
import io.github.skystrike.render.PlayerRenderer;
import io.github.skystrike.render.ProjectileRenderer;
import io.github.skystrike.render.StatusOverlay;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.ui.text.MessageBuffer;
import io.github.skystrike.ui.text.MessageLine;
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

    /** Scrollback lines echoed into the debug readout until the dialog exists. */
    private static final int RECENT_CHAT_LINES = 4;

    private final ArenaMap arena = ArenaMap.standard();
    private final GameCamera camera = new GameCamera(arena.width(), arena.height());
    private final TerrainRenderer terrain = new TerrainRenderer(arena);
    private final PlayerRenderer playerRenderer = new PlayerRenderer();
    private final ProjectileRenderer projectileRenderer = new ProjectileRenderer();
    private final StatusOverlay overlay = new StatusOverlay();
    private final ClientSession session;

    /**
     * The chat/console state: the scrollback ring, the local mute list, and the one capability
     * the server owns. The dialog that renders them is still to come — until then the transport
     * is proven by the last few lines appearing in the debug readout.
     */
    private final MessageBuffer messages = new MessageBuffer();
    private final ChatMuteList muteList = new ChatMuteList();
    private final ClientCapabilities capabilities = new ClientCapabilities();
    private final ChatClient chatClient = new ChatClient(messages, muteList, capabilities);

    private final KeyBindings bindings = new KeyBindings();
    private final InputRouter inputRouter = new InputRouter();
    private final LoadoutController loadoutController = new LoadoutController(bindings, inputRouter);
    private final InputSampler inputSampler = new InputSampler(bindings, inputRouter, loadoutController);

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
    private boolean showSdfDebug;

    public GameScreen(String playerName, String host, int tcpPort, int udpPort) {
        this.session = new ClientSession(playerName);
        this.host = host;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;

        this.session.setSnapshotListener(this::onGameStateSnapshot);
        this.session.setKillListener(this::onKillEvent);
        this.session.setChatListener(this::onChatMessage);
        this.session.setCapabilityListener(this::onCapabilities);
        this.chatClient.setSender(session::sendReliable);
        this.loadoutController.setPacketSender(session::sendReliable);
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

        Player localPlayer = prediction.predicted();
        float visionReach = VisionConfig.REACH_HIP;

        if (localPlayer != null) {
            // 1. Loadout input first: a slot press this frame rides this frame's input packet
            loadoutController.update(localPlayer);

            // 2. Sample input and simulate predicted local motion
            PacketPlayerInput input = inputSampler.sample(localPlayer, camera);
            localPlayer = prediction.predict(input, delta, arena);
            session.sendUnreliable(input);

            // 2. Camera follow and smoothly interpolated ADS pan / vision reach
            adsAlpha = Lerp.smooth(adsAlpha, localPlayer.ads ? 1f : 0f, VisionConfig.ADS_TRANSITION_RATE, delta);
            visionReach = Lerp.mix(VisionConfig.REACH_HIP, VisionConfig.REACH_ADS, adsAlpha);

            float panDist = PlayerConfig.ADS_CAMERA_PAN * adsAlpha;
            float aimRad = Angles.toRadians(localPlayer.aimAngle);
            float targetCamX = localPlayer.centerX() + panDist * (float) Math.cos(aimRad);
            float targetCamY = localPlayer.centerY() + panDist * (float) Math.sin(aimRad);
            camera.centreOn(targetCamX, targetCamY);
        } else {
            // Fallback manual camera pan while waiting for join/spawn
            sampleCameraInput(delta);
        }

        // Toggle SDF debug view with F1
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            showSdfDebug = !showSdfDebug;
        }

        // 3. Interpolate remote player and projectile states
        List<Player> remotePlayers = interpolator.interpolateRemotePlayers(session.playerId());
        List<Projectile> projectiles = interpolator.interpolateProjectiles();

        // 4. Multi-pass rendering pipeline (effects §5)
        // Pass 1: SCENE (Terrain + Entities into scene buffer)
        pipeline.beginScene();
        terrain.render(camera);
        playerRenderer.render(camera, remotePlayers, localPlayer);
        projectileRenderer.render(camera, projectiles);
        pipeline.endScene();

        // Pass 2: VISIBILITY (Observers + SDF Soft Shadows into half-res visibility buffer)
        observers.clear();
        if (localPlayer != null) {
            observers.add(ObserverState.standardPlayer(
                    localPlayer.eyeX(), localPlayer.eyeY(), localPlayer.aimAngle, visionReach));
        }
        pipeline.renderVisibility(camera, observers);

        // Pass 3: COMPOSITE (scene * max(visibility, ambientFloor) + light onto backbuffer)
        pipeline.composite();

        // Pass 4: DEBUG OVERLAY
        if (showSdfDebug) {
            pipeline.renderSdfDebug(camera);
        }

        // Pass 5: HUD & OVERLAY (drawn unoccluded over composite)
        overlay.render(statusLines(localPlayer, visionReach, projectiles.size()));
    }

    private void sampleCameraInput(float delta) {
        float dx = 0f;
        float dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dx -= PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dx += PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dy -= PAN_SPEED_UNITS_PER_SECOND * delta;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
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
        lines.add("SkyStrike — Phase 4 (Weapons, Melee and Loadout)");
        lines.add("server: " + session.statusLine());
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
                String held = WeaponRegistry.displayNameForWireId(loadout.heldWeaponWireId());
                String ammo = item == null
                    ? ""
                    : String.format("  %d/%d", item.magazine, item.reserve);
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
                lines.add(loadoutController.debugStatusLine());
            }
            for (PacketKillEvent kill : session.killFeed()) {
                lines.add("  " + kill.feedLine());
            }
            appendRecentChat(lines);
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
            + "  1-5 slot (tap 1/2 quick-swap)  [ / ]/wheel cycle  F1 SDF debug");
        return lines;
    }

    /**
     * The last few scrollback lines, filtered exactly as the dialog will filter them. A stand-in
     * for the passive view until the dialog exists; it proves the relay end to end.
     */
    private void appendRecentChat(List<String> lines) {
        List<MessageLine> visible = messages.visibleLines(capabilities.consoleAccess());
        int from = Math.max(0, visible.size() - RECENT_CHAT_LINES);
        for (int i = from; i < visible.size(); i++) {
            MessageLine line = visible.get(i);
            String prefix = switch (line.channel()) {
                case TEAM -> "[TEAM] ";
                case ALL -> "";
                default -> "* ";
            };
            lines.add("  " + prefix
                + (line.hasAuthor() ? line.authorName() + ": " : "")
                + line.body());
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        overlay.resize(width, height);
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
        session.disconnect();
        terrain.dispose();
        playerRenderer.dispose();
        projectileRenderer.dispose();
        overlay.dispose();
        if (pipeline != null) {
            pipeline.dispose();
        }
    }
}
