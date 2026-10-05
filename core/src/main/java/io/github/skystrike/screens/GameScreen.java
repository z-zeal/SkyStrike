package io.github.skystrike.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import io.github.skystrike.net.ClientSession;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.StatusOverlay;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.world.TerrainRenderer;
import java.util.List;

/**
 * Composition root for a match.
 *
 * <p>Thin on purpose: it constructs the pieces, forwards the frame to them in layer order, and
 * owns nothing else. No gameplay decisions are made here and none ever will be — the server is
 * the authority and the systems that mirror it get their own classes.
 *
 * <p>Phase 0 draws the arena and a connection readout. Arrow keys pan the camera and the minus and
 * equals keys zoom.
 */
public final class GameScreen implements Screen {

    private static final float PAN_SPEED_UNITS_PER_SECOND = 900f;
    private static final float ZOOM_RATE_PER_SECOND = 1.6f;

    private final ArenaMap arena = ArenaMap.standard();
    private final GameCamera camera = new GameCamera(arena.width(), arena.height());
    private final TerrainRenderer terrain = new TerrainRenderer(arena);
    private final StatusOverlay overlay = new StatusOverlay();
    private final ClientSession session;

    private final String host;
    private final int tcpPort;
    private final int udpPort;

    public GameScreen(String playerName, String host, int tcpPort, int udpPort) {
        this.session = new ClientSession(playerName);
        this.host = host;
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }

    @Override
    public void show() {
        // Start on the mirror axis, bottom-aligned: all the playable geometry sits in the lower
        // half of the arena, so centring on the arena's middle would open on empty sky.
        camera.centreOn(arena.mirrorAxisX(), camera.viewportHeight() / 2f);
        session.connect(host, tcpPort, udpPort);
    }

    @Override
    public void render(float delta) {
        session.update(delta);
        sampleCameraInput(delta);

        Gdx.gl.glClearColor(0.035f, 0.045f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        terrain.render(camera);
        overlay.render(statusLines());
    }

    /**
     * The only input Phase 0 samples. Player input moves into {@code core/input} in Phase 1 so the
     * UI can consume it before gameplay sees it.
     */
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

    private List<String> statusLines() {
        return List.of(
            "SkyStrike — Phase 0",
            "server: " + session.statusLine(),
            String.format(
                "camera: %.0f, %.0f  view %.0f u  |  arena %.0f x %.0f, %d solids",
                camera.x(), camera.y(), camera.viewportHeight(),
                arena.width(), arena.height(), arena.solids().size()),
            "arrows pan  -/= zoom");
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
        overlay.dispose();
    }
}
