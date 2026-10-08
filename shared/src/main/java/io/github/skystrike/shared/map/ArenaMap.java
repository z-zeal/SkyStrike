package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * The canonical static geometry of the one arena, built straight from mechanics §1.
 *
 * <p>The same rectangles serve collision, rendering and line-of-sight blocking — there is no
 * second, "visual only" copy of the layout anywhere.
 *
 * <p><b>Symmetry.</b> The arena is mirror-symmetric about {@code x = 1500}. Geometry is added
 * either through {@link Builder#mirrored} (one rectangle per side) or {@link Builder#centred}
 * (one rectangle straddling the axis, which asserts its own symmetry), so an asymmetric layout
 * cannot be written by accident.
 *
 * <p><b>Coordinates.</b> Arena dimensions, feature sizes and the M8 access layout are canonical
 * with mechanics §1. The only deliberately provisional dimensions are documented alongside the
 * constants where §9.3 specifies an intent but not an exact extent.
 */
public final class ArenaMap {

    // --- Arena shell -------------------------------------------------------------------------
    private static final float BOUNDARY_THICKNESS = 40f;

    // --- Stepped ramp (five 120x18 platforms, +120 across and +50 up per step) -----------------
    private static final int RAMP_STEPS = 5;
    private static final float RAMP_STEP_WIDTH = 120f;
    private static final float RAMP_STEP_THICKNESS = 18f;
    private static final float RAMP_STEP_RISE = 50f;
    private static final float RAMP_FIRST_X = 120f;
    private static final float RAMP_FIRST_Y = 200f;

    // --- Mid-level traversal lane --------------------------------------------------------------
    private static final float MID_LANE_X = 820f;
    private static final float MID_LANE_Y = 500f;
    private static final float MID_LANE_WIDTH = 250f;
    private static final float MID_LANE_THICKNESS = 18f;

    // --- Two-tier crate stack -------------------------------------------------------------------
    private static final float CRATE_WIDTH = 70f;
    private static final float CRATE_TOP_THICKNESS = 16f;
    private static final float LOWER_CRATE_X = 820f;
    private static final float LOWER_CRATE_HEIGHT = 120f;
    private static final float LOWER_CRATE_TOP_Y = 220f;
    private static final float UPPER_CRATE_X = 890f;
    private static final float UPPER_CRATE_HEIGHT = 160f;
    private static final float UPPER_CRATE_TOP_Y = 260f;

    // --- Lane pillar and tunnel choke support ---------------------------------------------------
    private static final float LANE_PILLAR_X = 1010f;
    private static final float LANE_PILLAR_WIDTH = 24f;
    private static final float LANE_PILLAR_HEIGHT = 260f;
    private static final float TUNNEL_SUPPORT_X = 1070f;
    private static final float TUNNEL_SUPPORT_WIDTH = 22f;
    private static final float TUNNEL_SUPPORT_HEIGHT = 170f;

    // --- Centre room -----------------------------------------------------------------------------
    private static final float ROOM_FLOOR_X = 1120f;
    private static final float ROOM_FLOOR_Y = 280f;
    private static final float ROOM_FLOOR_WIDTH = 760f;
    private static final float ROOM_FLOOR_THICKNESS = 20f;
    private static final float ROOM_WALL_WIDTH = 24f;
    private static final float ROOM_WALL_HEIGHT = 560f;
    private static final float ROOM_DOOR_JAMB_HEIGHT = 60f;
    private static final float ROOM_DOOR_OPENING_HEIGHT = 50f;
    private static final float INNER_PILLAR_X = 1184f;
    private static final float INNER_PILLAR_WIDTH = 16f;
    private static final float INNER_PILLAR_HEIGHT = 130f;
    private static final float CATWALK_X = 1180f;
    private static final float CATWALK_Y = 620f;
    private static final float CATWALK_WIDTH = 640f;
    private static final float CATWALK_THICKNESS = 20f;

    // --- Tunnel-to-room hatches -----------------------------------------------------------------
    private static final float HATCH_CENTER_X = 1240f;
    private static final float HATCH_WIDTH = 90f;
    private static final float HATCH_LEFT = HATCH_CENTER_X - HATCH_WIDTH / 2f;
    private static final float HATCH_RIGHT = HATCH_CENTER_X + HATCH_WIDTH / 2f;

    /**
     * The hatch platform surface is 110 units above the ground, exactly one standing-jump step.
     * Its 18-unit thickness is provisional: §9.3 fixes the 110-unit step but not the slab depth.
     */
    private static final float HATCH_STEP_TOP_Y = WorldConfig.GROUND_HEIGHT + 110f;
    private static final float HATCH_STEP_THICKNESS = 18f;
    private static final float HATCH_STEP_Y = HATCH_STEP_TOP_Y - HATCH_STEP_THICKNESS;

    // --- Tunnel roofs ---------------------------------------------------------------------------
    private static final float TUNNEL_ROOF_X = 1120f;
    private static final float TUNNEL_ROOF_Y = 190f;
    private static final float TUNNEL_ROOF_WIDTH = 180f;
    private static final float TUNNEL_ROOF_THICKNESS = 14f;

    // --- M8 room-to-catwalk access ---------------------------------------------------------------
    private static final float CATWALK_SHELF_X = 1100f;
    private static final float CATWALK_SHELF_Y = 520f;
    private static final float CATWALK_SHELF_WIDTH = 120f;
    private static final float CATWALK_SHELF_THICKNESS = 18f;

    /**
     * Provisional doorway-height landing. §9.3 fixes the doorway and high shelf but not a landing
     * at the doorway's floor; this lets the mid lane enter while standing at y=360 rather than
     * relying on a frame-perfect fall through a 50-unit opening.
     */
    private static final float DOORWAY_LANDING_X = 1000f;
    private static final float DOORWAY_LANDING_WIDTH = 120f;
    private static final float DOORWAY_LANDING_THICKNESS = 18f;
    private static final float DOORWAY_LANDING_Y = ROOM_FLOOR_Y + ROOM_FLOOR_THICKNESS
        + ROOM_DOOR_JAMB_HEIGHT - DOORWAY_LANDING_THICKNESS;

    /**
     * Provisional interior riser. §9.3 gives the shelf but not the missing intermediate footstep;
     * this 100x18 platform makes the room-floor-to-shelf rise 102 units, below the 110-unit jump
     * envelope, without changing the specified doorway or shelf.
     */
    private static final float ROOM_ACCESS_STEP_X = 1220f;
    private static final float ROOM_ACCESS_STEP_Y = 400f;
    private static final float ROOM_ACCESS_STEP_WIDTH = 100f;
    private static final float ROOM_ACCESS_STEP_THICKNESS = 18f;

    // --- Sniper perch ----------------------------------------------------------------------------
    private static final float PERCH_X = 1380f;
    private static final float PERCH_Y = 760f;
    private static final float PERCH_WIDTH = 240f;
    private static final float PERCH_THICKNESS = 18f;
    private static final float PERCH_LADDER_X = 1300f;
    private static final float PERCH_LADDER_Y = 700f;
    private static final float PERCH_LADDER_WIDTH = 100f;
    private static final float PERCH_LADDER_THICKNESS = 16f;

    // --- M8 upper arena --------------------------------------------------------------------------
    /**
     * Provisional extent for §9.3's centred platform at approximately y=1000. It deliberately
     * matches the perch width so its purpose is clear directly above that objective.
     */
    private static final float HIGH_PLATFORM_Y = 1000f;
    private static final float HIGH_PLATFORM_THICKNESS = 18f;

    /**
     * Provisional outer-ledges placement and width; §9.3 fixes their mirrored y≈900 role but not
     * their exact span. They overlap their side's mid lane horizontally for a ≤40% fuel ascent.
     */
    private static final float OUTER_LEDGE_X = 760f;
    private static final float OUTER_LEDGE_Y = 900f;
    private static final float OUTER_LEDGE_WIDTH = 240f;
    private static final float OUTER_LEDGE_THICKNESS = 18f;

    // --- Spawns ---------------------------------------------------------------------------------
    private static final float SPAWN_X = 240f;
    private static final float SPAWN_Y = 180f;

    private final List<Rect> solids;
    private final List<SpawnPoint> spawns;

    private ArenaMap(List<Rect> solids, List<SpawnPoint> spawns) {
        this.solids = List.copyOf(solids);
        this.spawns = List.copyOf(spawns);
    }

    /** Every solid rectangle in the arena, in no particular order. Immutable. */
    public List<Rect> solids() {
        return solids;
    }

    /** One spawn per team, left team first. Immutable. */
    public List<SpawnPoint> spawns() {
        return spawns;
    }

    public float width() {
        return WorldConfig.ARENA_WIDTH;
    }

    public float height() {
        return WorldConfig.ARENA_HEIGHT;
    }

    /** The vertical line the whole layout reflects about. */
    public float mirrorAxisX() {
        return WorldConfig.MIRROR_AXIS_X;
    }

    /** The arena as a single rectangle, for clamping and culling. */
    public Rect bounds() {
        return new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.ARENA_HEIGHT);
    }

    /** Builds the one shipped arena. */
    public static ArenaMap standard() {
        Builder builder = new Builder(WorldConfig.MIRROR_AXIS_X);

        addArenaShell(builder);
        addRamps(builder);
        addMidLanes(builder);
        addCrateStacks(builder);
        addLanePillars(builder);
        addTunnelSupports(builder);
        addCentreRoom(builder);
        addTunnelHatches(builder);
        addTunnelRoofs(builder);
        addCatwalkAccess(builder);
        addSniperPerch(builder);
        addUpperArena(builder);

        SpawnPoint leftSpawn = new SpawnPoint(0, SPAWN_X, SPAWN_Y);
        SpawnPoint rightSpawn = new SpawnPoint(1, WorldConfig.ARENA_WIDTH - SPAWN_X, SPAWN_Y);

        return new ArenaMap(builder.rects, List.of(leftSpawn, rightSpawn));
    }

    /** Ground plane plus the side walls and ceiling that keep everything inside the arena. */
    private static void addArenaShell(Builder builder) {
        builder.centred(new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.GROUND_HEIGHT));
        builder.mirrored(new Rect(
            0f,
            WorldConfig.GROUND_HEIGHT,
            BOUNDARY_THICKNESS,
            WorldConfig.ARENA_HEIGHT - WorldConfig.GROUND_HEIGHT - BOUNDARY_THICKNESS));
        builder.centred(new Rect(
            0f,
            WorldConfig.ARENA_HEIGHT - BOUNDARY_THICKNESS,
            WorldConfig.ARENA_WIDTH,
            BOUNDARY_THICKNESS));
    }

    /** Five stepped platforms per side, climbing from (120, 200) to (600, 400). */
    private static void addRamps(Builder builder) {
        for (int step = 0; step < RAMP_STEPS; step++) {
            builder.mirrored(new Rect(
                RAMP_FIRST_X + step * RAMP_STEP_WIDTH,
                RAMP_FIRST_Y + step * RAMP_STEP_RISE,
                RAMP_STEP_WIDTH,
                RAMP_STEP_THICKNESS));
        }
    }

    /** One mid-level traversal lane per side, bridging the ramp top toward the centre room. */
    private static void addMidLanes(Builder builder) {
        builder.mirrored(new Rect(MID_LANE_X, MID_LANE_Y, MID_LANE_WIDTH, MID_LANE_THICKNESS));
    }

    /** A short crate and a tall crate per side, each with a standable top surface. */
    private static void addCrateStacks(Builder builder) {
        float ground = WorldConfig.GROUND_HEIGHT;
        builder.mirrored(new Rect(LOWER_CRATE_X, ground, CRATE_WIDTH, LOWER_CRATE_HEIGHT));
        builder.mirrored(new Rect(LOWER_CRATE_X, LOWER_CRATE_TOP_Y, CRATE_WIDTH, CRATE_TOP_THICKNESS));
        builder.mirrored(new Rect(UPPER_CRATE_X, ground, CRATE_WIDTH, UPPER_CRATE_HEIGHT));
        builder.mirrored(new Rect(UPPER_CRATE_X, UPPER_CRATE_TOP_Y, CRATE_WIDTH, CRATE_TOP_THICKNESS));
    }

    /** The tall sight-blocking pillar in each side lane. */
    private static void addLanePillars(Builder builder) {
        builder.mirrored(new Rect(
            LANE_PILLAR_X, WorldConfig.GROUND_HEIGHT, LANE_PILLAR_WIDTH, LANE_PILLAR_HEIGHT));
    }

    /** The narrow support that chokes each tunnel mouth. */
    private static void addTunnelSupports(Builder builder) {
        builder.mirrored(new Rect(
            TUNNEL_SUPPORT_X, WorldConfig.GROUND_HEIGHT, TUNNEL_SUPPORT_WIDTH, TUNNEL_SUPPORT_HEIGHT));
    }

    /**
     * The fortified centre: floor sections around two tunnel hatches, doorway jambs, short inner
     * pillars and the roof catwalk. The 50-unit doorway opening is exactly a standing player tall.
     */
    private static void addCentreRoom(Builder builder) {
        float outerFloorWidth = HATCH_LEFT - ROOM_FLOOR_X;
        float centreFloorWidth = ROOM_FLOOR_WIDTH - 2f * outerFloorWidth - 2f * HATCH_WIDTH;
        builder.mirrored(new Rect(
            ROOM_FLOOR_X, ROOM_FLOOR_Y, outerFloorWidth, ROOM_FLOOR_THICKNESS));
        builder.centred(new Rect(
            HATCH_RIGHT, ROOM_FLOOR_Y, centreFloorWidth, ROOM_FLOOR_THICKNESS));

        float roomDeck = ROOM_FLOOR_Y + ROOM_FLOOR_THICKNESS;
        builder.mirrored(new Rect(
            ROOM_FLOOR_X, roomDeck, ROOM_WALL_WIDTH, ROOM_DOOR_JAMB_HEIGHT));
        builder.mirrored(new Rect(
            ROOM_FLOOR_X,
            roomDeck + ROOM_DOOR_JAMB_HEIGHT + ROOM_DOOR_OPENING_HEIGHT,
            ROOM_WALL_WIDTH,
            ROOM_WALL_HEIGHT - ROOM_DOOR_JAMB_HEIGHT - ROOM_DOOR_OPENING_HEIGHT));
        builder.mirrored(new Rect(INNER_PILLAR_X, roomDeck, INNER_PILLAR_WIDTH, INNER_PILLAR_HEIGHT));

        builder.centred(new Rect(CATWALK_X, CATWALK_Y, CATWALK_WIDTH, CATWALK_THICKNESS));
    }

    /**
     * One 90-unit floor opening per side and an exactly 110-unit-high step surface make the ground
     * tunnel a bidirectional route into the room rather than a corridor to nowhere.
     */
    private static void addTunnelHatches(Builder builder) {
        builder.mirrored(new Rect(
            HATCH_LEFT, HATCH_STEP_Y, HATCH_WIDTH, HATCH_STEP_THICKNESS));
    }

    /**
     * The low tunnel roofs retain their cramped character but are split around each hatch, leaving
     * a clear vertical route from the tunnel step to the room-floor opening.
     */
    private static void addTunnelRoofs(Builder builder) {
        float tunnelRoofEnd = TUNNEL_ROOF_X + TUNNEL_ROOF_WIDTH;
        builder.mirrored(new Rect(
            TUNNEL_ROOF_X,
            TUNNEL_ROOF_Y,
            HATCH_LEFT - TUNNEL_ROOF_X,
            TUNNEL_ROOF_THICKNESS));
        builder.mirrored(new Rect(
            HATCH_RIGHT,
            TUNNEL_ROOF_Y,
            tunnelRoofEnd - HATCH_RIGHT,
            TUNNEL_ROOF_THICKNESS));
    }

    /**
     * A stable lane-to-doorway landing plus chained ≤110-unit access from the room floor to the
     * catwalk. The specified shelf extends the mid lane toward the doorway and forms the final
     * step into the catwalk.
     */
    private static void addCatwalkAccess(Builder builder) {
        builder.mirrored(new Rect(
            DOORWAY_LANDING_X,
            DOORWAY_LANDING_Y,
            DOORWAY_LANDING_WIDTH,
            DOORWAY_LANDING_THICKNESS));
        builder.mirrored(new Rect(
            CATWALK_SHELF_X,
            CATWALK_SHELF_Y,
            CATWALK_SHELF_WIDTH,
            CATWALK_SHELF_THICKNESS));
        builder.mirrored(new Rect(
            ROOM_ACCESS_STEP_X,
            ROOM_ACCESS_STEP_Y,
            ROOM_ACCESS_STEP_WIDTH,
            ROOM_ACCESS_STEP_THICKNESS));
    }

    /** The high centre perch and its mirrored 60-unit ladder step from the catwalk. */
    private static void addSniperPerch(Builder builder) {
        builder.centred(new Rect(PERCH_X, PERCH_Y, PERCH_WIDTH, PERCH_THICKNESS));
        builder.mirrored(new Rect(
            PERCH_LADDER_X,
            PERCH_LADDER_Y,
            PERCH_LADDER_WIDTH,
            PERCH_LADDER_THICKNESS));
    }

    /** A high centre objective and a jetpack-only outer ledge on each side occupy the upper half. */
    private static void addUpperArena(Builder builder) {
        builder.centred(new Rect(PERCH_X, HIGH_PLATFORM_Y, PERCH_WIDTH, HIGH_PLATFORM_THICKNESS));
        builder.mirrored(new Rect(
            OUTER_LEDGE_X,
            OUTER_LEDGE_Y,
            OUTER_LEDGE_WIDTH,
            OUTER_LEDGE_THICKNESS));
    }

    /** Collects geometry while enforcing mirror symmetry at construction time. */
    private static final class Builder {

        private final List<Rect> rects = new ArrayList<>();
        private final float axis;

        private Builder(float axis) {
            this.axis = axis;
        }

        /** Adds {@code rect} on one side and its reflection on the other. */
        void mirrored(Rect rect) {
            Rect reflection = rect.mirror(axis);
            if (reflection.equals(rect)) {
                throw new IllegalArgumentException(
                    "rect straddles the mirror axis, use centred(): " + rect);
            }
            rects.add(rect);
            rects.add(reflection);
        }

        /** Adds a rectangle that is its own reflection, failing loudly if it is not. */
        void centred(Rect rect) {
            Rect reflection = rect.mirror(axis);
            if (!reflection.equals(rect)) {
                throw new IllegalArgumentException(
                    "rect is not symmetric about x=" + axis + ": " + rect);
            }
            rects.add(rect);
        }
    }
}
