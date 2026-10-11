package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable authored geometry for one symmetric arena.
 *
 * <p>A map owns {@link MapBlock blocks}; their shared {@link BlockRules} rows decide what each
 * consumer sees. {@link #solids()} remains a compatibility view for existing bullet-blocking
 * callers while those callers migrate to their specific rule columns. The shipped arena contains
 * only wall blocks today, so that view is geometrically identical to the old rect-only map.
 *
 * <p>Use {@link Builder#mirrored(MapBlock)} or {@link Builder#centred(MapBlock)} to preserve the
 * fairness invariant that every authored block has a reflection about {@link #mirrorAxisX()}.
 */
public final class ArenaMap {

    private final float mirrorAxisX;
    private final List<MapBlock> blocks;
    private final List<Rect> solids;
    private final List<SpawnPoint> spawns;
    private final byte[] mapHash;

    private ArenaMap(float mirrorAxisX, List<MapBlock> blocks, List<SpawnPoint> spawns) {
        validateMirrorAxis(mirrorAxisX);
        this.mirrorAxisX = mirrorAxisX;
        this.blocks = List.copyOf(blocks);
        this.spawns = List.copyOf(spawns);

        List<Rect> bulletBlockers = new ArrayList<>();
        for (MapBlock block : this.blocks) {
            if (BlockRules.blocks(block.kind(), Blocking.BULLETS)) {
                bulletBlockers.add(block.rect());
            }
        }
        this.solids = List.copyOf(bulletBlockers);
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
     * Compatibility view of rectangles whose intact/closed kind blocks bullets. Immutable.
     *
     * <p>This preserves the legacy rect API while query, movement, rendering and SDF consumers
     * migrate to their own {@link BlockRules} column. It must not be used as a universal meaning
     * of "solid" for a newly introduced block kind.
     */
    public List<Rect> solids() {
        return solids;
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
