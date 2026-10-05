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
 * <p><b>Coordinates taken verbatim from the mechanics plan:</b> arena size, ground height, every
 * feature's width/height, the ramp's start and end, all platform heights, and both spawns.
 * <b>Chosen here</b> (the plan fixes the sizes but not the lateral placement): the x positions of
 * the mid lane, the crate stack, the lane pillar and the tunnel choke support; and the fact that
 * the centre room's shell walls stand on the room floor rather than on the ground, which is what
 * leaves the ground-level tunnel underneath the room open.
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
    private static final float INNER_PILLAR_X = 1184f;
    private static final float INNER_PILLAR_WIDTH = 16f;
    private static final float INNER_PILLAR_HEIGHT = 130f;
    private static final float CATWALK_X = 1180f;
    private static final float CATWALK_Y = 620f;
    private static final float CATWALK_WIDTH = 640f;
    private static final float CATWALK_THICKNESS = 20f;
    private static final float TUNNEL_ROOF_X = 1120f;
    private static final float TUNNEL_ROOF_Y = 190f;
    private static final float TUNNEL_ROOF_WIDTH = 180f;
    private static final float TUNNEL_ROOF_THICKNESS = 14f;

    // --- Sniper perch ------------------------------------------------------------------------------
    private static final float PERCH_X = 1380f;
    private static final float PERCH_Y = 760f;
    private static final float PERCH_WIDTH = 240f;
    private static final float PERCH_THICKNESS = 18f;

    // --- Spawns ---------------------------------------------------------------------------------------
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
        addTunnelRoofs(builder);
        addSniperPerch(builder);

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
     * The fortified centre: a floor, the two shell walls standing on it, the short inner pillars at
     * the entrances, and the roof catwalk.
     */
    private static void addCentreRoom(Builder builder) {
        builder.centred(new Rect(ROOM_FLOOR_X, ROOM_FLOOR_Y, ROOM_FLOOR_WIDTH, ROOM_FLOOR_THICKNESS));

        float roomDeck = ROOM_FLOOR_Y + ROOM_FLOOR_THICKNESS;
        builder.mirrored(new Rect(ROOM_FLOOR_X, roomDeck, ROOM_WALL_WIDTH, ROOM_WALL_HEIGHT));
        builder.mirrored(new Rect(INNER_PILLAR_X, roomDeck, INNER_PILLAR_WIDTH, INNER_PILLAR_HEIGHT));

        builder.centred(new Rect(CATWALK_X, CATWALK_Y, CATWALK_WIDTH, CATWALK_THICKNESS));
    }

    /** Two low roof segments under the room floor, making the ground-level flank cramped. */
    private static void addTunnelRoofs(Builder builder) {
        builder.mirrored(new Rect(
            TUNNEL_ROOF_X, TUNNEL_ROOF_Y, TUNNEL_ROOF_WIDTH, TUNNEL_ROOF_THICKNESS));
    }

    /** The single high platform above the centre room. */
    private static void addSniperPerch(Builder builder) {
        builder.centred(new Rect(PERCH_X, PERCH_Y, PERCH_WIDTH, PERCH_THICKNESS));
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
