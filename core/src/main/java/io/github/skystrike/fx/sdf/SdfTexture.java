package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.sdf.SdfField;

/**
 * GPU texture wrapper for the Signed Distance Field.
 *
 * <p>Uploads the encoded 8-bit distance field into a GPU texture with linear filtering and
 * clamp-to-edge wrap mode for hardware-accelerated bilinear distance interpolation.
 */
public final class SdfTexture implements Disposable {

    private final SdfField field;
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
