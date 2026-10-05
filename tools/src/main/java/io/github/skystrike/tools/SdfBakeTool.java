package io.github.skystrike.tools;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.sdf.SdfBaker;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Offline bake tool that precomputes the 2D Signed Distance Field for the standard arena geometry
 * and writes {@code assets/data/arena.sdf}.
 */
public final class SdfBakeTool {

    public static void main(String[] args) {
        String outputPath = args.length > 0 ? args[0] : "assets/data/arena.sdf";
        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        System.out.println("[SdfBakeTool] Baking SDF for ArenaMap.standard()...");
        long startTime = System.currentTimeMillis();

        ArenaMap map = ArenaMap.standard();
        SdfField field = SdfBaker.bake(map, VisionConfig.SDF_TEXEL_SCALE);

        long bakeTime = System.currentTimeMillis() - startTime;
        System.out.printf(
                "[SdfBakeTool] Bake complete in %d ms (%dx%d texels, %.1f units/texel)%n",
                bakeTime, field.width(), field.height(), field.texelScale());

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            field.write(out);
            System.out.printf("[SdfBakeTool] Written %d bytes to %s%n", outputFile.length(), outputFile.getPath());
        } catch (IOException e) {
            System.err.println("[SdfBakeTool] Failed to write SDF file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
