package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.Collection;
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
 * their specific rule columns. A door also carries an authored {@link DoorSpec}, which is the one
 * piece of authored data a block's rectangle cannot express; which doors are currently in the way
 * is runtime state and belongs to {@link MapDoorState}, not here.
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

    /** The map hash as lower-case hexadecimal — the form logs and error messages quote. */
    public String mapHashHex() {
        return MapHash.hex(mapHash);
    }

    /**
     * One-line identity of this map for startup logs and operator dumps: the block census by
     * kind, the mirror axis and the map hash.
     *
     * <p>The reason this exists is the first live run of the milestone-3 fixture range: findings
     * that read as physics bugs — a pane that neither stops a body nor shatters — are exactly
     * what a client and a server running different maps produce, and the ordinary startup line's
     * bare block count cannot tell a stale process from a current one. Both sides now announce
     * the map they actually parsed, so a mismatch is one log line apart. The census is computed
     * here, inside {@code shared/map}, because no consumer outside this package may name a block
     * kind — a census every consumer can print is also the one string that cannot drift from the
     * rule table.
     */
    public String describe() {
        Map<BlockKind, Integer> census = new EnumMap<>(BlockKind.class);
        for (MapBlock block : blocks) {
            census.merge(block.kind(), 1, Integer::sum);
        }
        StringBuilder text = new StringBuilder(blocks.size() * 12);
        text.append(blocks.size()).append(" blocks [");
        boolean first = true;
        for (Map.Entry<BlockKind, Integer> entry : census.entrySet()) {
            if (!first) {
                text.append(", ");
            }
            text.append(entry.getKey()).append(' ').append(entry.getValue());
            first = false;
        }
        text.append("], mirror x=").append(mirrorAxisX);
        text.append(", map ").append(MapHash.hex(mapHash));
        return text.toString();
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
     * <p>The movement column is not a {@link Blocking} purpose: it says which <b>faces</b> may
     * stop a body, not whether a kind answers a line query. The shared sweeper therefore sweeps
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

    /**
     * The same map with {@code removed} blocks taken out — the live view of a map after
     * destruction.
     *
     * <p>Deliberately separate from {@link Builder}: the builder's symmetry assertions are a rule
     * for <b>authoring</b>, and runtime destruction is not authoring. Shooting the left-hand window
     * must not shatter the right-hand one, so a live map is asymmetric by design while the authored
     * map it came from stays symmetric. {@link ArenaMapFormat#write(ArenaMap)} still refuses to save
     * such a map, which is the honest outcome: a match's damage is not a map edit.
     *
     * <p>The result is an ordinary immutable {@link ArenaMap} with its own cached rule views and its
     * own {@link #mapHash()} over the surviving contents — which is the map a re-baked SDF names,
     * and why that hash stops matching the authored one as soon as something is destroyed.
     *
     * @param removed blocks to leave out; a block that is not in this map is ignored
     * @return this map when nothing actually matched, otherwise a derived map
     */
    public ArenaMap without(Collection<MapBlock> removed) {
        Objects.requireNonNull(removed, "removed");
        if (removed.isEmpty()) {
            return this;
        }
        Set<MapBlock> gone = new HashSet<>(removed);
        List<MapBlock> survivors = new ArrayList<>(blocks.size());
        for (MapBlock block : blocks) {
            if (!gone.contains(block)) {
                survivors.add(block);
            }
        }
        if (survivors.size() == blocks.size()) {
            return this;
        }
        return new ArenaMap(mirrorAxisX, survivors, spawns);
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
        /** Every authored door id, so a door cannot be named twice. */
        private final Set<Integer> doorIds = new HashSet<>();
        private int doorCount;
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

        /**
         * Builds the immutable map. Format readers additionally validate source line context.
         *
         * <p>A map may not author more doors than the visibility and light shaders can occlude
         * (plan D3, {@link VisionConfig#MAX_DOOR_OCCLUDERS}): the occluder array exists precisely
         * so a closed door shades its own doorway, and a door that fell off the end of it would be
         * a door that blocks a body and nothing else. Failing the load is the only honest
         * outcome — a map that only mostly occludes is worse than one that refuses to load.
         */
        public ArenaMap build() {
            if (doorCount > VisionConfig.MAX_DOOR_OCCLUDERS) {
                throw new MapFormatException(
                    "map authors " + doorCount + " doors, but at most "
                        + VisionConfig.MAX_DOOR_OCCLUDERS
                        + " can occlude (VisionConfig.MAX_DOOR_OCCLUDERS)");
            }
            return new ArenaMap(mirrorAxisX, blocks, spawns);
        }

        private void add(MapBlock block) {
            if (!uniqueBlocks.add(block)) {
                throw new IllegalArgumentException("duplicate map block: " + block);
            }
            if (block.door().isDoor()) {
                // A door's id is its identity on the wire, so two doors sharing one would make the
                // door-state packet ambiguous. The reflection of a mirrored door takes the next id
                // (DoorSpec.reflection), which is how this can fail at all: a hand-written file
                // authoring the same id on two doors, or a mirrored pair whose reflection collides
                // with the next door authored.
                if (!doorIds.add(block.door().id())) {
                    throw new IllegalArgumentException("duplicate door id: " + block.door().id());
                }
                doorCount++;
            }
            blocks.add(block);
        }
    }
}
