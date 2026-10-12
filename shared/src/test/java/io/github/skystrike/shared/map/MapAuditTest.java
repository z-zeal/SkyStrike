package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.sdf.SdfField;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The check/bake core: what the CLI gate and the editor's report both read.
 *
 * <p>The class under test is a policy as much as an algorithm, so these tests are mostly about the
 * policy the plan's §11.4 left open and slice B's CI step had to answer: a map whose spawns cannot
 * reach each other fails under either reading, a map that is merely not fully reachable fails only
 * under the strict reading, and the shipped arena is the second kind — which is the whole reason
 * the decision had to be taken deliberately rather than inherited from the gate. The bake half is
 * pinned as bytes: the same map bakes to the same bytes, and those bytes carry the map's own hash.
 */
class MapAuditTest {

    private static final float AXIS = 1500f;

    /** The ten stances the shipped arena seals inside the milestone-4 fixture door's alcove. */
    private static final List<MapReachability.Stance> ALCOVE = List.of(
        new MapReachability.Stance(1052.5f, 100f),
        new MapReachability.Stance(1072.5f, 270f),
        new MapReachability.Stance(1077.5f, 270f),
        new MapReachability.Stance(1082.5f, 270f),
        new MapReachability.Stance(1087.5f, 270f),
        new MapReachability.Stance(1912.5f, 270f),
        new MapReachability.Stance(1917.5f, 270f),
        new MapReachability.Stance(1922.5f, 270f),
        new MapReachability.Stance(1927.5f, 270f),
        new MapReachability.Stance(1947.5f, 100f));

    @Test
    @DisplayName("the shipped arena warns under the CI reading: spawns connected, one pocket named")
    void theShippedArenaWarnsAndNamesItsPocket() {
        MapAudit.Result result = MapAudit.check(ArenaMap.standard(), "standard()");

        assertEquals(MapAudit.Reading.SPAWNS_ONLY, result.reading());
        assertEquals(MapAudit.Status.WARN, result.status());
        assertFalse(result.failed(), "the shipped arena is playable: CI must not fail on it");
        assertEquals(ALCOVE, result.pocket());
        assertEquals(1526, result.reachability().stanceCount());
        assertEquals(600, result.reachability().columns());
        assertTrue(result.reachability().spawnsConnected());
        assertEquals(2, result.reachability().spawns().size());
        for (MapReachability.SpawnReport spawn : result.reachability().spawns()) {
            assertEquals(150, spawn.onFoot(), "each spawn reaches 150 stances on foot alone");
            assertEquals(1516, spawn.withFuel(), "and 1516 with the jetpack");
            assertEquals(ALCOVE, spawn.unreachable(), "both spawns seal the same alcove");
        }
    }

    @Test
    @DisplayName("the report names every sealed stance, maps the verdict and says which reading ran")
    void theReportIsReadableAndNamesThePocket() {
        MapAudit.Result result = MapAudit.check(ArenaMap.standard(), "standard()");

        String headline = result.lines().get(0);
        assertTrue(headline.startsWith("WARN standard(): reachability: 1526 stance(s) on 600"),
            "the first line names the verdict, the map and the grid: " + headline);
        assertTrue(headline.contains("every spawn reaches every spawn"), headline);
        assertTrue(headline.endsWith("10 stance(s) reachable from no spawn"), headline);
        assertTrue(result.lines().contains("  10 stance(s) reachable from no spawn:"),
            "the pocket is announced with its count: " + result.lines());
        for (MapReachability.Stance stance : ALCOVE) {
            assertTrue(result.lines().contains("    - x=" + stance.x() + " feet=" + stance.feet()),
                "every sealed stance is listed: " + stance);
        }
        assertEquals("  reading SPAWNS_ONLY: spawns must reach each other",
            result.lines().get(result.lines().size() - 1),
            "the report has to say which reading produced the verdict");
    }

    @Test
    @DisplayName("the strict reading fails the same map the CI reading only warns about")
    void theStrictReadingFailsTheArena() {
        MapAudit.Result strict =
            MapAudit.check(ArenaMap.standard(), "standard()", MapAudit.Reading.FULL);

        assertEquals(MapAudit.Status.FAIL, strict.status());
        assertTrue(strict.failed());
        assertEquals(ALCOVE, strict.pocket());
        assertEquals("  reading FULL: every stance must be reachable from every spawn",
            strict.lines().get(strict.lines().size() - 1));
    }

    @Test
    @DisplayName("a map whose spawns cannot reach each other fails under both readings")
    void disconnectedSpawnsFailUnderBothReadings() {
        // A floor with a full-height centred wall through the middle: both spawns stand on the
        // floor of one sealed half. The wall is 1800 tall, far above the jump's rise and above the
        // rise a 40% tank buys, so nothing connects the two sides at any envelope the gate knows.
        ArenaMap sealed = ArenaMap.builder(AXIS)
            .centred(new MapBlock(BlockKind.WALL, Material.STONE,
                new Rect(0f, 0f, 3000f, 100f)))
            .centred(new MapBlock(BlockKind.WALL, Material.STONE,
                new Rect(1400f, 100f, 200f, 1800f)))
            .spawn(new SpawnPoint(0, 300f, 100f))
            .spawn(new SpawnPoint(1, 2700f, 100f))
            .build();

        for (MapAudit.Reading reading : MapAudit.Reading.values()) {
            MapAudit.Result result = MapAudit.check(sealed, "sealed", reading);
            assertEquals(MapAudit.Status.FAIL, result.status(), "reading " + reading);
            assertFalse(result.reachability().spawnsConnected());
            assertTrue(result.lines().get(0).startsWith("FAIL sealed(): reachability: "),
                reading.name());
            assertTrue(result.lines().get(0).contains("SPAWNS DISCONNECTED"), reading.name());
        }
    }

    @Test
    @DisplayName("a clean map passes, a sealed pocket only warns, and no spawn is worth saying")
    void theVerdictsAreOrderedByWhatTheyProve() {
        ArenaMap open = ArenaMap.builder(AXIS)
            .centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f)))
            .spawn(new SpawnPoint(0, 300f, 100f))
            .spawn(new SpawnPoint(1, 2700f, 100f))
            .build();
        MapAudit.Result clean = MapAudit.check(open, "open");
        assertEquals(MapAudit.Status.PASS, clean.status());
        assertTrue(clean.pocket().isEmpty());
        assertTrue(clean.reachability().fullyReachable());
        assertEquals("  reading SPAWNS_ONLY: spawns must reach each other",
            clean.lines().get(clean.lines().size() - 1));

        // One sealed chamber with no way in: the map is playable (the spawns reach each other and
        // everything a player needs, which is the CI reading) but the pocket is named. A room whose
        // ceiling sits flush on its walls, 800 above the floor, is closed to the M8 envelope: its
        // floor is ground a player can see and never stand on.
        ArenaMap walled = ArenaMap.builder(AXIS)
            .centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f)))
            .mirrored(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(1350f, 100f, 40f, 800f)))
            .centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(1350f, 900f, 300f, 40f)))
            .spawn(new SpawnPoint(0, 900f, 100f))
            .spawn(new SpawnPoint(1, 2100f, 100f))
            .build();
        MapAudit.Result pocket = MapAudit.check(walled, "chamber");
        assertEquals(MapAudit.Status.WARN, pocket.status());
        assertTrue(pocket.reachability().spawnsConnected());
        assertFalse(pocket.pocket().isEmpty(), "the chamber behind the wall is sealed");
        assertTrue(pocket.reachability().stances().containsAll(pocket.pocket()),
            "a pocket is a subset of the grid, never an invented place");
        for (MapReachability.Stance stance : pocket.pocket()) {
            assertEquals(100f, stance.feet(), "only the chamber's floor is sealed: " + stance);
            assertTrue(stance.x() > 1390f && stance.x() < 1610f,
                "and only the ground inside its walls: " + stance);
        }

        ArenaMap spawnless = ArenaMap.builder(AXIS)
            .centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f)))
            .build();
        MapAudit.Result none = MapAudit.check(spawnless, "spawnless");
        assertEquals(MapAudit.Status.WARN, none.status());
        assertFalse(none.failed(), "a spawnless map is valid, it is just not a match map");
        assertTrue(none.reachability().spawnsConnected());
        assertTrue(none.pocket().isEmpty());
        assertTrue(none.lines().contains(
            "  the map authors no spawn: the search starts nowhere, so it proves nothing"),
            none.lines().toString());
    }

    @Test
    @DisplayName("baking is deterministic and the bytes name the map they were baked from")
    void theBakeIsDeterministicAndNamesItsMap() throws IOException {
        byte[] first = MapAudit.bake(ArenaMap.standard());
        byte[] second = MapAudit.bake(ArenaMap.standard());

        assertArrayEquals(first, second, "two bakes of one map must be the same bytes");
        assertEquals(1_500_060, first.length,
            "the shipped bake is a 60-byte header plus 1500 x 1000 texels");
        SdfField field = SdfField.read(new ByteArrayInputStream(first));
        assertEquals(1500, field.width());
        assertEquals(1000, field.height());
        assertEquals(2f, field.texelScale());
        assertTrue(field.matchesMap(ArenaMap.standard()),
            "the field's hash is the map's hash, which is what the client's load guard reads");

        // A different map bakes to different bytes and to a different hash, so a stale field can
        // never be mistaken for a fresh one — the property MapTool bake --check leans on.
        ArenaMap other = ArenaMap.builder(AXIS)
            .centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f)))
            .spawn(new SpawnPoint(0, 300f, 100f))
            .build();
        byte[] otherBytes = MapAudit.bake(other);
        assertFalse(first.length == otherBytes.length && Arrays.equals(first, otherBytes),
            "a changed map must change its bake");
        SdfField otherField = SdfField.read(new ByteArrayInputStream(otherBytes));
        assertTrue(otherField.matchesMap(other));
        assertFalse(otherField.matchesMap(ArenaMap.standard()));
    }
}
