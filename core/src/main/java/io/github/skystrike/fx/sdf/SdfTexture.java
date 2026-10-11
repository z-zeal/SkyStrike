package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.sdf.SdfDiff;
import io.github.skystrike.shared.sdf.SdfField;
import java.util.Objects;

/**
 * GPU texture wrapper for the Signed Distance Field.
 *
 * <p>Uploads the encoded 8-bit distance field into a GPU texture with linear filtering and
 * clamp-to-edge wrap mode for hardware-accelerated bilinear distance interpolation.
 *
 * <p>It also owns the second half of the re-bake path (build plan D4): when the map changes, the
 * bake happens on a worker and {@link #patch(SdfField)} uploads only the texels that actually
 * moved. That is why the texture is deliberately unmanaged — a managed texture refuses
 * {@code glTexSubImage2D}, and a region upload is the point.
 */
public final class SdfTexture implements Disposable {

    private SdfField field;
    private final Texture texture;

    public SdfTexture(SdfField field) {
        if (field == null) {
            throw new IllegalArgumentException("field cannot be null");
        }
        this.field = field;

        int width = field.width();
        int height = field.height();
        byte[] raw = field.rawData();

        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int val = raw[y * width + x] & 0xFF;
                // Pack into RGBA (r = val, g = val, b = val, a = 255).
                int rgba = (val << 24) | (val << 16) | (val << 8) | 0xFF;

                // SdfField row zero and OpenGL texture v=0 both represent the bottom of the
                // world. Pixmap's first stored row is uploaded as v=0, despite its drawing API
                // describing y=0 as the top of an image, so flipping here mirrors occluders and
                // makes their shadows appear on the opposite side of the arena.
                pixmap.drawPixel(x, y, rgba);
            }
        }

        this.texture = new Texture(pixmap, false);
        this.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        this.texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        pixmap.dispose();
    }

    /**
     * Adopts a re-baked field, uploading only the texels that differ from the one on the GPU.
     *
     * <p>The bake itself has already happened, on the re-bake worker; what is left is the upload,
     * and it happens on the render thread because it touches the GL context. The changed region is
     * computed here rather than on the worker on purpose: the field on the GPU is the one this
     * patch must be relative to, and it can only be read safely from the thread that owns it.
     *
     * <p>A field on another grid is refused loudly rather than applied: texel {@code (x, y)} of a
     * different grid is not the piece of the world the texture already holds, so there is no patch
     * that means anything. The map's size and the texel scale are fixed for a session, so a field
     * that does not fit this texture is a bug, not a situation to draw through.
     *
     * @return true when texels were uploaded; false when the field was already identical
     */
    public boolean patch(SdfField next) {
        Objects.requireNonNull(next, "next");
        if (next == field) {
            return false;
        }
        if (next.width() != field.width() || next.height() != field.height()) {
            throw new IllegalArgumentException(
                "re-baked SDF is " + next.width() + "x" + next.height()
                    + " but this texture holds " + field.width() + "x" + field.height()
                    + "; the texel grid cannot change while the map size is fixed");
        }

        SdfDiff region = field.diff(next);
        this.field = next;
        if (region.isEmpty()) {
            return false;
        }

        Pixmap patch = new Pixmap(region.width(), region.height(), Pixmap.Format.RGBA8888);
        byte[] raw = next.rawData();
        for (int row = 0; row < region.height(); row++) {
            int rowOffset = (region.y() + row) * next.width() + region.x();
            for (int column = 0; column < region.width(); column++) {
                int val = raw[rowOffset + column] & 0xFF;
                // Same packing and row order as the whole-field upload above: field row zero is
                // texture v=0, so the patch keeps the field's orientation.
                patch.drawPixel(column, row, (val << 24) | (val << 16) | (val << 8) | 0xFF);
            }
        }
        texture.draw(patch, region.x(), region.y());
        patch.dispose();
        return true;
    }

    /** Binds the SDF texture to the given OpenGL texture unit. */
    public void bind(int unit) {
        texture.bind(unit);
    }

    public Texture texture() {
        return texture;
    }

    public SdfField field() {
        return field;
    }

    public float worldWidth() {
        return field.worldWidth();
    }

    public float worldHeight() {
        return field.worldHeight();
    }

    public float texelScale() {
        return field.texelScale();
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
