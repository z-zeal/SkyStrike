package io.github.skystrike.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.RenderLayers;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.map.SpawnPoint;

/**
 * Draws the arena.
 *
 * <p>Reads the shared {@link ArenaMap} directly: there is no client-side copy of the layout and
 * no "art" version of the geometry. A wall that stops a bullet is the same rectangle that gets
 * drawn, which is the only way the picture can be trusted.
 *
 * <p>Flat colour for now. Sprites, normal maps and the baked occlusion field arrive in Phase 2.
 */
public final class TerrainRenderer implements Disposable {

    private static final Color SOLID_FILL = new Color(0.22f, 0.27f, 0.35f, 1f);
    private static final Color SOLID_EDGE = new Color(0.38f, 0.46f, 0.58f, 1f);
    private static final Color ARENA_EDGE = new Color(0.16f, 0.20f, 0.27f, 1f);
    private static final Color SPAWN_LEFT = new Color(0.30f, 0.62f, 0.95f, 1f);
    private static final Color SPAWN_RIGHT = new Color(0.95f, 0.45f, 0.30f, 1f);

    private static final float SPAWN_MARKER_RADIUS = 18f;

    private final ArenaMap map;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public TerrainRenderer(ArenaMap map) {
        this.map = map;
    }

    /** The layer this renderer belongs to. */
    public RenderLayers layer() {
        return RenderLayers.TERRAIN;
    }

    public void render(GameCamera camera) {
        shapes.setProjectionMatrix(camera.combined());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SOLID_FILL);
        for (Rect rect : map.solids()) {
            shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        }
        drawSpawnMarkers();
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(SOLID_EDGE);
        for (Rect rect : map.solids()) {
            shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
        }
        shapes.setColor(ARENA_EDGE);
        shapes.rect(0f, 0f, map.width(), map.height());
        shapes.line(map.mirrorAxisX(), 0f, map.mirrorAxisX(), map.height());
        shapes.end();
    }

    private void drawSpawnMarkers() {
        for (SpawnPoint spawn : map.spawns()) {
            shapes.setColor(spawn.teamIndex() == 0 ? SPAWN_LEFT : SPAWN_RIGHT);
            shapes.circle(spawn.x(), spawn.y(), SPAWN_MARKER_RADIUS);
        }
    }

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
