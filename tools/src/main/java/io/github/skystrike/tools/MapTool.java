package io.github.skystrike.tools;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.map.MapAudit;
import io.github.skystrike.shared.map.MapFormatException;
import io.github.skystrike.shared.map.MapHash;
import io.github.skystrike.shared.sdf.SdfDiff;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The map builder's headless surface (map plan §6): {@code check} audits maps, {@code bake} writes
 * or verifies the SDF the client loads.
 *
 * <p>This class is deliberately a shell. Everything that decides something — what "this map is all
 * right" means, and the bake — lives in {@link MapAudit} in {@code shared}, because the in-game
 * editor has to answer the same questions and the two surfaces must not answer them twice. What is
 * left here is argument parsing, file I/O, printing and the exit code, and the mapping from a
 * verdict to that exit code is one line, so a CI step cannot quietly change what a failed map
 * means.
 *
 * <p>Maps come from files, and with no file named the shipped arena is audited instead — the
 * embedded canonical text, which is the only map there is until the shipped-maps milestone moves it
 * to {@code assets/maps/arena.map}. That is why CI can gate the map this build actually ships
 * without waiting for the file.
 *
 * <p>Exit codes: 0 when every map passed or warned, 1 when one failed, was malformed or could not
 * be written, and 2 for a usage error.
 */
public final class MapTool {

    /** The asset {@code bake} writes and {@code bake --check} verifies by default. */
    private static final String DEFAULT_SDF = "assets/data/arena.sdf";

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILED = 1;
    private static final int EXIT_USAGE = 2;

    private MapTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The whole CLI without {@link System#exit}, so a caller (a Gradle task, a test runner) sees
     * the verdict as a return value rather than as a process outcome it cannot assert on.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            usage(err);
            return EXIT_USAGE;
        }
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        return switch (args[0]) {
            case "check" -> check(rest, out, err);
            case "bake" -> bake(rest, out, err);
            case "help", "--help", "-h" -> {
                usage(out);
                yield EXIT_OK;
            }
            default -> {
                err.println("unknown command: " + args[0]);
                usage(err);
                yield EXIT_USAGE;
            }
        };
    }

    private static int check(List<String> args, PrintStream out, PrintStream err) {
        MapAudit.Reading reading = MapAudit.Reading.SPAWNS_ONLY;
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if ("--strict".equals(arg)) {
                // §11.4's other reading, on demand: CI enforces SPAWNS_ONLY, and a human asking
                // "would this map pass §7's letter?" gets the same search judged the strict way.
                reading = MapAudit.Reading.FULL;
            } else if (arg.startsWith("--")) {
                err.println("unknown option for check: " + arg);
                usage(err);
                return EXIT_USAGE;
            } else {
                paths.add(arg);
            }
        }

        boolean failed = false;
        if (paths.isEmpty()) {
            ArenaMap shipped = ArenaMap.standard();
            failed |= audit("standard()", shipped, reading, out);
        } else {
            for (String path : paths) {
                ArenaMap map = load(path, err);
                if (map == null) {
                    failed = true;
                    continue;
                }
                failed |= audit(path, map, reading, out);
            }
        }
        return failed ? EXIT_FAILED : EXIT_OK;
    }

    /** Prints one map's identity, then the shared audit's own lines. */
    private static boolean audit(
            String label, ArenaMap map, MapAudit.Reading reading, PrintStream out) {
        out.println("map: " + label + ": " + map.blocks().size() + " block(s), "
            + map.spawns().size() + " spawn(s), mirror " + map.mirrorAxisX() + ", hash "
            + map.mapHashHex());
        MapAudit.Result result = MapAudit.check(map, label, reading);
        for (String line : result.lines()) {
            out.println(line);
        }
        return result.failed();
    }

    private static int bake(List<String> args, PrintStream out, PrintStream err) {
        boolean verifyOnly = false;
        String mapPath = null;
        String sdfPath = DEFAULT_SDF;
        for (int index = 0; index < args.size(); index++) {
            String arg = args.get(index);
            switch (arg) {
                case "--check" -> verifyOnly = true;
                case "--out" -> {
                    if (index + 1 >= args.size()) {
                        err.println("--out needs a path");
                        usage(err);
                        return EXIT_USAGE;
                    }
                    sdfPath = args.get(++index);
                }
                default -> {
                    if (arg.startsWith("--")) {
                        err.println("unknown option for bake: " + arg);
                        usage(err);
                        return EXIT_USAGE;
                    }
                    if (mapPath != null) {
                        err.println("bake takes one map, not two: " + mapPath + " and " + arg);
                        usage(err);
                        return EXIT_USAGE;
                    }
                    mapPath = arg;
                }
            }
        }

        String label = mapPath == null ? "standard()" : mapPath;
        ArenaMap map = mapPath == null ? ArenaMap.standard() : load(mapPath, err);
        if (map == null) {
            return EXIT_FAILED;
        }
        Path target = Path.of(sdfPath);
        if (verifyOnly) {
            return verifyBake(label, map, target, out, err);
        }
        return writeBake(label, map, target, out, err);
    }

    /**
     * Writes the bake. The bytes are {@link MapAudit#bake}'s, so the asset is the same bake the
     * editor makes and {@code --check} verifies — there is no second implementation to drift.
     */
    private static int writeBake(
            String label, ArenaMap map, Path target, PrintStream out, PrintStream err) {
        byte[] bytes = MapAudit.bake(map);
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(target, bytes);
        } catch (IOException failure) {
            err.println("cannot write " + target + ": " + failure.getMessage());
            return EXIT_FAILED;
        }
        out.println("map: " + label + ": " + map.blocks().size() + " block(s), "
            + map.spawns().size() + " spawn(s), mirror " + map.mirrorAxisX() + ", hash "
            + map.mapHashHex());
        out.println("wrote " + bytes.length + " bytes to " + target);
        return EXIT_OK;
    }

    /**
     * Compares the committed asset with a fresh bake, byte for byte.
     *
     * <p>Anything but equality fails, including a header that names another map: the client refuses
     * a field whose header hash is not the map in play, so an asset that merely holds the right
     * texels is still the wrong asset. The report says which of the three it is — another length,
     * another header hash, or a texel region — because a CI log has to name the difference a human
     * would otherwise look for by hand.
     */
    private static int verifyBake(
            String label, ArenaMap map, Path target, PrintStream out, PrintStream err) {
        byte[] baked = MapAudit.bake(map);
        SdfField fresh = MapAudit.bakeField(map);
        byte[] committed;
        try {
            committed = Files.readAllBytes(target);
        } catch (IOException failure) {
            err.println("cannot read " + target + ": " + failure.getMessage());
            return EXIT_FAILED;
        }
        SdfField committedField;
        try {
            committedField = SdfField.read(new ByteArrayInputStream(committed));
        } catch (IOException failure) {
            err.println("FAILED " + target + " is not a readable SDF: " + failure.getMessage());
            return EXIT_FAILED;
        }

        out.println("map: " + label + ": " + map.blocks().size() + " block(s), "
            + map.spawns().size() + " spawn(s), mirror " + map.mirrorAxisX() + ", hash "
            + map.mapHashHex());
        out.println("fresh bake: " + shape(fresh) + ", " + baked.length + " bytes, hash "
            + MapHash.hex(fresh.mapHash()));
        out.println("committed: " + target + ": " + shape(committedField) + ", " + committed.length
            + " bytes, hash " + MapHash.hex(committedField.mapHash()));

        if (Arrays.equals(baked, committed)) {
            out.println("the committed asset is the bake of this map: header hash, header shape"
                + " and every texel agree");
            return EXIT_OK;
        }
        if (baked.length != committed.length) {
            err.println("FAILED the committed asset is " + committed.length + " bytes, the bake of "
                + label + " is " + baked.length);
        } else if (!Arrays.equals(fresh.mapHash(), committedField.mapHash())) {
            err.println("FAILED the committed asset names another map: its header hash is "
                + MapHash.hex(committedField.mapHash()));
        } else {
            SdfDiff diff = fresh.diff(committedField);
            err.println("FAILED the committed asset is not the bake of " + label + ": "
                + diff.texels() + " texel(s) differ, from (" + diff.x() + ", " + diff.y() + ") "
                + diff.width() + "x" + diff.height());
        }
        return EXIT_FAILED;
    }

    private static String shape(SdfField field) {
        return field.width() + "x" + field.height() + " texels, " + field.texelScale()
            + " units/texel";
    }

    /** Reads and parses one map file, reporting the parser's own message on failure. */
    private static ArenaMap load(String path, PrintStream err) {
        String text;
        try {
            text = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            err.println("cannot read " + path + ": " + failure.getMessage());
            return null;
        }
        try {
            return ArenaMapFormat.parse(text);
        } catch (MapFormatException failure) {
            err.println("FAILED " + path + ": " + failure.getMessage());
            return null;
        }
    }

    private static void usage(PrintStream out) {
        out.println("MapTool — the map builder's headless surface.");
        out.println("  check [--strict] [map.map ...]   audit maps (no path = the shipped arena)");
        out.println("  bake [map.map] [--out <path>]    write " + DEFAULT_SDF + " for the map");
        out.println("  bake --check [map.map] [--out <path>]");
        out.println("                                   verify the committed asset is that bake");
        out.println("Exit codes: 0 passed, 1 failed, 2 usage.");
    }
}
