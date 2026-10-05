package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import io.github.skystrike.fx.FxPipeline;
import io.github.skystrike.fx.lighting.VisibilitySystem.ObserverState;
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
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
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
    private final StatusOverlay overlay = new StatusOverlay();
    private final ClientSession session;

    private final KeyBindings bindings = new KeyBindings();
    private final InputRouter inputRouter = new InputRouter();
    private final InputSampler inputSampler = new InputSampler(bindings, inputRouter);
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
    }

    private void onGameStateSnapshot(PacketGameState snapshot) {
        stateBuffer.addSnapshot(snapshot);

        int localId = session.playerId();
        if (localId >= 0 && snapshot.players != null) {
            for (Player p : snapshot.players) {
                if (p.id == localId) {
                    prediction.reconcile(p, arena);
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
        Gdx.input.setInputProcessor(inputRouter.multiplexer());
        pipeline = new FxPipeline(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        session.connect(host, tcpPort, udpPort);
    }

    @Override
    public void render(float delta) {
        session.update(delta);

        Player localPlayer = prediction.predicted();
        float visionReach = VisionConfig.REACH_HIP;

        if (localPlayer != null) {
            // 1. Sample input and simulate predicted local motion
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
        playerRenderer.render(camera, remotePlayers, localPlayer, arena, pipeline.smokeVolumes().all());
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
        lines.add("SkyStrike — Phase 3 (Combat Core)");
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
                    localPlayer.weapon().displayName(),
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
            + "  [ / ] weapon  F1 SDF debug");
        return lines;
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
