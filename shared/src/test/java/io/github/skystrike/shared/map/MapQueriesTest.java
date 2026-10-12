package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapQueriesTest {

    private final ArenaMap map = ArenaMap.standard();

    @Test
    void groundIsSolidAndTheAirAboveItIsNot() {
        assertTrue(MapQueries.solidAt(map, 1500f, 50f));
        assertFalse(MapQueries.solidAt(map, 1500f, 150f));
    }

    @Test
    @DisplayName("the centre room's upper shell blocks sight across the arena")
    void centreGeometryBlocksLongSightLines() {
        assertTrue(MapQueries.lineBlocked(map, 100f, 500f, 2900f, 500f));
    }

    @Test
    void anUnobstructedLineIsNotBlocked() {
        assertFalse(MapQueries.lineBlocked(map, 200f, 1200f, 600f, 1200f));
    }

    @Test
    void firstBlockerIsTheNearestOne() {
        Rect blocker = MapQueries.firstBlocker(map, 1500f, 1200f, 1500f, 0f);
        assertNotNull(blocker);
        assertEquals(1000f, blocker.bottom(), 0f, "expected the upper platform directly below");
    }

    @Test
    void firstBlockerIsNullOnAClearLine() {
        assertNull(MapQueries.firstBlocker(map, 200f, 1200f, 600f, 1200f));
    }

    @Test
    void surfaceBelowFindsTheGroundWhenNothingIsInTheWay() {
        assertEquals(WorldConfig.GROUND_HEIGHT, MapQueries.surfaceBelow(map, 1500f, 150f), 0f);
    }

    @Test
    void surfaceBelowFindsTheHighestPlatformUnderneath() {
        assertEquals(300f, MapQueries.surfaceBelow(map, 1500f, 400f), 0f);
    }

    @Test
    @DisplayName("doorAt resolves the nearest door within reach, and only within reach")
    void doorAtFindsTheNearestDoorInReach() {
        ArenaMap doors = ArenaMap.builder(WorldConfig.MIRROR_AXIS_X)
            .mirrored(door(1, new Rect(700f, 450f, 24f, 90f)))
            .mirrored(door(3, new Rect(800f, 450f, 24f, 90f)))
            .build();

        assertEquals(
            new Rect(700f, 450f, 24f, 90f),
            MapQueries.doorAt(doors, 680f, 460f, 40f).rect(),
            "the door 20 units away, not the one beyond it");
        assertEquals(
            new Rect(800f, 450f, 24f, 90f),
            MapQueries.doorAt(doors, 820f, 460f, 100f).rect(),
            "the nearest of two doors in reach");
        assertNull(MapQueries.doorAt(doors, 500f, 460f, 100f), "nothing within reach");
        assertNull(MapQueries.doorAt(doors, 680f, 460f, 0f), "a zero reach reaches nothing");

        // Exactly between two doors, the earlier authored block answers: the client predicting a
        // press and the server resolving it must not be able to choose differently.
        ArenaMap tie = ArenaMap.builder(WorldConfig.MIRROR_AXIS_X)
            .mirrored(door(1, new Rect(700f, 450f, 24f, 90f)))
            .mirrored(door(3, new Rect(976f, 450f, 24f, 90f)))
            .build();
        assertEquals(1, MapQueries.doorAt(tie, 850f, 460f, 200f).door().id());
    }

    @Test
    void doorAtReadsTheAuthoredList() {
        ArenaMap doors = ArenaMap.builder(WorldConfig.MIRROR_AXIS_X)
            .mirrored(door(1, new Rect(700f, 450f, 24f, 90f)))
            .build();

        // A live map has already left an open door out, so the query is given a map that still
        // holds it — shutting a door that is standing open is exactly what the key is for.
        assertNotNull(MapQueries.doorAt(doors, 680f, 460f, 40f));
        assertNull(MapQueries.doorAt(null, 680f, 460f, 40f));
    }

    private static MapBlock door(int id, Rect rect) {
        return new MapBlock(BlockKind.DOOR, Material.METAL, rect,
            MapBlock.DEFAULT_DURABILITY_MULTIPLIER, DoorSpec.all(id));
    }

    @Test
    void overlapDetectsTheRoomFloor() {
        assertTrue(MapQueries.overlapsSolid(map, new Rect(1490f, 285f, 20f, 20f)));
        assertFalse(MapQueries.overlapsSolid(map, new Rect(1490f, 320f, 20f, 20f)));
    }
}
