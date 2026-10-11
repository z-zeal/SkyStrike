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
 * <p>The multiplier is authored data, not runtime damage: the damage a block has already taken
 * lives in the per-round destruction state, because it changes while the map does not.
 */
public record MapBlock(BlockKind kind, Material material, Rect rect, float durabilityMultiplier) {

    /** The multiplier a block has when the map text does not say otherwise. */
    public static final float DEFAULT_DURABILITY_MULTIPLIER = 1f;

    public MapBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rect, "rect");
        if (!Float.isFinite(durabilityMultiplier) || durabilityMultiplier <= 0f) {
            throw new IllegalArgumentException(
                "durability multiplier must be finite and positive: " + durabilityMultiplier);
        }
    }

    /** A block with the default multiplier: the form every block of every pre-multiplier map has. */
    public MapBlock(BlockKind kind, Material material, Rect rect) {
        this(kind, material, rect, DEFAULT_DURABILITY_MULTIPLIER);
    }

    /** Returns this same block reflected about the given vertical axis. */
    public MapBlock mirror(float axis) {
        return new MapBlock(kind, material, rect.mirror(axis), durabilityMultiplier);
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
