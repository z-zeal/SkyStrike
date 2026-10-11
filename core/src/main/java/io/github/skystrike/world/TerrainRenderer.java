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
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MaterialTable;
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
 * compile-time omission rather than a silently invisible block. Colours are the same arrangement
 * one level down: a surface's fill and outline are its {@link MaterialTable} row's packed
 * colours, and only treatment accents — the pane's sheen, the door's seam, the platform's landing
 * edge — live in core. That is what keeps LibGDX out of shared.
 *
 * <p>Every draw pass is one loop over the whole block list, so the geometry cannot drift between
 * the fill and the outline of the same block. Which block list is a constructor argument: with a
 * {@link MapDamageState} the picture follows the map the server is actually playing on — a
 * destroyed block is not drawn and a damaged one shows its cracks — and without one it draws the
 * authored map, which is the only thing a client that has not been told anything can honestly
 * draw.
 */
public final class TerrainRenderer implements Disposable {

    // Two layers of colour. A surface's fill and outline come from its material row in the shared
    // MaterialTable, so metal, stone and wood read as themselves and the shipped arena — a stone
    // wall — draws exactly the slate it always did. What a treatment adds on top is its accent: a
    // one-way slab is a slab, not a wall, so it keeps its own colour and highlights only its top
    // edge, because that edge is the surface a body lands on; glass keeps its pane sheen; and a
    // door is drawn as the closed panel with its seam, a treatment accent until the swing angle
    // arrives with the authoritative door state.
    private static final Color GLASS_SHEEN_COLOR = new Color(0.80f, 0.97f, 1f, 0.55f);
    private static final Color DOOR_FILL_COLOR = new Color(0.40f, 0.33f, 0.24f, 1f);
    private static final Color DOOR_EDGE_COLOR = new Color(0.64f, 0.53f, 0.36f, 1f);
    private static final Color DOOR_SEAM_COLOR = new Color(0.22f, 0.18f, 0.13f, 1f);
    private static final Color PLATFORM_FILL_COLOR = new Color(0.26f, 0.30f, 0.27f, 1f);
    private static final Color PLATFORM_TOP_COLOR = new Color(0.62f, 0.82f, 0.52f, 1f);

    // The crack overlay's ink: near-black, so it reads as damage on any material's fill.
    private static final Color CRACK_COLOR = new Color(0.09f, 0.07f, 0.05f, 0.78f);
    /** Strokes per cracked block: enough to read as damage, few enough to stay a mark. */
    private static final int CRACK_STROKES = 3;

    private static final Color ARENA_EDGE = new Color(0.16f, 0.20f, 0.27f, 1f);
    private static final Color SPAWN_LEFT = new Color(0.30f, 0.62f, 0.95f, 1f);
    private static final Color SPAWN_RIGHT = new Color(0.95f, 0.45f, 0.30f, 1f);

    private static final float SPAWN_MARKER_RADIUS = 18f;

    private final ArenaMap map;
    /** The client's copy of the round's destruction state, or null until it has one. */
    private final MapDamageState damage;
    private final ShapeRenderer shapes = new ShapeRenderer();

    /** Scratch for a material's packed colour, so drawing a block allocates nothing. */
    private final Color materialColour = new Color();

    public TerrainRenderer(ArenaMap map) {
        this(map, null);
    }

    /**
     * @param map    the authored map, which the picture is drawn from when {@code damage} is null
     * @param damage the client's copy of the round's destruction state, or null when it has none;
     *               the wire carries it with the protocol-14 block events
     */
    public TerrainRenderer(ArenaMap map, MapDamageState damage) {
        if (map == null) {
            throw new IllegalArgumentException("map is required");
        }
        this.map = map;
        this.damage = damage;
    }

    /** The map being drawn: the live one whenever the client has a destruction state. */
    private ArenaMap liveMap() {
        return damage == null ? map : damage.liveMap();
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
        for (MapBlock block : liveMap().blocks()) {
            drawFill(block);
        }
        drawSpawnMarkers();
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (MapBlock block : liveMap().blocks()) {
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
     * <p>A {@code BREAKABLE_FILL} block is drawn in its material's colours and then cracked in
     * proportion to the damage it has taken. Without a destruction state there is no damage to
     * draw, so a client that has not been told anything draws a pristine crate — cracks nothing
     * can compute would be a damage readout that lies.
     */
    private void drawFill(MapBlock block) {
        Rect rect = block.rect();
        switch (BlockRules.renderStyle(block.kind())) {
            case SOLID_FILL -> {
                shapes.setColor(materialColour(block));
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case BREAKABLE_FILL -> {
                shapes.setColor(materialColour(block));
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
                drawCracks(block);
            }
            case GLASS_PANE -> {
                shapes.setColor(materialColour(block));
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
                shapes.setColor(materialLine(block));
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case BREAKABLE_FILL -> {
                shapes.setColor(materialLine(block));
                shapes.rect(rect.x(), rect.y(), rect.width(), rect.height());
            }
            case GLASS_PANE -> {
                shapes.setColor(materialLine(block));
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

    /**
     * The crack overlay of one breakable: how much of its durability pool is spent, drawn so the
     * answer can be read without a number.
     *
     * <p>Three strokes fan out from the block's middle with a reach that grows with the fraction,
     * at angles fixed by the block's own rectangle: the same block always cracks the same way, so
     * a client watching it take hits sees the cracks grow rather than twitch. Nothing is drawn for
     * an undamaged block, and nothing at all without a destruction state.
     */
    private void drawCracks(MapBlock block) {
        if (damage == null) {
            return;
        }
        float fraction = damage.damageFraction(block);
        if (fraction <= 0f) {
            return;
        }
        Rect rect = block.rect();
        float centreX = rect.centerX();
        float centreY = rect.centerY();
        float reach = 0.5f * Math.min(rect.width(), rect.height()) * fraction;
        int seed = 31 * Float.floatToIntBits(rect.x()) + Float.floatToIntBits(rect.y());
        shapes.setColor(CRACK_COLOR);
        for (int stroke = 0; stroke < CRACK_STROKES; stroke++) {
            float angle = (float) Math.toRadians(Math.floorMod(seed + stroke * 97, 360));
            float dx = (float) Math.cos(angle) * reach;
            float dy = (float) Math.sin(angle) * reach;
            shapes.line(centreX - dx, centreY - dy, centreX + dx, centreY + dy);
        }
    }

    /**
     * The surface fill colour of one block: its material's packed RGBA8888 fill, from the shared
     * table. This is the one call that makes a metal wall and a stone wall different objects
     * without core hardcoding either.
     */
    private Color materialColour(MapBlock block) {
        return materialColour.set(MaterialTable.profileFor(block.material()).tintRgba());
    }

    /** The surface outline colour of one block: its material's packed RGBA8888 line. */
    private Color materialLine(MapBlock block) {
        return materialColour.set(MaterialTable.profileFor(block.material()).lineRgba());
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
