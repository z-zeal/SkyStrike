package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The palette the editor places from, and the geometry a tap authors with it.
 *
 * <p>These are the editor's rules without a GPU in the room: every kind is reachable from the
 * palette, a preset authors a block the loader accepts, a tap snaps and stays inside the arena,
 * a tap that straddles the mirror axis becomes the centred block the draft can hold, and door ids
 * are allotted in sequence because a duplicate id is a load error rather than a correction. The
 * screen is a view over these calls and has no test of its own — this is where its authoring rules
 * are pinned.
 */
class MapPaletteTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;

    @Test
    @DisplayName("every kind has exactly one preset, labelled once, and only a door is a door")
    void everyKindIsReachableFromThePalette() {
        List<MapPalette.Preset> presets = MapPalette.presets();
        Set<BlockKind> kinds = EnumSet.noneOf(BlockKind.class);
        Set<String> labels = new HashSet<>();
        int doors = 0;

        for (MapPalette.Preset preset : presets) {
            assertTrue(kinds.add(preset.kind()), "two presets author " + preset.kind());
            assertTrue(labels.add(preset.label()), "two presets share a label: " + preset.label());
            assertFalse(preset.label().isBlank());
            if (preset.door()) {
                doors++;
            }
            assertTrue(preset.width() > 0f && preset.height() > 0f, preset.label());
            assertTrue(preset.width() <= WorldConfig.ARENA_WIDTH, preset.label());
            assertTrue(preset.height() <= WorldConfig.ARENA_HEIGHT, preset.label());
        }
        assertEquals(EnumSet.allOf(BlockKind.class), kinds,
            "every kind has to be placeable, or the kind is unreachable from the editor");
        assertEquals(1, doors, "exactly one preset needs a door id, and it is the door");
    }

    @Test
    @DisplayName("a map made of one tap per preset loads, re-saves and does not fail the audit")
    void aPaletteAuthoredMapIsAMap() {
        MapDraft draft = MapDraft.empty(AXIS);
        draft.centred(new MapBlock(BlockKind.WALL, Material.STONE, new Rect(0f, 0f, 3000f, 100f)));
        draft.spawn(new SpawnPoint(0, 300f, 100f));
        draft.spawn(new SpawnPoint(1, 2700f, 100f));

        // One tap per preset, spread out so no two presets share a rectangle: a duplicate block is
        // a load error and a test that authored one would be testing the builder, not the palette.
        float[][] taps = {
            {300f, 400f}, {600f, 400f}, {900f, 600f}, {1150f, 600f}, {1300f, 600f}, {700f, 900f},
        };
        List<MapPalette.Preset> presets = MapPalette.presets();
        assertEquals(taps.length, presets.size(), "the taps have to cover the palette");
        for (int index = 0; index < presets.size(); index++) {
            place(draft, presets.get(index), taps[index][0], taps[index][1]);
        }

        ArenaMap map = draft.build();
        assertEquals(1 + 2 * presets.size(), map.blocks().size(),
            "a centred floor plus one mirrored pair per preset, none of the taps centred");
        assertEquals(List.of(1, 2), doorIds(map), "the mirrored door pair takes ids 1 and 2");

        ArenaMap reparsed = ArenaMapFormat.parse(ArenaMapFormat.write(map));
        assertEquals(map.blocks(), reparsed.blocks());
        assertEquals(map.mapHashHex(), reparsed.mapHashHex());

        MapAudit.Result audit = MapAudit.check(reparsed, "palette");
        assertTrue(audit.reachability().spawnsConnected(),
            "a palette-authored map keeps its spawns in touch: " + audit.lines());
        assertFalse(audit.failed(), audit.lines().toString());
    }

    @Test
    @DisplayName("a tap snaps, stays inside the arena, and becomes centred on the axis")
    void aTapSnapsAndFindsItsVerb() {
        MapPalette.Preset ledge = preset("Ledge");

        Rect snapped = MapPalette.tapRect(ledge, 237.5f, 401f, AXIS);
        assertEquals(new Rect(180f, 391f, 120f, 18f), snapped, "the tap centre snaps to 5 units");
        assertFalse(MapPalette.isCentred(snapped, AXIS), "a ledge at x 180 is one of a pair");

        Rect atTheWall = MapPalette.tapRect(ledge, 10f, 400f, AXIS);
        assertEquals(0f, atTheWall.left(), "a tap at the edge is pushed inside, not clipped");
        assertEquals(120f, atTheWall.width(), "and keeps its authored size");

        Rect straddling = MapPalette.tapRect(ledge, 1510f, 400f, AXIS);
        assertEquals(new Rect(1440f, 391f, 120f, 18f), straddling,
            "a tap that would straddle the axis is re-centred on it");
        assertTrue(MapPalette.isCentred(straddling, AXIS));

        Rect aboveTheCeiling = MapPalette.tapRect(ledge, 300f, 1995f, AXIS);
        assertEquals(WorldConfig.ARENA_HEIGHT, aboveTheCeiling.top());
        assertTrue(aboveTheCeiling.bottom() >= 0f);
    }

    @Test
    @DisplayName("door ids are allotted in sequence, and a door without an id is refused")
    void doorIdsAreAllottedInSequence() {
        assertEquals(1, MapPalette.nextDoorId(List.of()), "a map with no door starts at 1");
        assertEquals(3, MapPalette.nextDoorId(ArenaMap.standard().blocks()),
            "the shipped arena authors door ids 1 and 2, so the next one is 3");

        MapPalette.Preset door = preset("Door");
        MapDraft draft = MapDraft.empty(AXIS);
        MapBlock block = door.block(new Rect(1400f, 100f, 36f, 170f),
            DoorSpec.all(MapPalette.nextDoorId(draft.blocks())));
        draft.mirrored(block);

        assertEquals(List.of(1, 2), doorIds(draft.build()),
            "the authored door is 1 and its reflection takes the next id");
        assertEquals(3, MapPalette.nextDoorId(draft.blocks()));

        assertThrows(IllegalArgumentException.class,
            () -> door.block(new Rect(1400f, 100f, 36f, 170f), DoorSpec.NONE),
            "a door preset without an id is exactly the block MapBlock refuses");
        assertThrows(IllegalArgumentException.class,
            () -> preset("Crate").block(new Rect(200f, 100f, 60f, 60f), DoorSpec.all(7)),
            "and only a door may carry a door spec");
    }

    /** The editor's placement, in the three calls its tap handler makes. */
    private static void place(MapDraft draft, MapPalette.Preset preset, float tapX, float tapY) {
        Rect rect = MapPalette.tapRect(preset, tapX, tapY, draft.mirrorAxisX());
        DoorSpec spec = preset.door()
            ? DoorSpec.all(MapPalette.nextDoorId(draft.blocks()))
            : DoorSpec.NONE;
        MapBlock block = preset.block(rect, spec);
        if (MapPalette.isCentred(rect, draft.mirrorAxisX())) {
            draft.centred(block);
        } else {
            draft.mirrored(block);
        }
    }

    private static MapPalette.Preset preset(String label) {
        for (MapPalette.Preset preset : MapPalette.presets()) {
            if (preset.label().equals(label)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("no preset labelled " + label);
    }

    private static List<Integer> doorIds(ArenaMap map) {
        List<Integer> ids = new ArrayList<>();
        for (MapBlock block : map.blocks()) {
            if (block.door().isDoor()) {
                ids.add(block.door().id());
            }
        }
        ids.sort(Integer::compare);
        return ids;
    }
}
