package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArenaMapFormatTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;

    @Test
    void standardTextPreservesThePreMigrationArenaGeometry() {
        ArenaMap map = ArenaMap.standard();
        Set<Rect> expected = legacyStandardSolids();

        assertEquals(expected, new HashSet<>(map.solids()));
        assertEquals(expected.size(), map.solids().size(), "the migrated map must not add duplicates");
        assertEquals(List.of(
            new SpawnPoint(0, 240f, 180f),
            new SpawnPoint(1, 2760f, 180f)), map.spawns());
        for (MapBlock block : map.blocks()) {
            assertEquals(BlockKind.WALL, block.kind());
            assertEquals(Material.STONE, block.material());
        }
    }

    @Test
    void standardArenaActuallyLoadsThroughTheTextParser() {
        ArenaMap parsed = ArenaMapFormat.parse(StandardArenaMap.TEXT);
        ArenaMap standard = ArenaMap.standard();

        assertEquals(standard.mirrorAxisX(), parsed.mirrorAxisX());
        assertEquals(standard.blocks(), parsed.blocks());
        assertEquals(standard.spawns(), parsed.spawns());
    }

    @Test
    void saveLoadRoundTripIsCanonicalAndByteStable() {
        ArenaMap original = ArenaMap.standard();

        String firstSave = ArenaMapFormat.write(original);
        ArenaMap loaded = ArenaMapFormat.parse(firstSave);
        String secondSave = ArenaMapFormat.write(loaded);

        assertEquals(original.mirrorAxisX(), loaded.mirrorAxisX());
        assertEquals(original.blocks(), loaded.blocks());
        assertEquals(original.spawns(), loaded.spawns());
        assertEquals(firstSave, secondSave);
    }

    @Test
    void formatCarriesAllKindsAndSolidsRemainTheBulletCompatibilityView() {
        ArenaMap map = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "mirrored wall stone 100 200 20 20",
            "mirrored glass glass 400 300 20 80",
            "centred door metal 1490 400 20 90",
            "mirrored breakable wood 700 450 40 20",
            "centred platform energy 1400 600 200 18"));

        assertEquals(8, map.blocks().size());
        assertEquals(5, map.solids().size());
        assertTrue(map.solids().contains(new Rect(100f, 200f, 20f, 20f)));
        assertTrue(map.solids().contains(new Rect(1490f, 400f, 20f, 90f)));
        assertTrue(map.solids().contains(new Rect(700f, 450f, 40f, 20f)));
        assertFalse(map.solids().contains(new Rect(400f, 300f, 20f, 80f)));
        assertFalse(map.solids().contains(new Rect(1400f, 600f, 200f, 18f)));
        assertEquals(ArenaMapFormat.write(map), ArenaMapFormat.write(ArenaMapFormat.parse(ArenaMapFormat.write(map))));
    }

    @Test
    void malformedMapsFailWholeLoadWithUsefulMessages() {
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "tilted wall stone 100 200 20 20"), "unknown map verb 'tilted'");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored vapor stone 100 200 20 20"), "unknown block kind 'vapor'");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored wall mist 100 200 20 20"), "unknown material 'mist'");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored wall stone -1 200 20 20"), "block lies outside arena bounds");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored wall stone 1490 300 20 18"), "block straddles the mirror axis");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 1490 300 20 18",
            "centred wall stone 1490 300 20 18"), "duplicate map block");
    }

    private static void assertFormatError(String text, String messageFragment) {
        MapFormatException exception = assertThrows(MapFormatException.class, () -> ArenaMapFormat.parse(text));
        assertTrue(exception.getMessage().contains(messageFragment), exception::getMessage);
    }

    private static String source(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /** The old Java builder's exact rectangles, kept independent of the new text source. */
    private static Set<Rect> legacyStandardSolids() {
        Set<Rect> solids = new HashSet<>();

        addCentred(solids, new Rect(0f, 0f, 3000f, 100f));
        addMirrored(solids, new Rect(0f, 100f, 40f, 1860f));
        addCentred(solids, new Rect(0f, 1960f, 3000f, 40f));

        for (int step = 0; step < 5; step++) {
            addMirrored(solids, new Rect(120f + step * 120f, 200f + step * 50f, 120f, 18f));
        }
        addMirrored(solids, new Rect(820f, 500f, 250f, 18f));

        addMirrored(solids, new Rect(820f, 100f, 70f, 120f));
        addMirrored(solids, new Rect(820f, 220f, 70f, 16f));
        addMirrored(solids, new Rect(890f, 100f, 70f, 160f));
        addMirrored(solids, new Rect(890f, 260f, 70f, 16f));
        addMirrored(solids, new Rect(1010f, 100f, 24f, 260f));
        addMirrored(solids, new Rect(1070f, 100f, 22f, 170f));

        addMirrored(solids, new Rect(1120f, 280f, 75f, 20f));
        addCentred(solids, new Rect(1285f, 280f, 430f, 20f));
        addMirrored(solids, new Rect(1120f, 300f, 24f, 60f));
        addMirrored(solids, new Rect(1120f, 410f, 24f, 450f));
        addMirrored(solids, new Rect(1184f, 300f, 16f, 130f));
        addCentred(solids, new Rect(1180f, 620f, 640f, 20f));

        addMirrored(solids, new Rect(1195f, 192f, 90f, 18f));
        addMirrored(solids, new Rect(1120f, 190f, 75f, 14f));
        addMirrored(solids, new Rect(1285f, 190f, 15f, 14f));
        addMirrored(solids, new Rect(1000f, 342f, 120f, 18f));
        addMirrored(solids, new Rect(1100f, 520f, 120f, 18f));
        addMirrored(solids, new Rect(1220f, 400f, 100f, 18f));

        addCentred(solids, new Rect(1380f, 760f, 240f, 18f));
        addMirrored(solids, new Rect(1300f, 700f, 100f, 16f));
        addCentred(solids, new Rect(1380f, 1000f, 240f, 18f));
        addMirrored(solids, new Rect(760f, 900f, 240f, 18f));

        return solids;
    }

    private static void addMirrored(Set<Rect> solids, Rect rect) {
        solids.add(rect);
        solids.add(rect.mirror(AXIS));
    }

    private static void addCentred(Set<Rect> solids, Rect rect) {
        assertEquals(rect, rect.mirror(AXIS), "test fixture must use a centred rectangle");
        solids.add(rect);
    }
}
