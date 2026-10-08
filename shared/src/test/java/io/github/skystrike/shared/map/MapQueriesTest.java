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
    void overlapDetectsTheRoomFloor() {
        assertTrue(MapQueries.overlapsSolid(map, new Rect(1490f, 285f, 20f, 20f)));
        assertFalse(MapQueries.overlapsSolid(map, new Rect(1490f, 320f, 20f, 20f)));
    }
}
