package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArenaMapFormatTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;

    @Test
    void standardTextCarriesTheExpectedArenaGeometry() {
        ArenaMap map = ArenaMap.standard();
        Set<Rect> expected = expectedStandardSolids();

        assertEquals(expected, new HashSet<>(map.solids()));
        assertEquals(expected.size(), map.solids().size(), "the migrated map must not add duplicates");
        assertEquals(List.of(
            new SpawnPoint(0, 240f, 180f),
            new SpawnPoint(1, 2760f, 180f)), map.spawns());

        // The pre-migration arena was wall and stone throughout. Milestone 3 then authored the sky
        // range — a one-way platform, a pane and a crate per side — and the close-out authored the
        // fixture door pair, so this census is the shipped map's published shape: the wall count is
        // the pre-migration one, and every other block is named here rather than appearing quietly.
        Map<BlockKind, Integer> kinds = new EnumMap<>(BlockKind.class);
        Map<Material, Integer> materials = new EnumMap<>(Material.class);
        for (MapBlock block : map.blocks()) {
            kinds.merge(block.kind(), 1, Integer::sum);
            materials.merge(block.material(), 1, Integer::sum);
        }
        assertEquals(
            Map.of(BlockKind.WALL, 56, BlockKind.GLASS, 2, BlockKind.BREAKABLE, 2,
                BlockKind.PLATFORM, 2, BlockKind.DOOR, 2),
            kinds);
        assertEquals(
            Map.of(Material.STONE, 56, Material.WOOD, 4, Material.GLASS, 2, Material.METAL, 2),
            materials);
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
            "centred door metal 1490 400 20 90 id=1",
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
     * durability multiplier stayed out of the encoding for maps that never authored one, and that
     * a door's id and access are encoded only for a door block: the close-out's fixture door pair
     * is the first door content the shipped arena authors, and it moved this literal and the
     * asset's header together.
     */
    @Test
    void standardArenaHashPinsTheBakedSdfIdentity() {
        assertEquals(
            "686c9165aa23baecbd21c45e2139c16e13e3b43fc81be2e7ed16c7c0559da597",
            MapHash.hex(ArenaMap.standard().mapHash()));
    }

    /**
     * The startup-log identity line names the census, the mirror axis and the hash.
     *
     * <p>This is the string both a client and a server print at startup so a stale process that
     * still runs a wall-only map is one log line apart from a current one — the first live run of
     * the fixture range produced findings only such a mismatch can produce.
     */
    @Test
    void describeAnnouncesTheCensusMirrorAxisAndHash() {
        ArenaMap map = ArenaMap.standard();
        String describe = map.describe();

        assertTrue(describe.contains("64 blocks"), describe);
        assertTrue(describe.contains("WALL 56"), describe);
        assertTrue(describe.contains("GLASS 2"), describe);
        assertTrue(describe.contains("BREAKABLE 2"), describe);
        assertTrue(describe.contains("PLATFORM 2"), describe);
        assertTrue(describe.contains("DOOR 2"), describe);
        assertTrue(describe.contains("mirror x=1500.0"), describe);
        assertTrue(
            describe.contains("686c9165aa23baecbd21c45e2139c16e13e3b43fc81be2e7ed16c7c0559da597"),
            describe);
        assertEquals(MapHash.hex(map.mapHash()), map.mapHashHex());
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
    @DisplayName("a door's id and access round-trip, and a mirrored pair is two doors")
    void doorAttributesRoundTripAndMirroredPairTakesTwoIds() {
        ArenaMap map = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1",
            "mirrored door metal 240 100 24 90 id=7 access=TEAM0",
            "mirrored door metal 700 400 24 90 id=12 access=TEAM1"));

        // Authored order: the centred door, then the mirrored pair's own two blocks (the left block
        // first, as `mirrored` adds the block before its reflection).
        assertEquals(DoorSpec.all(1), map.blocks().get(0).door());
        assertEquals(DoorSpec.team(7, 0), map.blocks().get(1).door());
        assertEquals("a reflected door is a second door, not the same one mirrored",
            DoorSpec.team(8, 0), map.blocks().get(2).door());
        assertEquals(12, map.blocks().get(3).door().id());
        assertEquals(13, map.blocks().get(4).door().id());
        assertEquals(5, map.blocks().size());

        assertTrue(map.blocks().get(0).door().allows(0));
        assertTrue(map.blocks().get(0).door().allows(1), "an ALL door answers to either side");
        assertTrue(map.blocks().get(1).door().allows(0));
        assertFalse(map.blocks().get(1).door().allows(1), "a TEAM0 door refuses the other side");
        assertFalse(map.blocks().get(1).door().allows(2), "and it refuses a neutral");

        String saved = ArenaMapFormat.write(map);
        assertTrue(saved.contains("centred door metal 1488.0 100.0 24.0 90.0 id=1"), saved);
        assertTrue(saved.contains("mirrored door metal 240.0 100.0 24.0 90.0 id=7 access=TEAM0"),
            "a TEAM door is written with the team it answers to: " + saved);
        assertFalse(saved.contains("access=ALL"), "the default access is not spelled out", saved);
        assertEquals(map.blocks(), ArenaMapFormat.parse(saved).blocks());
    }

    @Test
    @DisplayName("a door's id and access are authored content: they move the map hash")
    void doorIdentityParticipatesInTheHash() {
        ArenaMap plain = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1"));
        ArenaMap renumbered = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=2"));
        ArenaMap locked = ArenaMapFormat.parse(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1 access=TEAM1"));

        assertFalse(java.util.Arrays.equals(plain.mapHash(), renumbered.mapHash()),
            "the wire identity of a door is authored content");
        assertFalse(java.util.Arrays.equals(plain.mapHash(), locked.mapHash()),
            "who may open a door is authored content");
        // The door block is the only block in either map, and every non-door block of the shipped
        // arena hashes exactly as it did before the door fields existed — pinned by the canonical
        // hash assertion below, which the fixture range also has to keep passing.
    }

    @Test
    void doorAttributesAreStrict() {
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90"),
            "a door requires an id=<n> attribute");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 1488 100 24 90 id=1"),
            "id is only meaningful on a door");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred glass glass 1488 100 24 90 access=ALL"),
            "access is only meaningful on a door");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=0"),
            "door id must be positive");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1 access=SOMETIMES"),
            "door access must be ALL or TEAM<n>");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1 id=2"),
            "duplicate id attribute");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=1 access=ALL access=TEAM0"),
            "duplicate access attribute");
        assertFormatError(source(
            "skystrike-map 1",
            "mirror 1500",
            "centred door metal 1488 100 24 90 id=3",
            "mirrored door metal 240 100 24 90 id=2"),
            // The mirrored pair takes ids 2 and 3, so the centred door's 3 is taken.
            "duplicate door id: 3");
    }

    @Test
    @DisplayName("a map may not author more doors than the occluder array can hold")
    void tooManyDoorsFailsTheLoad() {
        // Centred doors, one per height, so each is its own block and the only thing the load can
        // object to is how many of them there are.
        StringBuilder text = new StringBuilder("skystrike-map 1\nmirror 1500\n");
        for (int index = 0; index < VisionConfig.MAX_DOOR_OCCLUDERS; index++) {
            text.append("centred door metal 1488 ").append(100 + index * 100)
                .append(" 24 90 id=").append(index + 1).append('\n');
        }
        ArenaMap atCap = ArenaMapFormat.parse(text.toString());
        assertEquals(VisionConfig.MAX_DOOR_OCCLUDERS, atCap.blocks().size(),
            "every door of a map at the cap must have an occluder slot");

        text.append("centred door metal 1488 1700 24 90 id=99\n");
        assertFormatError(text.toString(), "can occlude");
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

    /**
     * The shipped arena's bullet-blocking rectangles, written by hand so the comparison below is
     * independent of the text source it is checking.
     *
     * <p>This list began as the old Java builder's exact rectangles — the pre-migration arena,
     * which the text conversion had to reproduce to the unit. Milestone 3 then added the sky range
     * to the shipped map on purpose, and two fixtures of it appear here: the crate, because a
     * breakable block blocks bullets while the pane beside it passes them and the platform under it
     * never blocks them, and the close-out's door pair, because a shut door panel is in the way of
     * a bullet exactly as a wall is.
     */
    private static Set<Rect> expectedStandardSolids() {
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

        // Milestone 3's fixture range: the crate on the sky platform, which does block bullets.
        addMirrored(solids, new Rect(1740f, 1318f, 60f, 60f));

        // The close-out's fixture door: a mirrored metal pair in the lane, closed at the start of a
        // round and therefore in the bullet view this set describes.
        addMirrored(solids, new Rect(1034f, 100f, 36f, 170f));

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
