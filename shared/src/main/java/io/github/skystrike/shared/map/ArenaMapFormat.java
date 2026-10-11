package io.github.skystrike.shared.map;

import io.github.skystrike.shared.config.WorldConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Strict, dependency-free reader and canonical writer for versioned SkyStrike text maps.
 *
 * <p>The version-1 grammar is deliberately small and line-based:
 *
 * <pre>
 * skystrike-map 1
 * mirror 1500
 * spawn 0 240 180
 * mirrored wall stone 120 200 120 18
 * centred wall stone 0 0 3000 100
 * </pre>
 *
 * <p>Blank lines and {@code #} comments are ignored. All other lines are strict: malformed
 * headers, unknown verbs/kinds/materials, non-positive sizes, out-of-bounds geometry, duplicate
 * blocks and a rectangle on the wrong side of its symmetry verb fail the entire load with a
 * {@link MapFormatException}. Door ids and other per-kind attributes wait until their shared model
 * exists, rather than accepting data that a save would silently discard.
 */
public final class ArenaMapFormat {

    /** The literal header token required at the first meaningful line. */
    public static final String HEADER = "skystrike-map";

    /** Current text-map grammar version. */
    public static final int VERSION = 1;

    private ArenaMapFormat() {
    }

    /** Parses a complete map string. */
    public static ArenaMap parse(String text) {
        Objects.requireNonNull(text, "text");
        try {
            return read(new StringReader(text));
        } catch (IOException exception) {
            // StringReader never throws, but keep the public Reader implementation as the one path.
            throw new AssertionError("StringReader unexpectedly failed", exception);
        }
    }

    /**
     * Reads a complete map from {@code source}. I/O failures are propagated; format failures are
     * {@link MapFormatException}s and leave no partially built map exposed.
     */
    public static ArenaMap read(Reader source) throws IOException {
        Objects.requireNonNull(source, "source");
        BufferedReader reader = source instanceof BufferedReader buffered
            ? buffered
            : new BufferedReader(source);

        boolean sawHeader = false;
        ArenaMap.Builder builder = null;
        int lineNumber = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String meaningful = withoutComment(line).trim();
            if (meaningful.isEmpty()) {
                continue;
            }
            String[] tokens = meaningful.split("\\s+");

            if (!sawHeader) {
                parseHeader(tokens, lineNumber);
                sawHeader = true;
                continue;
            }

            String verb = tokens[0].toLowerCase(Locale.ROOT);
            switch (verb) {
                case "mirror" -> {
                    requireCount(tokens, 2, lineNumber, "mirror <axis>");
                    if (builder != null) {
                        throw failure(lineNumber, "duplicate mirror declaration");
                    }
                    float axis = parseFloat(tokens[1], lineNumber, "mirror axis");
                    validateMirrorAxis(axis, lineNumber);
                    builder = ArenaMap.builder(axis);
                }
                case "spawn" -> {
                    requireBuilder(builder, lineNumber);
                    requireCount(tokens, 4, lineNumber, "spawn <team> <x> <y>");
                    int teamIndex = parseNonNegativeInt(tokens[1], lineNumber, "spawn team");
                    float x = parseFloat(tokens[2], lineNumber, "spawn x");
                    float y = parseFloat(tokens[3], lineNumber, "spawn y");
                    validatePointInBounds(x, y, lineNumber, "spawn");
                    builder.spawn(new SpawnPoint(teamIndex, x, y));
                }
                case "mirrored", "centred" -> {
                    requireBuilder(builder, lineNumber);
                    requireCount(tokens, 7, lineNumber,
                        verb + " <kind> <material> <x> <y> <width> <height>");
                    MapBlock block = parseBlock(tokens, lineNumber);
                    try {
                        if (verb.equals("mirrored")) {
                            builder.mirrored(block);
                        } else {
                            builder.centred(block);
                        }
                    } catch (IllegalArgumentException exception) {
                        throw failure(lineNumber, exception.getMessage(), exception);
                    }
                }
                default -> throw failure(lineNumber, "unknown map verb '" + tokens[0] + "'");
            }
        }

        if (!sawHeader) {
            throw new MapFormatException("missing '" + HEADER + " " + VERSION + "' header");
        }
        if (builder == null) {
            throw new MapFormatException("missing mirror declaration");
        }

        ArenaMap map = builder.build();
        validateSpawnsNotInsideBlocks(map);
        return map;
    }

    /** Writes {@code map} in canonical version-1 form, ending in a single newline. */
    public static String write(ArenaMap map) {
        StringBuilder output = new StringBuilder();
        try {
            write(map, output);
        } catch (IOException exception) {
            // StringBuilder never throws, but keep one writer implementation for every destination.
            throw new AssertionError("StringBuilder unexpectedly failed", exception);
        }
        return output.toString();
    }

    /**
     * Writes {@code map} in canonical version-1 form to {@code output}.
     *
     * <p>The map must be symmetric. This makes an in-memory asymmetric edit a loud save error
     * instead of an output file whose next load changes its geometry.
     */
    public static void write(ArenaMap map, Appendable output) throws IOException {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(output, "output");

        output.append(HEADER).append(' ').append(Integer.toString(VERSION)).append('\n');
        output.append("mirror ").append(number(map.mirrorAxisX())).append('\n');
        for (SpawnPoint spawn : map.spawns()) {
            output.append("spawn ")
                .append(Integer.toString(spawn.teamIndex()))
                .append(' ')
                .append(number(spawn.x()))
                .append(' ')
                .append(number(spawn.y()))
                .append('\n');
        }

        Set<MapBlock> remaining = new HashSet<>(map.blocks());
        if (remaining.size() != map.blocks().size()) {
            throw new IllegalArgumentException("map cannot be saved with duplicate blocks");
        }
        for (MapBlock block : map.blocks()) {
            if (!remaining.remove(block)) {
                continue;
            }
            MapBlock reflection = block.mirror(map.mirrorAxisX());
            if (reflection.equals(block)) {
                appendBlock(output, "centred", block);
            } else if (remaining.remove(reflection)) {
                appendBlock(output, "mirrored", block);
            } else {
                throw new IllegalArgumentException(
                    "map cannot be saved; block has no reflection: " + block.rect());
            }
        }
    }

    private static void parseHeader(String[] tokens, int lineNumber) {
        if (tokens.length != 2 || !HEADER.equals(tokens[0])) {
            throw failure(lineNumber, "expected '" + HEADER + " " + VERSION + "' header");
        }
        int version;
        try {
            version = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException exception) {
            throw failure(lineNumber, "map version must be an integer: '" + tokens[1] + "'", exception);
        }
        if (version != VERSION) {
            throw failure(lineNumber, "unsupported map format version: " + version);
        }
    }

    private static MapBlock parseBlock(String[] tokens, int lineNumber) {
        BlockKind kind = parseKind(tokens[1], lineNumber);
        Material material = parseMaterial(tokens[2], lineNumber);
        float x = parseFloat(tokens[3], lineNumber, "block x");
        float y = parseFloat(tokens[4], lineNumber, "block y");
        float width = parseFloat(tokens[5], lineNumber, "block width");
        float height = parseFloat(tokens[6], lineNumber, "block height");
        if (width <= 0f || height <= 0f) {
            throw failure(lineNumber, "block size must be positive: " + width + "x" + height);
        }
        Rect rect = new Rect(x, y, width, height);
        validateRectInBounds(rect, lineNumber);
        return new MapBlock(kind, material, rect);
    }

    private static BlockKind parseKind(String token, int lineNumber) {
        try {
            return BlockKind.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw failure(lineNumber, "unknown block kind '" + token + "'", exception);
        }
    }

    private static Material parseMaterial(String token, int lineNumber) {
        try {
            return Material.valueOf(token.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw failure(lineNumber, "unknown material '" + token + "'", exception);
        }
    }

    private static int parseNonNegativeInt(String token, int lineNumber, String field) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException exception) {
            throw failure(lineNumber, field + " must be an integer: '" + token + "'", exception);
        }
        if (value < 0) {
            throw failure(lineNumber, field + " must not be negative: " + value);
        }
        return value;
    }

    private static float parseFloat(String token, int lineNumber, String field) {
        float value;
        try {
            value = Float.parseFloat(token);
        } catch (NumberFormatException exception) {
            throw failure(lineNumber, field + " must be a number: '" + token + "'", exception);
        }
        if (!Float.isFinite(value)) {
            throw failure(lineNumber, field + " must be finite: '" + token + "'");
        }
        return value;
    }

    private static void validateMirrorAxis(float axis, int lineNumber) {
        if (axis <= 0f || axis >= WorldConfig.ARENA_WIDTH) {
            throw failure(lineNumber, "mirror axis lies outside arena bounds: " + axis);
        }
    }

    private static void validatePointInBounds(float x, float y, int lineNumber, String label) {
        if (x < 0f || x > WorldConfig.ARENA_WIDTH || y < 0f || y > WorldConfig.ARENA_HEIGHT) {
            throw failure(lineNumber, label + " lies outside arena bounds: " + x + "," + y);
        }
    }

    private static void validateRectInBounds(Rect rect, int lineNumber) {
        if (rect.left() < 0f
                || rect.right() > WorldConfig.ARENA_WIDTH
                || rect.bottom() < 0f
                || rect.top() > WorldConfig.ARENA_HEIGHT) {
            throw failure(lineNumber, "block lies outside arena bounds: " + rect);
        }
    }

    private static void validateSpawnsNotInsideBlocks(ArenaMap map) {
        for (SpawnPoint spawn : map.spawns()) {
            for (MapBlock block : map.blocks()) {
                if (block.rect().contains(spawn.x(), spawn.y())) {
                    throw new MapFormatException(
                        "spawn for team " + spawn.teamIndex() + " lies inside block: " + block.rect());
                }
            }
        }
    }

    private static void requireBuilder(ArenaMap.Builder builder, int lineNumber) {
        if (builder == null) {
            throw failure(lineNumber, "mirror declaration must appear before map content");
        }
    }

    private static void requireCount(String[] tokens, int expected, int lineNumber, String syntax) {
        if (tokens.length != expected) {
            throw failure(lineNumber, "expected '" + syntax + "'");
        }
    }

    private static String withoutComment(String line) {
        int commentStart = line.indexOf('#');
        return commentStart >= 0 ? line.substring(0, commentStart) : line;
    }

    private static MapFormatException failure(int lineNumber, String message) {
        return new MapFormatException(lineNumber, message);
    }

    private static MapFormatException failure(int lineNumber, String message, Throwable cause) {
        return new MapFormatException(lineNumber, message, cause);
    }

    private static void appendBlock(Appendable output, String verb, MapBlock block) throws IOException {
        Rect rect = block.rect();
        output.append(verb)
            .append(' ')
            .append(block.kind().name().toLowerCase(Locale.ROOT))
            .append(' ')
            .append(block.material().name().toLowerCase(Locale.ROOT))
            .append(' ')
            .append(number(rect.x()))
            .append(' ')
            .append(number(rect.y()))
            .append(' ')
            .append(number(rect.width()))
            .append(' ')
            .append(number(rect.height()))
            .append('\n');
    }

    private static String number(float value) {
        return Float.toString(value);
    }
}
