package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.InputSampler;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.net.Interpolator;
import io.github.skystrike.net.LocalPrediction;
import io.github.skystrike.net.StateBuffer;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.PlayerRenderer;
import io.github.skystrike.render.StatusOverlay;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.world.TerrainRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * Composition root for a match.
 *
 * <p>Thin composition root: routes input, triggers prediction and interpolation, and dispatches
 * drawing across render layers.
 */
public final class GameScreen implements Screen {

    private static final float PAN_SPEED_UNITS_PER_SECOND = 900f;
    private static final float ZOOM_RATE_PER_SECOND = 1.6f;

    private final ArenaMap arena = ArenaMap.standard();
    private final GameCamera camera = new GameCamera(arena.width(), arena.height());
    private final TerrainRenderer terrain = new TerrainRenderer(arena);
    private final PlayerRenderer playerRenderer = new PlayerRenderer();
    private final StatusOverlay overlay = new StatusOverlay();
    private final ClientSession session;

    private final KeyBindings bindings = new KeyBindings();
    private final InputRouter inputRouter = new InputRouter();
    private final InputSampler inputSampler = new InputSampler(bindings, inputRouter);
    private final LocalPrediction prediction = new LocalPrediction();
    private final StateBuffer stateBuffer = new StateBuffer();
    private final Interpolator interpolator = new Interpolator(stateBuffer);

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

    @Override
    public void show() {
        camera.centreOn(arena.mirrorAxisX(), camera.viewportHeight() / 2f);
        Gdx.input.setInputProcessor(inputRouter.multiplexer());
        session.connect(host, tcpPort, udpPort);
    }

    @Override
    public void render(float delta) {
        session.update(delta);

        Player localPlayer = prediction.predicted();
        if (localPlayer != null) {
            // 1. Sample input and simulate predicted local motion
            PacketPlayerInput input = inputSampler.sample(localPlayer, camera);
            localPlayer = prediction.predict(input, delta, arena);
            session.sendUnreliable(input);

            // 2. Camera follow and interpolated ADS pan toward aim direction
            adsAlpha = Lerp.smooth(adsAlpha, localPlayer.ads ? 1f : 0f, PlayerConfig.ADS_TRANSITION_RATE, delta);
            float panDist = PlayerConfig.ADS_CAMERA_PAN * adsAlpha;
            float aimRad = Angles.toRadians(localPlayer.aimAngle);
            float targetCamX = localPlayer.centerX() + panDist * (float) Math.cos(aimRad);
            float targetCamY = localPlayer.centerY() + panDist * (float) Math.sin(aimRad);
            camera.centreOn(targetCamX, targetCamY);
        } else {
            // Fallback manual camera pan while waiting for join/spawn
            sampleCameraInput(delta);
        }

        // 3. Interpolate remote player states
        List<Player> remotePlayers = interpolator.interpolateRemotePlayers(session.playerId());

        // 4. Render layers in order
        Gdx.gl.glClearColor(0.035f, 0.045f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Layer: TERRAIN
        terrain.render(camera);

        // Layer: ENTITIES
        playerRenderer.render(camera, remotePlayers, localPlayer);

        // Layer: HUD / OVERLAY
        overlay.render(statusLines(localPlayer));
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

    private List<String> statusLines(Player localPlayer) {
        List<String> lines = new ArrayList<>();
        lines.add("SkyStrike — Phase 1 (Movement & Aim)");
        lines.add("server: " + session.statusLine());
        if (localPlayer != null) {
            lines.add(String.format(
                "player: pos (%.0f, %.0f)  vel (%.0f, %.0f)  fuel %.0f  rot %.1f°  aim %.1f°  %s %s",
                localPlayer.x, localPlayer.y, localPlayer.vx, localPlayer.vy,
                localPlayer.fuel, localPlayer.rotation, localPlayer.aimAngle,
                localPlayer.grounded ? "[GND]" : "[AIR]",
                localPlayer.crouched ? "[CROUCH]" : "[STAND]"));
        } else {
            lines.add(String.format(
                "camera: %.0f, %.0f  view %.0f u  |  arena %.0f x %.0f, %d solids",
                camera.x(), camera.y(), camera.viewportHeight(),
                arena.width(), arena.height(), arena.solids().size()));
        }
        lines.add("A/D move  W jump  Space jetpack  S crouch  RMB aim/ADS  LMB fire");
        return lines;
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        overlay.resize(width, height);
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
        overlay.dispose();
    }
}
