package io.github.skystrike.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.fx.gl.BlendState;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.render.RenderLayers;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockRenderStyle;
import io.github.skystrike.shared.map.BlockRules;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.map.SpawnPoint;

/**
 * Draws the arena.
 *
 * <p>Reads the shared {@link ArenaMap} directly: there is no client-side copy of the layout and
 * no "art" version of the geometry. A wall that stops a bullet is the same rectangle that gets
 * drawn, which is the only way the picture can be trusted.
 *
 * <p>How a block is drawn comes from the {@link BlockRenderStyle} column of the shared
 * {@link BlockRules} table, and this class never names a block kind: a kind that gains a treatment
 * gains it here by gaining it in the table, and a treatment with no drawing code is a
 * compile-time omission rather than a silently invisible block. Shared decides the treatment and
 * core owns the palette, which is what keeps LibGDX out of shared.
 *
 * <p>Every draw pass is one loop over the whole block list, so the geometry cannot drift between
 * the fill and the outline of the same block.
 */
public final class TerrainRenderer implements Disposable {

    // Palette per treatment. A one-way slab is a slab, not a wall: it is filled with its own
    // colour and outlined along its top edge only, because that edge is the surface a body lands
    // on. Glass is translucent and carries a sheen, because the pane's whole reason to exist is
    // that sight and rounds pass through it. Doors are drawn as the closed panel with its seam;
    // the swing angle arrives with the authoritative door state.
    private static final Color SOLID_FILL_COLOR = new Color(0.22f, 0.27f, 0.35f, 1f);
    private static final Color SOLID_EDGE_COLOR = new Color(0.38f, 0.46f, 0.58f, 1f);
    private static final Color BREAKABLE_FILL_COLOR = new Color(0.38f, 0.31f, 0.22f, 1f);
    private static final Color BREAKABLE_EDGE_COLOR = new Color(0.60f, 0.48f, 0.33f, 1f);
    private static final Color GLASS_FILL_COLOR = new Color(0.40f, 0.68f, 0.80f, 0.30f);
    private static final Color GLASS_EDGE_COLOR = new Color(0.66f, 0.90f, 0.98f, 1f);
    private static final Color GLASS_SHEEN_COLOR = new Color(0.80f, 0.97f, 1f, 0.55f);
    private static final Color DOOR_FILL_COLOR = new Color(0.40f, 0.33f, 0.24f, 1f);
    private static final Color DOOR_EDGE_COLOR = new Color(0.64f, 0.53f, 0.36f, 1f);
    private static final Color DOOR_SEAM_COLOR = new Color(0.22f, 0.18f, 0.13f, 1f);
    private static final Color PLATFORM_FILL_COLOR = new Color(0.26f, 0.30f, 0.27f, 1f);
    private static final Color PLATFORM_TOP_COLOR = new Color(0.62f, 0.82f, 0.52f, 1f);

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

        // A glass pane is drawn with an alpha, so the pass runs with the same alpha blending every
        // other translucent pass in the frame uses. Opaque treatments are unaffected by it.
        BlendState.setAlpha();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (MapBlock block : map.blocks()) {
            drawFill(block);
        }
        drawSpawnMarkers();
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (MapBlock block : map.blocks()) {
            drawOutline(block);
        }
        shapes.setColor(ARENA_EDGE);
        shapes.rect(0f, 0f, map.width(), map.height());
        shapes.line(map.mirrorAxisX(), 0f, map.mirrorAxisX(), map.height());
        shapes.end();
    }

    /**
     * The filled body of one block, chosen by its shared treatment row.
     *
     * <p>A {@code BREAKABLE_FILL} block is drawn intact here on purpose: the crack overlay is a
     * function of durability, and durability does not exist yet. Drawing cracks nothing can compute
     * would be a damage readout that lies.
     */
    private void drawFill(MapBlock block) {
        Rect rect = block.rect();
        switch (BlockRules.renderStyle(block.kind())) {
            case SOLID_FILL -> {
                shapes.setColor(SOLID_FILL_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case BREAKABLE_FILL -> {
                shapes.setColor(BREAKABLE_FILL_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case GLASS_PANE -> {
                shapes.setColor(GLASS_FILL_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case DOOR_PANEL -> {
                shapes.setColor(DOOR_FILL_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case PLATFORM_SLAB -> {
                shapes.setColor(PLATFORM_FILL_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
        }
    }

    /** The outline treatment of one block: what distinguishes its treatment at a glance. */
    private void drawOutline(MapBlock block) {
        Rect rect = block.rect();
        switch (BlockRules.renderStyle(block.kind())) {
            case SOLID_FILL -> {
                shapes.setColor(SOLID_EDGE_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case BREAKABLE_FILL -> {
                shapes.setColor(BREAKABLE_EDGE_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case GLASS_PANE -> {
                shapes.setColor(GLASS_EDGE_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
                // A diagonal sheen, the mark that reads as a pane rather than a wall.
                shapes.setColor(GLASS_SHEEN_COLOR);
                shapes.line(rect.left(), rect.bottom(), rect.right(), rect.top());
            }
            case DOOR_PANEL -> {
                shapes.setColor(DOOR_EDGE_COLOR);
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
                // The panel's seam. Its swing angle is authoritative door state, which arrives
                // with the door milestone; until then a door is drawn closed, as it behaves.
                shapes.setColor(DOOR_SEAM_COLOR);
                shapes.line(rect.centerX(), rect.bottom(), rect.centerX(), rect.top());
            }
            case PLATFORM_SLAB -> {
                // Only the landing surface is outlined: the top edge is the whole collider.
                shapes.setColor(PLATFORM_TOP_COLOR);
                shapes.line(rect.left(), rect.top(), rect.right(), rect.top());
            }
        }
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
