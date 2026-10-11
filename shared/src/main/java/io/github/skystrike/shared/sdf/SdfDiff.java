package io.github.skystrike.shared.sdf;

/**
 * The one texel rectangle two {@link SdfField}s differ in.
 *
 * <p>A re-bake after a block leaves the map recomputes the whole field — an 8SSEDT distance is a
 * global property of the geometry, so a removed wall can move a texel nowhere near it — but only a
 * small region around the block actually changes, and this is that region. It is the difference
 * between "the texture has new contents, upload all 1.5 million texels" and "the texture has
 * changed here, patch this rectangle", which is what makes a destruction affordable on the frame
 * it happens.
 *
 * <p>The rectangle is the bounding box of the changed texels, so it is the smallest axis-aligned
 * patch that copies the new field over the old one; two identical fields have an empty diff.
 * Texels are addressed the way {@link SdfField} addresses them: {@code x} across the arena,
 * {@code y} up from the bottom, both on the field's own grid.
 *
 * @param x      first changed texel column
 * @param y      first changed texel row, from the bottom
 * @param width  changed columns, zero when nothing changed
 * @param height changed rows, zero when nothing changed
 */
public record SdfDiff(int x, int y, int width, int height) {

    /** The empty diff: two fields agree everywhere and there is nothing to upload. */
    public static final SdfDiff NONE = new SdfDiff(0, 0, 0, 0);

    public SdfDiff {
        if (x < 0 || y < 0 || width < 0 || height < 0) {
            throw new IllegalArgumentException(
                "diff rectangle must not be negative: " + x + ", " + y + ", " + width + ", "
                    + height);
        }
    }

    /** True when nothing changed. */
    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    /** How many texels the patch covers. */
    public int texels() {
        return width * height;
    }
}
