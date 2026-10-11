package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapHash;
import io.github.skystrike.shared.sdf.SdfBaker;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Cache and loader for baked Signed Distance Field assets.
 *
 * <p>Every version-2 field carries the hash of the map it was baked from. A release client refuses
 * a missing, invalid or mismatched field rather than rendering shadows and particle collision for
 * a different arena. An explicitly enabled development client logs the problem and re-bakes the
 * requested map to keep authoring iteration practical.
 */
public final class SdfCache {

    private static final String DEFAULT_SDF_PATH = "data/arena.sdf";

    private SdfCache() {
    }

    /** Loads the baked SDF asset for the canonical standard arena. */
    public static SdfField loadStandard() {
        return load(DEFAULT_SDF_PATH, ArenaMap.standard());
    }

    /** Loads an SDF asset for the canonical standard arena. */
    public static SdfField load(String internalPath) {
        return load(internalPath, ArenaMap.standard());
    }

    /**
     * Loads an SDF asset and verifies that it was baked for {@code map}.
     *
     * <p>With {@link DebugFlags#enabled()} this falls back to a logged runtime bake. With the
     * shipping-default debug switch off, failure is deliberately loud: a stale field must never be
     * silently presented as geometry for another map.
     */
    public static SdfField load(String internalPath, ArenaMap map) {
        Objects.requireNonNull(internalPath, "internalPath");
        Objects.requireNonNull(map, "map");

        try {
            FileHandle handle = Gdx.files.internal(internalPath);
            if (!handle.exists()) {
                throw new IOException("asset does not exist");
            }
            try (InputStream in = handle.read()) {
                SdfField field = SdfField.read(in);
                if (!field.matchesMap(map)) {
                    throw new IOException(
                        "map hash mismatch: field=" + MapHash.hex(field.mapHash())
                            + ", map=" + MapHash.hex(map.mapHash()));
                }
                return field;
            }
        } catch (Exception exception) {
            if (!DebugFlags.enabled()) {
                throw new IllegalStateException(
                    "Refusing SDF asset '" + internalPath + "' for map "
                        + MapHash.hex(map.mapHash()) + ": " + exception.getMessage(),
                    exception);
            }
            Gdx.app.error(
                "SdfCache",
                "Failed to load matching SDF from " + internalPath
                    + "; development fallback will re-bake: " + exception.getMessage());
        }

        Gdx.app.log("SdfCache", "Development SDF bake for map " + MapHash.hex(map.mapHash()) + "...");
        return SdfBaker.bake(map, VisionConfig.SDF_TEXEL_SCALE);
    }
}
