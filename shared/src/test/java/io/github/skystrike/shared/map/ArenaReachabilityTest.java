package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the M8 traversal contract, now asked of the shipped gate.
 *
 * <p>The algorithm used to live in this file as private helpers over a hand-written surface list.
 * Milestone 5 moved it to {@link MapReachability} so a map file — not only this one — can be asked
 * the same question, and the list below stayed: it is the inventory of the arena's <i>intended</i>
 * routes, each named after the platform it stands for, and it is what makes an assertion failure
 * read as "the sniper perch is unreachable" rather than "stance 812 is unreachable". Everything
 * else about the search — the grid, the envelope, the closures — is the shared checker's.
 *
 * <p>The shipped arena's verdict, which these tests pin: both spawns reach every one of those
 * named surfaces, the spawns reach each other, and the one pocket of the map nothing can reach is
 * the milestone-4 fixture door's alcove — including the two floor stances inside it, which is why
 * the sweep below steps over that pocket and the test after it pins it. That alcove is sealed by design of the fixture rather
 * than by accident — the door panel is flush against the tall wall that carries it, so opening it
 * reveals a niche instead of a passage — and pinning it here is what keeps it the <i>only</i>
 * sealed pocket: a change that seals a second one fails this test, and a change that opens this one
 * fails it too, until the pin and the map move together.
 */
class ArenaReachabilityTest {

    private static final float EPSILON = 0.01f;

    /**
     * Every platform the M8 contract is about, named, as the old hand-written list had them.
     *
     * <p>Each rectangle is a block the arena authors — the top face is the surface — and each is
     * mirrored about the axis where the arena mirrors it. The list is deliberately independent of
     * the map text: it is the claim about the arena, not a second copy of it.
     */
    private static List<Surface> traversalSurfaces() {
        List<Surface> surfaces = new ArrayList<>();
        add(surfaces, "ground", new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.GROUND_HEIGHT));

        for (int step = 0; step < 5; step++) {
            addMirrored(surfaces, "ramp " + step,
                new Rect(120f + step * 120f, 200f + step * 50f, 120f, 18f));
        }
        addMirrored(surfaces, "mid lane", new Rect(820f, 500f, 250f, 18f));
        addMirrored(surfaces, "lower crate top", new Rect(820f, 220f, 70f, 16f));
        addMirrored(surfaces, "upper crate top", new Rect(890f, 260f, 70f, 16f));

        addMirrored(surfaces, "outer room floor", new Rect(1120f, 280f, 75f, 20f));
        add(surfaces, "centre room floor", new Rect(1285f, 280f, 430f, 20f));
        addMirrored(surfaces, "wide tunnel roof lip", new Rect(1120f, 190f, 75f, 14f));
        addMirrored(surfaces, "tunnel hatch step", new Rect(1195f, 192f, 90f, 18f));

        addMirrored(surfaces, "doorway landing", new Rect(1000f, 342f, 120f, 18f));
        addMirrored(surfaces, "room access riser", new Rect(1220f, 400f, 100f, 18f));
        addMirrored(surfaces, "catwalk shelf", new Rect(1100f, 520f, 120f, 18f));
        add(surfaces, "catwalk", new Rect(1180f, 620f, 640f, 20f));
        addMirrored(surfaces, "perch ladder", new Rect(1300f, 700f, 100f, 16f));
        add(surfaces, "sniper perch", new Rect(1380f, 760f, 240f, 18f));

        addMirrored(surfaces, "outer upper ledge", new Rect(760f, 900f, 240f, 18f));
        add(surfaces, "high centre platform", new Rect(1380f, 1000f, 240f, 18f));
        return surfaces;
    }

    @Test
    @DisplayName("every M8 gameplay surface is reachable from every spawn")
    void everyTraversalSurfaceIsReachable() {
        ArenaMap map = ArenaMap.standard();
        MapReachability.Report report = MapReachability.check(map);

        for (Surface surface : traversalSurfaces()) {
            List<MapReachability.Stance> stances = stancesOn(report, surface);
            assertFalse(stances.isEmpty(),
                surface.name + " carries no stance a standing body fits on, at "
                    + surface.left + ".." + surface.right + " top " + surface.top);
            for (MapReachability.SpawnReport spawn : report.spawns()) {
                for (MapReachability.Stance stance : stances) {
                    if (inFixtureAlcove(stance)) {
                        // The one sealed pocket, pinned by the test below rather than excused here:
                        // the floor lane runs through the milestone-4 fixture door's alcove, so two
                        // of the ground's stances are the seal instead of a traversal surface.
                        continue;
                    }
                    assertFalse(spawn.unreachable().contains(stance),
                        surface.name + " at " + stance + " is not reachable from team "
                            + spawn.teamIndex() + " within the M8 foot/40%-fuel envelope");
                }
            }
        }
    }

    @Test
    @DisplayName("the shipped arena's spawns reach each other, and only the wall-sealed volume is unreachable")
    void spawnsReachEachOtherAndOnlyTheWallSealedVolumeIsUnreachable() {
        MapReachability.Report report = MapReachability.check(ArenaMap.standard());

        assertEquals(2, report.spawns().size(), "the arena authors two spawns");
        assertTrue(report.spawnsConnected(),
            "the two spawns must reach each other: " + report.describe());

        List<MapReachability.Stance> sealed = report.unreachableFromEverySpawn();
        assertFalse(sealed.isEmpty(),
            "the volume between the tall wall at x 1010..1034 and the shorter one at 1070..1092 is sealed "
                + "by that wall geometry; if nothing is unreachable the map has changed and this pin has to "
                + "move with it: " + report.describe());
        for (MapReachability.Stance stance : sealed) {
            assertTrue(inFixtureAlcove(stance),
                "a stance is unreachable outside the volume between x 1010..1034 and x 1070..1092, which "
                + "is a new seal: " + stance);
        }
        for (MapReachability.SpawnReport spawn : report.spawns()) {
            assertEquals(sealed.size(), spawn.unreachable().size(),
                "both spawns must agree on what the map seals: " + report.describe());
        }
    }

    @Test
    @DisplayName("the gate is deterministic and does not read the grid's own sampling order")
    void theSearchIsDeterministic() {
        ArenaMap map = ArenaMap.standard();
        MapReachability.Report first = MapReachability.check(map);
        MapReachability.Report second = MapReachability.check(map);

        assertEquals(first.describe(), second.describe());
        assertEquals(first.stanceCount(), second.stanceCount());
        assertEquals(first.stances(), second.stances());
        assertEquals(first.unreachableFromEverySpawn(), second.unreachableFromEverySpawn());
        assertTrue(first.columns() > 0 && first.stances().size() > first.columns(),
            "the grid is a grid: more stances than columns");
    }

    @Test
    @DisplayName("a map that seals a spawn off is reported, and one that does not is not")
    void aSealedMapIsReported() {
        // A floor, and a wall 1160 tall across the middle: more rise than 40% of a tank buys, so
        // nothing crosses it and the two spawns cannot meet.
        ArenaMap sealed = parse(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "centred wall stone 0 0 3000 100",
            "mirrored wall stone 1480 100 40 1160");

        MapReachability.Report report = MapReachability.check(sealed);

        assertFalse(report.spawnsConnected(), report.describe());
        assertFalse(report.fullyReachable(), report.describe());
        assertFalse(report.unreachableFromEverySpawn().isEmpty(), report.describe());
    }

    @Test
    @DisplayName("a route the fuel budget cannot buy is a seal, and the budget's own edge is not")
    void theFuelBudgetDecides() {
        ArenaMap outOfReach = parse(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "centred wall stone 0 0 3000 100",
            "centred wall stone 1200 1300 600 20");
        ArenaMap withinReach = parse(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "centred wall stone 0 0 3000 100",
            "centred wall stone 1200 1000 600 20");

        MapReachability.Report out = MapReachability.check(outOfReach);
        MapReachability.Report in = MapReachability.check(withinReach);

        // Both maps keep the spawns connected: the shelf is a bonus surface, not a bridge. What
        // separates them is whether anything on the map is out of the envelope's reach at all.
        assertTrue(out.spawnsConnected(), out.describe());
        assertFalse(out.fullyReachable(), out.describe());
        assertTrue(in.spawnsConnected(), in.describe());
        assertTrue(in.fullyReachable(), in.describe());
    }

    @Test
    @DisplayName("a door is read as the opening a player can make of it")
    void aDoorIsAnOpening() {
        // A passage in this format is a tunnel — a wall held clear of the floor — and a door is the
        // panel that fills it. This one is 300 tall under a wall whose underside is 400 up and
        // whose top is 1260, so climbing over it needs 1160 of rise while the tunnel needs none:
        // the map is walkable exactly while the door counts as an opening.
        ArenaMap throughTheDoor = parse(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "centred wall stone 0 0 3000 100",
            "mirrored wall stone 1400 400 100 860",
            "centred door metal 1440 100 120 300 id=1");

        MapReachability.Report report = MapReachability.check(throughTheDoor);

        assertTrue(report.spawnsConnected(), "a doorway is a route: " + report.describe());
        // The wall's cap is the one thing on this map out of reach — 1160 of rise is more than 40%
        // of a tank buys — and that is what makes the tunnel the route rather than the wall.
        for (MapReachability.Stance stance : report.unreachableFromEverySpawn()) {
            assertEquals(1260f, stance.feet(), 0.001f,
                "nothing but the wall cap is out of reach: " + stance);
        }
        assertFalse(report.unreachableFromEverySpawn().isEmpty(),
            "the wall cap is out of reach, and the doorway is what carries the map: "
                + report.describe());
    }

    @Test
    @DisplayName("a pit a body drops into is reachable, not sealed")
    void aPitIsEnteredByFalling() {
        // One mirrored line authors pillars at 1400..1440 and 1560..1600 — the pair is the two
        // walls — and the 120-unit gap between them is the pit a body drops into from either cap.
        // The caps are 400 above the ground, so reaching them again takes a fuel ascent: the pit is
        // somewhere a player ends up, not somewhere the map seals off.
        ArenaMap withPit = parse(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "centred wall stone 0 0 3000 100",
            "mirrored wall stone 1400 100 40 400");

        MapReachability.Report report = MapReachability.check(withPit);

        assertTrue(report.spawnsConnected(), report.describe());
        assertTrue(report.fullyReachable(),
            "the pit's floor is somewhere a body lands, not a seal: " + report.describe());
        boolean sawAPitFloor = report.stances().stream()
            .anyMatch(stance -> stance.feet() == WorldConfig.GROUND_HEIGHT
                && stance.x() > 1440f && stance.x() < 1560f);
        // The pillars' own caps are inside the envelope, so the map has nothing sealed at all:
        // fullyReachable() above is a real claim about the pocket, not about the pillars.
        assertTrue(sawAPitFloor, "the pit has a floor on the grid: " + report.describe());
    }

    @Test
    @DisplayName("a map with no spawn is searched from nothing and reports nothing sealed")
    void aMapWithoutSpawnsIsNotAParadox() {
        ArenaMap spawnless = parse(
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 0 0 3000 100");

        MapReachability.Report report = MapReachability.check(spawnless);

        assertTrue(report.spawns().isEmpty(), report.describe());
        assertTrue(report.spawnsConnected(), report.describe());
        assertTrue(report.fullyReachable(), report.describe());
    }

    /** The stances standing on a named surface: its top face, over the surface's own x range. */
    private static List<MapReachability.Stance> stancesOn(
            MapReachability.Report report, Surface surface) {
        List<MapReachability.Stance> stances = new ArrayList<>();
        for (MapReachability.Stance stance : report.stances()) {
            if (Math.abs(stance.feet() - surface.top) <= EPSILON
                    && stance.x() >= surface.left - EPSILON
                    && stance.x() <= surface.right + EPSILON) {
                stances.add(stance);
            }
        }
        return stances;
    }

    /**
     * The volume between the tall wall at {@code x} 1010..1034 and the shorter wall at 1070..1092,
     * with its reflection about the axis. The milestone-4 fixture door was originally placed inside
     * this volume (at {@code x} 1034..1070) and has since been moved to a reachable position inside
     * the crate-stack wall; the volume is sealed by the two walls regardless of where the door is, and
     * the unreachable stances persist at their original positions. A body's box overhangs a surface by
     * half its width, so the test spans the volume's walls by that half-width rather than by their faces.
     */
    private static boolean inFixtureAlcove(MapReachability.Stance stance) {
        float half = PlayerConfig.WIDTH / 2f;
        float mirrored = 2f * WorldConfig.MIRROR_AXIS_X - stance.x();
        boolean inside = withinAlcoveX(stance.x(), half) || withinAlcoveX(mirrored, half);
        return inside && stance.feet() <= 360f;
    }

    private static boolean withinAlcoveX(float x, float half) {
        return x >= 1034f - half && x <= 1092f + half;
    }

    private static void addMirrored(List<Surface> surfaces, String name, Rect left) {
        add(surfaces, name + " left", left);
        add(surfaces, name + " right", left.mirror(WorldConfig.MIRROR_AXIS_X));
    }

    private static void add(List<Surface> surfaces, String name, Rect rect) {
        surfaces.add(new Surface(name, rect.left(), rect.right(), rect.bottom(), rect.top()));
    }

    private static ArenaMap parse(String... lines) {
        return ArenaMapFormat.parse(String.join("\n", lines) + "\n");
    }

    private static final class Surface {

        private final String name;
        private final float left;
        private final float right;
        private final float bottom;
        private final float top;

        private Surface(String name, float left, float right, float bottom, float top) {
            this.name = name;
            this.left = left;
            this.right = right;
            this.bottom = bottom;
            this.top = top;
        }
    }
}
