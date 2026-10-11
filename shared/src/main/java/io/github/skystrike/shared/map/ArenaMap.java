package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable authored geometry for one symmetric arena.
 *
 * <p>A map owns {@link MapBlock blocks}; their shared {@link BlockRules} rows decide what each
 * consumer sees. {@link #blockers(Blocking)} exposes those rows as one cached rectangle list per
 * query purpose, {@link #movementBlockers(MovementRule)} does the same for the movement column
 * (which decides which faces stop a body, not whether a line is blocked), and {@link #solids()}
 * remains a compatibility view for existing bullet-blocking callers while those callers migrate to
 * their specific rule columns. The shipped arena contains only wall blocks today, so every view is
 * geometrically identical to the old rect-only map.
 *
 * <p>Use {@link Builder#mirrored(MapBlock)} or {@link Builder#centred(MapBlock)} to preserve the
 * fairness invariant that every authored block has a reflection about {@link #mirrorAxisX()}.
 */
public final class ArenaMap {

    private final float mirrorAxisX;
    private final List<MapBlock> blocks;
    private final Map<Blocking, List<Rect>> blockers;
    private final Map<MovementRule, List<Rect>> movementViews;
    private final List<SpawnPoint> spawns;
    private final byte[] mapHash;

    private ArenaMap(float mirrorAxisX, List<MapBlock> blocks, List<SpawnPoint> spawns) {
        validateMirrorAxis(mirrorAxisX);
        this.mirrorAxisX = mirrorAxisX;
        this.blocks = List.copyOf(blocks);
        this.spawns = List.copyOf(spawns);

        // One cached view per query purpose, still derived from the one rule table: the rectangles
        // whose row answers that purpose, in authored order. Consumers that sweep every segment of
        // a frame (the bullet raycaster, line queries) then pay the rule lookup once per block here
        // rather than once per rect per query, and no consumer has to know a kind by name.
        EnumMap<Blocking, List<Rect>> views = new EnumMap<>(Blocking.class);
        for (Blocking purpose : Blocking.values()) {
            List<Rect> rects = new ArrayList<>();
            for (MapBlock block : this.blocks) {
                if (BlockRules.blocks(block.kind(), purpose)) {
                    rects.add(block.rect());
                }
            }
            views.put(purpose, List.copyOf(rects));
        }
        this.blockers = views;

        // The movement column gets the same treatment, one view per rule: a line query can ask
        // "does this kind block this purpose", but only the movement rule says which faces of the
        // block stop a body, so the sweeper cannot read a Blocking view for it.
        EnumMap<MovementRule, List<Rect>> movement = new EnumMap<>(MovementRule.class);
        for (MovementRule rule : MovementRule.values()) {
            List<Rect> rects = new ArrayList<>();
            for (MapBlock block : this.blocks) {
                if (BlockRules.movement(block.kind()) == rule) {
                    rects.add(block.rect());
                }
            }
            movement.put(rule, List.copyOf(rects));
        }
        this.movementViews = movement;

        this.mapHash = MapHash.of(this);
    }

    /**
     * Stable SHA-256 identity for the authored map contents. The returned array is a copy.
     *
     * <p>It is carried by derived data such as the SDF, never by the mutable legacy rect view.
     */
    public byte[] mapHash() {
        return mapHash.clone();
    }

    /** Every authored map block, in authored order. Immutable. */
    public List<MapBlock> blocks() {
        return blocks;
    }

    /**
     * Every authored block whose shared {@link BlockRules} row blocks {@code purpose}, as
     * rectangles in authored order. Immutable.
     *
     * <p>This is the set a rule-aware query sweeps. It is derived data, computed once when the map
     * is built: the rule table stays the only place that decides whether a kind blocks a purpose.
     */
    public List<Rect> blockers(Blocking purpose) {
        Objects.requireNonNull(purpose, "purpose");
        return blockers.get(purpose);
    }

    /**
     * Compatibility view of rectangles whose intact/closed kind blocks bullets. Immutable.
     *
     * <p>Identical to {@code blockers(Blocking.BULLETS)}; it preserves the legacy rect API while
     * query, movement, rendering and SDF consumers migrate to their own {@link BlockRules} column.
     * It must not be used as a universal meaning of "solid" for a newly introduced block kind.
     */
    public List<Rect> solids() {
        return blockers(Blocking.BULLETS);
    }

    /**
     * Every authored block whose shared {@link BlockRules} row carries {@code rule}, as rectangles
     * in authored order. Immutable.
     *
     * <p>The movement column is not a {@link Blocking} purpose: it says which *faces* may stop a
     * body, not whether a kind answers a line query. The shared sweeper therefore sweeps
     * {@link MovementRule#SOLID} for walls, floors and ceilings, and {@link MovementRule#ONE_WAY_TOP}
     * for landings alone; a renderer or device that means "geometry that stops a body" reads this
     * view rather than naming a kind.
     */
    public List<Rect> movementBlockers(MovementRule rule) {
        Objects.requireNonNull(rule, "rule");
        return movementViews.get(rule);
    }

    /** Spawn positions in authored order. Immutable. */
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
        return mirrorAxisX;
    }

    /** The arena as a single rectangle, for clamping and culling. */
    public Rect bounds() {
        return new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.ARENA_HEIGHT);
    }

    /**
     * Loads the canonical shipped arena through the versioned text-map parser.
     *
     * <p>The asset move to {@code assets/maps/arena.map} is a later shipped-maps milestone. Until
     * then the exact same reviewable format is embedded in {@link StandardArenaMap} so this shared
     * module can load it without a platform file API.
     */
    public static ArenaMap standard() {
        return StandardHolder.MAP;
    }

    /** Starts a symmetric map builder for the given in-bounds mirror axis. */
    public static Builder builder(float mirrorAxisX) {
        return new Builder(mirrorAxisX);
    }

    private static void validateMirrorAxis(float mirrorAxisX) {
        if (!Float.isFinite(mirrorAxisX)
                || mirrorAxisX <= 0f
                || mirrorAxisX >= WorldConfig.ARENA_WIDTH) {
            throw new IllegalArgumentException(
                "mirror axis must lie inside arena bounds: " + mirrorAxisX);
        }
    }

    private static final class StandardHolder {
        private static final ArenaMap MAP = ArenaMapFormat.parse(StandardArenaMap.TEXT);
    }

    /** Collects blocks while enforcing reflection symmetry and rejecting duplicate geometry. */
    public static final class Builder {

        private final float mirrorAxisX;
        private final List<MapBlock> blocks = new ArrayList<>();
        private final Set<MapBlock> uniqueBlocks = new HashSet<>();
        private final List<SpawnPoint> spawns = new ArrayList<>();

        private Builder(float mirrorAxisX) {
            validateMirrorAxis(mirrorAxisX);
            this.mirrorAxisX = mirrorAxisX;
        }

        /** Adds {@code block} on one side and its reflection on the other. */
        public Builder mirrored(MapBlock block) {
            Objects.requireNonNull(block, "block");
            MapBlock reflection = block.mirror(mirrorAxisX);
            if (reflection.equals(block)) {
                throw new IllegalArgumentException(
                    "block straddles the mirror axis, use centred(): " + block.rect());
            }
            add(block);
            add(reflection);
            return this;
        }

        /** Adds a block that is its own reflection, failing loudly if it is not. */
        public Builder centred(MapBlock block) {
            Objects.requireNonNull(block, "block");
            MapBlock reflection = block.mirror(mirrorAxisX);
            if (!reflection.equals(block)) {
                throw new IllegalArgumentException(
                    "block is not symmetric about x=" + mirrorAxisX + ": " + block.rect());
            }
            add(block);
            return this;
        }

        /** Adds a spawn in authored order. */
        public Builder spawn(SpawnPoint spawn) {
            spawns.add(Objects.requireNonNull(spawn, "spawn"));
            return this;
        }

        /** Builds the immutable map. Format readers additionally validate source line context. */
        public ArenaMap build() {
            return new ArenaMap(mirrorAxisX, blocks, spawns);
        }

        private void add(MapBlock block) {
            if (!uniqueBlocks.add(block)) {
                throw new IllegalArgumentException("duplicate map block: " + block);
            }
            blocks.add(block);
        }
    }
}
