package io.github.skystrike.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.HashMap;
import java.util.Map;

/**
 * Held-weapon art: the 512×512 product-shot sprites of {@code assets/sprites/guns} and
 * {@code assets/sprites/melee}, plus the per-weapon draw scale of the companion
 * {@code *-scale(s).json} files.
 *
 * <p>Every sprite is fitted to its frame — a pistol fills the PNG exactly as much as a rifle —
 * so drawing without the catalog's scale would hand every gun the same silhouette size. The
 * scale files quote a stylised multiplier against the reference carbine (readable, not 1:1
 * real-world, where a pistol would be a speck); this class turns that multiplier into world
 * units against {@link #GUN_FRAME_UNITS} and the global {@link #WEAPON_SIZE_MULTIPLIER}.
 *
 * <p>Textures load lazily by wire id and are cached, including failures (a missing file is
 * remembered as {@code null} so the renderer can fall back to the plain barrel line instead of
 * retrying every frame). All loading happens on the render thread.
 */
public final class WeaponSprites implements Disposable {

    /**
     * TUNE HERE — global readability multiplier applied to every held weapon, on top of the
     * per-weapon catalog scale. Guns and melee scale together, so relative sizes are preserved
     * (a zweihänder still dwarfs a pocket knife), and since the grip anchor is a fraction of the
     * frame the grip stays on the hand at any value. 1.0 is the catalog-neutral size; 1.5 draws
     * the reference carbine about 69 units long against the 50-unit player, a compact pistol
     * about 30, and the largest anti-materiel rifle about 104.
     */
    public static final float WEAPON_SIZE_MULTIPLIER = 1.5f;

    /**
     * World size of a scale-1.0 gun frame (the reference carbine, 840 mm), before
     * {@link #WEAPON_SIZE_MULTIPLIER}. Against the 50-unit player this draws the readable,
     * deliberately oversized silhouette the scale catalog is tuned for: a carbine roughly
     * body-height long, a compact pistol under half of that.
     */
    public static final float GUN_FRAME_UNITS = 46f;

    /**
     * World size of a scale-1.0 melee frame, before {@link #WEAPON_SIZE_MULTIPLIER}. Its
     * catalog reference is 900 mm, not 840 mm.
     */
    public static final float MELEE_FRAME_UNITS = 49f;

    /** Grip anchor as a fraction of frame width — guns pivot near the trigger, not the centre. */
    public static final float GUN_GRIP_ANCHOR_X = 0.34f;

    /** Melee handles sit at the left edge of the frame. */
    public static final float MELEE_GRIP_ANCHOR_X = 0.18f;

    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Float> gunScales = new HashMap<>();
    private final Map<String, Float> meleeScales = new HashMap<>();

    public WeaponSprites() {
        readScales("sprites/guns-scale.json", gunScales);
        readScales("sprites/melee-scales.json", meleeScales);
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
        if (MeleeId.isMeleeWireId(wireId)) {
            MeleeId id = MeleeId.fromWireId(wireId);
            return MELEE_FRAME_UNITS * meleeScales.getOrDefault(id.assetId(), 1f)
                * WEAPON_SIZE_MULTIPLIER;
        }
        WeaponId id = WeaponId.fromOrdinal(wireId);
        return GUN_FRAME_UNITS * gunScales.getOrDefault(id.assetId(), 1f)
            * WEAPON_SIZE_MULTIPLIER;
    }

    /** Grip anchor for {@code wireId}, as a fraction of the frame width. */
    public float gripAnchorX(int wireId) {
        return MeleeId.isMeleeWireId(wireId) ? MELEE_GRIP_ANCHOR_X : GUN_GRIP_ANCHOR_X;
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

    private static void readScales(String path, Map<String, Float> out) {
        try {
            JsonValue root = new JsonReader().parse(Gdx.files.internal(path));
            JsonValue byId = root.get("byId");
            if (byId != null) {
                for (JsonValue entry = byId.child; entry != null; entry = entry.next) {
                    out.put(entry.name, entry.asFloat());
                }
            }
        } catch (Exception e) {
            Gdx.app.error("WeaponSprites", "unreadable scale catalog: " + path, e);
        }
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
