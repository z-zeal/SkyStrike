package io.github.skystrike.tools;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapAudit;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline bake tool that precomputes the 2D Signed Distance Field for the standard arena geometry
 * and writes {@code assets/data/arena.sdf}.
 *
 * <p>Kept because it is the desktop developer's one-button bake, and now a wrapper over the shared
 * core rather than a second bake: the bytes this writes are the bytes {@code MapTool bake} writes
 * and {@code MapTool bake --check} verifies, so the desktop tool, the CLI and CI's gate cannot end
 * up disagreeing about what the shipped asset is. {@code MapTool bake --out <path>} is the general
 * form; this class is the familiar default.
 */
public final class SdfBakeTool {

    public static void main(String[] args) {
        String outputPath = args.length > 0 ? args[0] : "assets/data/arena.sdf";

        System.out.println("[SdfBakeTool] Baking SDF for ArenaMap.standard()...");
        long startTime = System.currentTimeMillis();

        ArenaMap map = ArenaMap.standard();
        byte[] bytes = MapAudit.bake(map);

        long bakeTime = System.currentTimeMillis() - startTime;

        try {
            // The header printed below is read back out of the bytes that are about to be written,
            // so the report describes the asset rather than a second bake of the same map.
            SdfField field = SdfField.read(new ByteArrayInputStream(bytes));
            System.out.printf(
                    "[SdfBakeTool] Bake complete in %d ms (%dx%d texels, %.1f units/texel)%n",
                    bakeTime, field.width(), field.height(), field.texelScale());
            Path path = new File(outputPath).toPath().toAbsolutePath();
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, bytes);
            System.out.printf(
                    "[SdfBakeTool] Written %d bytes to %s%n", bytes.length, outputPath);
        } catch (IOException e) {
            System.err.println("[SdfBakeTool] Failed to write SDF file: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
