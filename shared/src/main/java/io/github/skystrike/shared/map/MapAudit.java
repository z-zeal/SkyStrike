package io.github.skystrike.shared.map;

import io.github.skystrike.shared.sdf.SdfBaker;
import io.github.skystrike.shared.sdf.SdfField;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The one statement of what "this map is all right" means, and the one bake both surfaces write.
 *
 * <p>Map plan §6 gives the map system two authoring surfaces — the in-game
 * {@code MapEditorScreen} and the headless CLI — and requires them to share one rule set. This
 * class is that rule set's executable half: {@link #check(ArenaMap, String)} turns a built map plus
 * the reachability gate into a verdict with a human-readable listing, and {@link #bake(ArenaMap)}
 * produces exactly the bytes the client loads. Neither surface re-implements either one, so an
 * editor warning and a failed build are the same statement about the same map, and a map baked on a
 * phone is the map baked by CI.
 *
 * <p><b>What a check needs and does not do.</b> Format validation is not repeated here. A map that
 * reaches this class has already been through {@link ArenaMap.Builder} — the parser, the editor's
 * draft and every hand-built test map all build through it — so symmetry, duplicate geometry,
 * duplicate door ids, the door-occluder cap and spawn-in-block rules have already been enforced
 * with the parser's own messages. What is left is the property no constructor can check: whether a
 * body standing at a spawn can reach the rest of the map.
 *
 * <p><b>§11.4's decision, as executed.</b> The plan left one question open: does a map that fails
 * reachability ship with a warning or fail the build? Both readings are available on
 * {@link MapReachability.Report}, so the choice is a policy, and this class is where it lives — as
 * a named {@link Reading} rather than a branch in the CLI, because the editor has to answer the
 * same question. {@link Reading#SPAWNS_ONLY} is the decision of 2026-10-12 and what CI enforces: a
 * map whose spawns cannot reach each other fails, and a map that is merely not fully reachable
 * warns with the sealed pocket named — the shipped arena is exactly that map, and its ten sealed
 * stances are an artifact of the milestone-4 fixture door's alcove rather than a defect.
 * {@link Reading#FULL} is §7's strict reading, still offered so a human can ask the stricter
 * question of any map without a code change.
 */
public final class MapAudit {

    /**
     * How strictly a map is judged.
     *
     * <p>The two values are the two readings §11.4 named, not a severity scale: they change which
     * finding fails, never what is searched.
     */
    public enum Reading {

        /**
         * The requirement a map has to meet to be playable: every spawn reaches every other spawn.
         *
         * <p>A stance no spawn can reach is reported, never fatal. This is what CI enforces.
         */
        SPAWNS_ONLY("spawns must reach each other"),

        /**
         * §7's letter: every stance on the grid is reachable from every spawn.
         *
         * <p>Stricter than {@link #SPAWNS_ONLY} by exactly the sealed pockets, which is why the
         * shipped arena fails this reading and passes the other one.
         */
        FULL("every stance must be reachable from every spawn");

        private final String summary;

        Reading(String summary) {
            this.summary = summary;
        }

        /** A clause naming what this reading requires, for a report line. */
        public String summary() {
            return summary;
        }
    }

    /** The outcome of one check. */
    public enum Status {

        /** Nothing to report. */
        PASS,

        /** The map is playable under the applied reading, with something worth naming. */
        WARN,

        /** The map fails the applied reading. */
        FAIL
    }

    /**
     * One map's audit: the verdict, the report behind it and the lines a caller prints or draws.
     *
     * <p>{@code lines} is the rendering, and it is deliberately built here rather than in the CLI:
     * a terminal report and an editor's status area have to say the same thing, and the only way to
     * guarantee that is one implementation of the sentence.
     */
    public record Result(
            String label,
            Reading reading,
            Status status,
            MapReachability.Report reachability,
            List<String> lines) {

        public Result {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(reading, "reading");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reachability, "reachability");
            lines = List.copyOf(lines);
        }

        /** True when the map failed the applied reading; the CLI's exit code follows this. */
        public boolean failed() {
            return status == Status.FAIL;
        }

        /**
         * The stances no spawn can reach: the sealed pocket, complete rather than sampled.
         *
         * <p>An editor overlay draws these and nothing else from the report, which is why the list
         * is the gate's own complete one instead of a summary.
         */
        public List<MapReachability.Stance> pocket() {
            return reachability.unreachableFromEverySpawn();
        }
    }

    private MapAudit() {
    }

    /** Audits {@code map} under the reading CI enforces. */
    public static Result check(ArenaMap map, String label) {
        return check(map, label, Reading.SPAWNS_ONLY);
    }

    /**
     * Audits {@code map}: one reachability search, then the verdict the reading implies.
     *
     * <p>{@code label} names the map in the report — a file path for the CLI, a map name for the
     * editor — because a CI log with several maps in it has to say which one failed.
     */
    public static Result check(ArenaMap map, String label, Reading reading) {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(reading, "reading");
        MapReachability.Report report = MapReachability.check(map);

        Status status;
        if (!report.spawnsConnected()) {
            // The hard requirement, and the one thing a reading does not change: a map whose spawns
            // cannot reach each other is not a map two teams can play.
            status = Status.FAIL;
        } else if (!report.fullyReachable()) {
            status = reading == Reading.FULL ? Status.FAIL : Status.WARN;
        } else if (map.spawns().isEmpty()) {
            // A map without a spawn is a valid map the format allows, and reachability is vacuously
            // true for it — the search starts nowhere and proves nothing. Say that rather than
            // reporting a clean bill of health for a map no match can use.
            status = Status.WARN;
        } else {
            status = Status.PASS;
        }

        List<String> lines = new ArrayList<>();
        lines.add(status + " " + label + ": " + report.describe());
        if (status == Status.FAIL && !report.spawnsConnected()) {
            for (MapReachability.SpawnReport spawn : report.spawns()) {
                lines.add("  spawn " + spawn.teamIndex() + " at (" + spawn.spawnX() + ", "
                    + spawn.spawnY() + ") stands at (" + spawn.start().x() + ", "
                    + spawn.start().feet() + "): " + spawn.withFuel() + "/" + report.stanceCount()
                    + " stance(s) reachable, " + spawn.onFoot() + " of them without fuel, "
                    + spawn.unreachable().size() + " unreachable");
            }
        }
        if (!report.unreachableFromEverySpawn().isEmpty()) {
            lines.add("  " + report.unreachableFromEverySpawn().size()
                + " stance(s) reachable from no spawn:");
            for (MapReachability.Stance stance : report.unreachableFromEverySpawn()) {
                lines.add("    - x=" + stance.x() + " feet=" + stance.feet());
            }
        }
        if (map.spawns().isEmpty()) {
            lines.add(
                "  the map authors no spawn: the search starts nowhere, so it proves nothing");
        }
        lines.add("  reading " + reading.name() + ": " + reading.summary());
        return new Result(label, reading, status, report, lines);
    }

    /**
     * The static distance field of {@code map}, at the scale the shipped asset uses.
     *
     * <p>The editor asks for this to draw with and to upload; {@link #bake(ArenaMap)} is the same
     * field as the bytes the client loads, so neither caller can bake a different map than the one
     * it names — the field carries the map's own hash.
     */
    public static SdfField bakeField(ArenaMap map) {
        Objects.requireNonNull(map, "map");
        return SdfBaker.bake(map);
    }

    /**
     * The bake of {@code map} as the bytes {@code data/<map>.sdf} holds: header included.
     *
     * <p>This is what makes "the shipped asset is the bake of the shipped map" a statement a check
     * can make: the bytes of this call and the bytes of the file must be equal, and CI compares
     * them.
     */
    public static byte[] bake(ArenaMap map) {
        SdfField field = bakeField(map);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(SdfField.HEADER_BYTES
            + field.width() * field.height());
        try {
            field.write(bytes);
        } catch (IOException exception) {
            // ByteArrayOutputStream never throws; keep one writer implementation for every
            // destination, exactly as ArenaMapFormat keeps one for its own two.
            throw new AssertionError("ByteArrayOutputStream unexpectedly failed", exception);
        }
        return bytes.toByteArray();
    }
}
