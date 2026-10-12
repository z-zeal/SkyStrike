package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The editor's data model: symmetry while editing, strictness at the build, and one identity.
 *
 * <p>A draft is the only mutable thing in the map layer, so these tests are about what it refuses
 * as much as what it does. The three properties worth pinning are that a mirrored entry can never
 * lose its twin (deleting either one deletes the verb), that a draft fails to build with exactly
 * the message the loader would have used — because it is the loader's own builder underneath — and
 * that "has this been edited?" is answered by the map hash the SDF header and the join gate use,
 * rather than by a dirty flag that could drift from it.
 */
class MapDraftTest {

    private static final float AXIS = 1500f;

    @Test
    @DisplayName("a draft of the shipped arena saves exactly what the canonical writer writes")
    void aDraftOfTheArenaIsTheCanonicalText() {
        ArenaMap arena = ArenaMap.standard();
        MapDraft draft = MapDraft.of(arena);

        assertEquals(arena.blocks().size(), draft.blocks().size());
        assertEquals(35, draft.authoredLineCount(),
            "35 authored lines carry the arena's 64 blocks: six centred and 29 mirrored");
        assertEquals(ArenaMapFormat.write(arena), draft.toText(),
            "a draft's save has to be the canonical save, not a near miss");
        assertFalse(draft.isDirty());
        assertEquals(arena.mapHashHex(), draft.build().mapHashHex());
    }

    @Test
    @DisplayName("a mirrored entry is added as a pair and deleted as a pair, from either twin")
    void aMirroredEntryLivesAndDiesAsAPair() {
        MapDraft draft = MapDraft.of(ArenaMap.standard());
        int before = draft.blocks().size();
        MapBlock crate = new MapBlock(
            BlockKind.BREAKABLE, Material.WOOD, new Rect(700f, 450f, 40f, 20f), 2.5f);

        draft.mirrored(crate);

        assertEquals(before + 2, draft.blocks().size());
        assertTrue(draft.blocks().contains(crate));
        MapBlock twin = crate.mirror(AXIS);
        assertTrue(draft.blocks().contains(twin));

        assertTrue(draft.remove(twin), "the twin has to be deletable by its own identity");
        assertEquals(before, draft.blocks().size());
        assertFalse(draft.blocks().contains(crate), "the pair left together");
        assertFalse(draft.isDirty(), "an entry added and removed again is not an edit");
    }

    @Test
    @DisplayName("a centred entry is one block, and a mirrored door takes the next id")
    void centredEntriesAndDoorsFollowTheRules() {
        MapDraft draft = MapDraft.empty(AXIS);
        MapBlock panel = new MapBlock(
            BlockKind.DOOR, Material.METAL, new Rect(1024f, 100f, 36f, 170f),
            MapBlock.DEFAULT_DURABILITY_MULTIPLIER, DoorSpec.all(7));

        draft.mirrored(panel);

        List<MapBlock> blocks = draft.blocks();
        assertEquals(2, blocks.size());
        assertEquals(7, blocks.get(0).door().id());
        assertEquals(8, blocks.get(1).door().id(),
            "a mirrored door is a second door, not the same one seen from the far side");
        assertEquals(1940f, blocks.get(1).rect().x(), 0.001f,
            "the twin is the panel reflected about 1500: 2 * 1500 - 1024 - 36");

        MapBlock wall = new MapBlock(
            BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f));
        draft.centred(wall);
        assertEquals(3, draft.blocks().size(), "a centred entry is its own reflection");
        assertEquals(3, draft.build().blocks().size());
    }

    @Test
    @DisplayName("the draft refuses at the gesture whatever has no mid-edit excuse")
    void gesturesAreCheckedWhereTheyHappen() {
        MapDraft draft = MapDraft.empty(AXIS);
        MapBlock straddling = new MapBlock(
            BlockKind.WALL, Material.STONE, new Rect(1490f, 200f, 20f, 20f));

        IllegalArgumentException straddle = assertThrows(
            IllegalArgumentException.class, () -> draft.mirrored(straddling));
        assertTrue(straddle.getMessage().contains("straddles the mirror axis"), straddle.getMessage());

        MapBlock offCentre = new MapBlock(
            BlockKind.WALL, Material.STONE, new Rect(100f, 200f, 20f, 20f));
        IllegalArgumentException centring = assertThrows(
            IllegalArgumentException.class, () -> draft.centred(offCentre));
        assertTrue(centring.getMessage().contains("not symmetric about x=1500.0"),
            centring.getMessage());

        assertTrue(draft.blocks().isEmpty(), "a refused gesture leaves the draft alone");
    }

    @Test
    @DisplayName("a draft may hold an invalid map; building it fails with the builder's own message")
    void buildingIsWhereTheInvariantsAre() {
        MapDraft draft = MapDraft.empty(AXIS);
        MapBlock wall = new MapBlock(BlockKind.WALL, Material.STONE, new Rect(100f, 200f, 20f, 20f));
        // The same gesture twice, which is what a mis-tapped place button is: the draft keeps both
        // authored lines, and the builder is where the second one is refused.
        draft.mirrored(wall);
        draft.mirrored(wall);

        assertEquals(4, draft.blocks().size(), "the draft holds the duplicate happily");

        IllegalArgumentException duplicate = assertThrows(
            IllegalArgumentException.class, draft::build);
        assertTrue(duplicate.getMessage().contains("duplicate map block"), duplicate.getMessage());
        assertThrows(IllegalArgumentException.class, draft::toText,
            "saving goes through the build, so a draft that cannot build cannot save");
    }

    @Test
    @DisplayName("a draft authored from nothing is a valid empty map and is never clean")
    void anEmptyDraftIsAnEmptyMap() {
        MapDraft draft = MapDraft.empty(AXIS);
        draft.spawn(new SpawnPoint(0, 240f, 180f));
        draft.spawn(new SpawnPoint(1, 2760f, 180f));

        ArenaMap map = draft.build();

        assertTrue(map.blocks().isEmpty());
        assertEquals(2, map.spawns().size());
        assertTrue(draft.isDirty(), "there is no baseline to be equal to");
        MapReachability.Report report = MapReachability.check(map);
        assertEquals(report.columns(), report.stanceCount(),
            "a map with no blocks still has the arena floor, one stance per column");

        ArenaMap reloaded = ArenaMapFormat.parse(draft.toText());
        assertEquals(map.mapHashHex(), reloaded.mapHashHex());
    }

    @Test
    @DisplayName("clear keeps the spawns and the axis; a stale gesture removes nothing")
    void clearingAndStaleGestures() {
        MapDraft draft = MapDraft.of(ArenaMap.standard());
        SpawnPoint spawn = draft.spawns().get(0);

        draft.clear();

        assertTrue(draft.blocks().isEmpty());
        assertEquals(2, draft.spawns().size(), "clearing blocks is not clearing the map");
        assertTrue(draft.spawns().contains(spawn));
        assertTrue(draft.isDirty(), "an empty draft is not the arena it was loaded from");
        assertEquals("skystrike-map 1\nmirror 1500.0\nspawn 0 240.0 180.0\nspawn 1 2760.0 180.0\n",
            draft.toText(), "a cleared draft is still a map: header, axis and spawns, no blocks");
        assertFalse(draft.remove(new MapBlock(
            BlockKind.WALL, Material.STONE, new Rect(5000f, 5000f, 20f, 20f))));
        assertTrue(draft.removeSpawn(spawn));
        assertFalse(draft.removeSpawn(spawn));
    }

    @Test
    @DisplayName("a draft of a live map is refused, because a match's damage is not an edit")
    void anAsymmetricMapIsNotADraft() {
        ArenaMap arena = ArenaMap.standard();
        MapBlock shattered = arena.blocks().stream()
            .filter(block -> !block.rect().mirror(arena.mirrorAxisX()).equals(block.rect()))
            .findFirst()
            .orElseThrow();
        ArenaMap live = arena.without(List.of(shattered));

        IllegalArgumentException asymmetric =
            assertThrows(IllegalArgumentException.class, () -> MapDraft.of(live));
        assertTrue(asymmetric.getMessage().contains("not symmetric"), asymmetric.getMessage());
    }
}
