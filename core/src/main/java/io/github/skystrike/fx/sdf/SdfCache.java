package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.sdf.SdfBaker;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.InputStream;

/**
 * Cache and loader for the baked arena Signed Distance Field asset ({@code data/arena.sdf}).
 */
public final class SdfCache {

    private static final String DEFAULT_SDF_PATH = "data/arena.sdf";

    private SdfCache() {
    }

    /**
     * Loads the baked SDF asset from internal storage, falling back to runtime bake if missing.
     */
    public static SdfField loadStandard() {
        return load(DEFAULT_SDF_PATH);
    }

    /**
     * Loads the SDF asset from the specified internal path.
     */
    public static SdfField load(String internalPath) {
        try {
            FileHandle handle = Gdx.files.internal(internalPath);
            if (handle.exists()) {
                try (InputStream in = handle.read()) {
                    return SdfField.read(in);
                }
            }
        } catch (Throwable t) {
            Gdx.app.error("SdfCache", "Failed to load pre-baked SDF from " + internalPath + ", baking at runtime: " + t.getMessage());
        }

        // Runtime bake fallback
        Gdx.app.log("SdfCache", "Baking SDF at runtime for ArenaMap.standard()...");
        return SdfBaker.bake(ArenaMap.standard(), VisionConfig.SDF_TEXEL_SCALE);
    }
}
