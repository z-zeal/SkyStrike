package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.weapons.HeldWeaponGeometry;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.HashMap;
import java.util.Map;

/**
 * Held-weapon art: the 512×512 product-shot sprites of {@code assets/sprites/guns} and
 * {@code assets/sprites/melee}.
 *
 * <p>Every sprite is fitted to its frame — a pistol fills the PNG exactly as much as a rifle —
 * so drawing without the per-weapon scale would hand every gun the same silhouette size. The
 * scale is {@link HeldWeaponGeometry}, in {@code shared}, because the drawn barrel is also what
 * the server aims its rounds and muzzle effects at: one implementation of the geometry, one
 * sprite, one muzzle point.
 *
 * <p>Textures load lazily by wire id and are cached, including failures (a missing file is
 * remembered as {@code null} so the renderer can fall back to the plain barrel line instead of
 * retrying every frame). All loading happens on the render thread.
 */
public final class WeaponSprites implements Disposable {

    private final Map<String, Texture> textures = new HashMap<>();

    public WeaponSprites() {
    }

    /** The sprite for whatever {@code wireId} names, or {@code null} when the art is missing. */
    public Texture texture(int wireId) {
        return load(path(wireId));
    }

    /** True when {@code wireId} has loadable art, without logging a second failure. */
    public boolean hasTexture(int wireId) {
        return load(path(wireId)) != null;
    }

    /** Frame size in world units for {@code wireId}, scale catalog and global size applied. */
    public float frameSize(int wireId) {
        return HeldWeaponGeometry.frameSize(wireId);
    }

    /** Grip anchor for {@code wireId}, as a fraction of the frame width. */
    public float gripAnchorX(int wireId) {
        return HeldWeaponGeometry.gripAnchorX(wireId);
    }

    private static String path(int wireId) {
        if (MeleeId.isMeleeWireId(wireId)) {
            return "sprites/melee/" + MeleeId.fromWireId(wireId).assetId() + ".png";
        }
        return "sprites/guns/" + WeaponId.fromOrdinal(wireId).assetId() + ".png";
    }

    private Texture load(String path) {
        if (textures.containsKey(path)) {
            return textures.get(path);
        }
        Texture texture = null;
        try {
            texture = new Texture(Gdx.files.internal(path), true);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        } catch (Exception e) {
            Gdx.app.error("WeaponSprites", "missing weapon sprite: " + path);
        }
        textures.put(path, texture);
        return texture;
    }

    @Override
    public void dispose() {
        for (Texture texture : textures.values()) {
            if (texture != null) {
                texture.dispose();
            }
        }
        textures.clear();
    }
}
