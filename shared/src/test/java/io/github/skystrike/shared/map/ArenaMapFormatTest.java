package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void mapHashIgnoresAuthoredLineOrderButIncludesMapContents() {
        ArenaMap first = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "mirrored wall stone 100 200 20 20",
            "mirrored breakable wood 700 450 40 20"));
        ArenaMap reordered = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 1 2760 180",
            "spawn 0 240 180",
            "mirrored breakable wood 700 450 40 20",
            "mirrored wall stone 100 200 20 20"));
        ArenaMap changed = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "spawn 0 240 180",
            "spawn 1 2760 180",
            "mirrored wall stone 100 200 20 20",
            "mirrored breakable wood 710 450 40 20"));

        assertArrayEquals(first.mapHash(), reordered.mapHash());
        assertFalse(java.util.Arrays.equals(first.mapHash(), changed.mapHash()));
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
    void durabilityAttributeRoundTripsAndReachesThePool() {
        String text = source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=2.5",
            "centred breakable stone 1400 600 200 18");

        ArenaMap map = ArenaMapFormat.parse(text);

        MapBlock reinforced = map.blocks().get(0);
        MapBlock plain = map.blocks().get(2);
        assertEquals(2.5f, reinforced.durabilityMultiplier());
        assertEquals(120f * 2.5f, MaterialTable.durabilityPool(reinforced));
        assertEquals(1f, plain.durabilityMultiplier(), "an omitted attribute is the default");
        assertEquals(420f, MaterialTable.durabilityPool(plain));

        String saved = ArenaMapFormat.write(map);
        assertTrue(saved.contains("mirrored breakable wood 700.0 450.0 40.0 20.0 durability=2.5"),
            saved);
        assertFalse(saved.contains("centred breakable stone 1400.0 600.0 200.0 18.0 durability"),
            "the canonical writer does not spell out a default multiplier: " + saved);

        ArenaMap reloaded = ArenaMapFormat.parse(saved);
        assertEquals(map.blocks(), reloaded.blocks());
        assertEquals(saved, ArenaMapFormat.write(reloaded));
    }

    @Test
    void durabilityAttributesAreStrict() {
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 polish=2"),
            "unknown block attribute 'polish'");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored wall stone 700 450 40 20 durability=2"),
            "durability is only meaningful for a kind that accumulates damage: wall");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored glass glass 700 450 40 20 durability=2"),
            "durability is only meaningful for a kind that accumulates damage: glass");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=0"),
            "durability multiplier must be positive");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=-2"),
            "durability multiplier must be positive");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=nan"),
            "durability multiplier must be finite");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=2 durability=3"),
            "duplicate durability attribute");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability"),
            "unknown block attribute 'durability'");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability="),
            "unknown block attribute 'durability='");
    }

    /**
     * The shipped arena's identity, pinned as a literal.
     *
     * <p>{@code assets/data/arena.sdf} names exactly this hash in its header, so if this assertion
     * ever fails the asset has to be re-baked with {@code SdfBakeTool} in the same change — a stale
     * field is refused by the client rather than drawn. It is also the guard that the per-block
     * durability multiplier stayed out of the encoding for maps that never authored one.
     */
    @Test
    void standardArenaHashPinsTheBakedSdfIdentity() {
        assertEquals(
            "767b90fd0a4963152dcdd07151944fb99f9b9e4a7eb75ed23f60d267cf4c2b1c",
            MapHash.hex(ArenaMap.standard().mapHash()));
    }

    @Test
    void durabilityMultiplierParticipatesInTheHashOnlyWhenAuthored() {
        ArenaMap plain = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20"));
        ArenaMap spelledOut = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=1"));
        ArenaMap reinforced = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "mirrored breakable wood 700 450 40 20 durability=2"));

        assertArrayEquals(plain.mapHash(), spelledOut.mapHash(),
            "a default multiplier is not authored content");
        assertFalse(java.util.Arrays.equals(plain.mapHash(), reinforced.mapHash()),
            "a reinforced block is different authored content");
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
