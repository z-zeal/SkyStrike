package io.github.skystrike.shared.map;

import java.util.Objects;

/**
 * One immutable piece of authored map geometry.
 *
 * <p>Geometry remains axis-aligned and y-up through {@link Rect}. Behaviour belongs to the
 * {@link BlockRules} row for {@link #kind()}, while {@link #material()} preserves the surface
 * identity needed by later damage, audio and rendering work.
 */
public record MapBlock(BlockKind kind, Material material, Rect rect) {

    public MapBlock {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rect, "rect");
    }

    /** Returns this same block reflected about the given vertical axis. */
    public MapBlock mirror(float axis) {
        return new MapBlock(kind, material, rect.mirror(axis));
    }
}
