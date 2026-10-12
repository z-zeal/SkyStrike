package io.github.skystrike.shared.map;

import java.util.Objects;

/**
 * One immutable piece of authored map geometry.
 *
 * <p>Geometry remains axis-aligned and y-up through {@link Rect}. Behaviour belongs to the
 * {@link BlockRules} row for {@link #kind()}, the surface's numbers to the {@link MaterialTable}
 * row for {@link #material()}, and {@link #durabilityMultiplier()} scales that material's
 * durability so two blocks of the same material can still take a different number of hits.
 *
 * <p>{@link #door()} is the authoring half of a door: the id that names it on the wire and the
 * access rule that decides who may open it (plan §4, §8). The <i>runtime</i> half — which state a
 * door is in — belongs to {@link MapDoorState}, for the same reason damage does: it changes
 * during a round while the map does not.
 *
 * <p>The multiplier is authored data, not runtime damage: the damage a block has already taken
 * lives in the per-round destruction state, because it changes while the map does not.
 */
public record MapBlock(
        BlockKind kind, Material material, Rect rect, float durabilityMultiplier, DoorSpec door) {

    /** The multiplier a block has when the map text does not say otherwise. */
    public static final float DEFAULT_DURABILITY_MULTIPLIER = 1f;

    public MapBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rect, "rect");
        Objects.requireNonNull(door, "door");
        if (!Float.isFinite(durabilityMultiplier) || durabilityMultiplier <= 0f) {
            throw new IllegalArgumentException(
                "durability multiplier must be finite and positive: " + durabilityMultiplier);
        }
        // A door is the one kind whose authored identity is not its geometry, so it is the one kind
        // that must carry one. Refusing the mismatch here means no later consumer has to ask
        // whether a DOOR without an id could exist, and no id can ride along on a wall.
        if (kind == BlockKind.DOOR && !door.isDoor()) {
            throw new IllegalArgumentException("a DOOR block must carry a door id: " + rect);
        }
        if (kind != BlockKind.DOOR && door != DoorSpec.NONE) {
            throw new IllegalArgumentException(
                "only a DOOR block may carry a door spec: " + kind + " " + rect);
        }
    }

    /** A block with the default multiplier and no door spec: the pre-door, pre-multiplier form. */
    public MapBlock(BlockKind kind, Material material, Rect rect) {
        this(kind, material, rect, DEFAULT_DURABILITY_MULTIPLIER, DoorSpec.NONE);
    }

    /** A block with an authored durability multiplier and no door spec. */
    public MapBlock(BlockKind kind, Material material, Rect rect, float durabilityMultiplier) {
        this(kind, material, rect, durabilityMultiplier, DoorSpec.NONE);
    }

    /**
     * Returns this block's reflection — its <i>twin</i> on the other side of the axis.
     *
     * <p>A reflected door is a second door (see {@link DoorSpec#reflection()}), so the reflection
     * carries the next id rather than the same one. A block that is its own reflection is therefore
     * never built this way: a door centred on the axis is one door with one id, authored once, and
     * the self-reflection checks (the builder's {@code centred}, the canonical writer) compare
     * rectangles rather than calling this.
     */
    public MapBlock mirror(float axis) {
        return new MapBlock(kind, material, rect.mirror(axis), durabilityMultiplier, door.reflection());
    }

    /**
     * True when the map authored a multiplier other than the default.
     *
     * <p>The canonical writer and {@link MapHash} both ask this instead of comparing the float
     * themselves, so a default multiplier stays unspoken in the saved text and in the map's
     * identity. That is what keeps the shipped arena's hash — and the baked SDF asset that names
     * it — unchanged by the introduction of this field.
     */
    public boolean hasAuthoredDurabilityMultiplier() {
        return durabilityMultiplier != DEFAULT_DURABILITY_MULTIPLIER;
    }
}
