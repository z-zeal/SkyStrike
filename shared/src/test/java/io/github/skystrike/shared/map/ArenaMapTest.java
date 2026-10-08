package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The arena is the only piece of Phase 0 content, so it gets the only content tests: that it is
 * symmetric, and that every feature mechanics §1 names is actually present.
 */
class ArenaMapTest {

    private static final float AXIS = WorldConfig.MIRROR_AXIS_X;

    private final ArenaMap map = ArenaMap.standard();

    @Test
    @DisplayName("reflecting every rect about x=1500 reproduces the same set")
    void layoutIsMirrorSymmetric() {
        Set<Rect> original = new HashSet<>(map.solids());
        Set<Rect> reflected = new HashSet<>();
        for (Rect rect : map.solids()) {
            reflected.add(rect.mirror(AXIS));
        }
        assertEquals(original, reflected, "mirrored layout differs from the original");
    }

    @Test
    @DisplayName("no two solids are the same rectangle")
    void layoutHasNoDuplicates() {
        Set<Rect> unique = new HashSet<>(map.solids());
        assertEquals(map.solids().size(), unique.size(), "duplicate rectangles in the layout");
    }

    @Test
    void everySolidLiesInsideTheArena() {
        Rect bounds = map.bounds();
        for (Rect rect : map.solids()) {
            assertTrue(
                rect.left() >= bounds.left()
                    && rect.right() <= bounds.right()
                    && rect.bottom() >= bounds.bottom()
                    && rect.top() <= bounds.top(),
                "rect escapes the arena: " + rect);
        }
    }

    @Test
    void groundPlaneSpansTheArenaAtHeight100() {
        assertContains(new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.GROUND_HEIGHT));
    }

    @Test
    @DisplayName("five 120x18 ramp steps per side, 120 across and 50 up each time")
    void bothRampsArePresent() {
        for (int step = 0; step < 5; step++) {
            Rect left = new Rect(120f + step * 120f, 200f + step * 50f, 120f, 18f);
            assertContains(left);
            assertContains(left.mirror(AXIS));
        }
        assertContains(new Rect(600f, 400f, 120f, 18f));
    }

    @Test
    void midLanesArePresent() {
        assertMirroredPair(new Rect(820f, 500f, 250f, 18f));
    }

    @Test
    @DisplayName("a 70x120 crate and a 70x160 crate per side, with standable tops at 220 and 260")
    void crateStacksArePresent() {
        assertMirroredPair(new Rect(820f, 100f, 70f, 120f));
        assertMirroredPair(new Rect(820f, 220f, 70f, 16f));
        assertMirroredPair(new Rect(890f, 100f, 70f, 160f));
        assertMirroredPair(new Rect(890f, 260f, 70f, 16f));
    }

    @Test
    void lanePillarsArePresent() {
        assertMirroredPair(new Rect(1010f, 100f, 24f, 260f));
    }

    @Test
    void tunnelSupportsArePresent() {
        assertMirroredPair(new Rect(1070f, 100f, 22f, 170f));
    }

    @Test
    @DisplayName("centre room has doorway jambs, two hatches, inner pillars and a catwalk")
    void centreRoomIsPresent() {
        assertMirroredPair(new Rect(1120f, 280f, 75f, 20f));
        assertContains(new Rect(1285f, 280f, 430f, 20f));

        assertMirroredPair(new Rect(1120f, 300f, 24f, 60f));
        assertMirroredPair(new Rect(1120f, 410f, 24f, 450f));
        assertMirroredPair(new Rect(1184f, 300f, 16f, 130f));
        assertContains(new Rect(1180f, 620f, 640f, 20f));
    }

    @Test
    @DisplayName("the shell doorway is a standing-height opening instead of a sealed wall")
    void shellDoorwaysAreOpen() {
        for (float doorwayX : new float[] {1132f, 1868f}) {
            assertTrue(MapQueries.solidAt(map, doorwayX, 330f), "lower jamb is missing");
            assertFalse(MapQueries.solidAt(map, doorwayX, 385f), "doorway is blocked");
            assertTrue(MapQueries.solidAt(map, doorwayX, 500f), "upper wall is missing");
        }
    }

    @Test
    @DisplayName("two 90-unit hatches join the room floor to the tunnel")
    void tunnelHatchesArePresent() {
        assertFalse(MapQueries.solidAt(map, 1240f, 290f), "left floor hatch is filled");
        assertFalse(MapQueries.solidAt(map, 1760f, 290f), "right floor hatch is filled");
        assertMirroredPair(new Rect(1195f, 192f, 90f, 18f));
    }

    @Test
    void tunnelRoofsLeaveTheHatchesOpen() {
        assertMirroredPair(new Rect(1120f, 190f, 75f, 14f));
        assertMirroredPair(new Rect(1285f, 190f, 15f, 14f));
        assertFalse(MapQueries.solidAt(map, 1240f, 240f), "left hatch has a roof above its step");
        assertFalse(MapQueries.solidAt(map, 1760f, 240f), "right hatch has a roof above its step");
    }

    @Test
    void catwalkAccessIsPresent() {
        assertMirroredPair(new Rect(1000f, 342f, 120f, 18f));
        assertMirroredPair(new Rect(1100f, 520f, 120f, 18f));
        assertMirroredPair(new Rect(1220f, 400f, 100f, 18f));
    }

    @Test
    void sniperPerchHasTheFootstepLadder() {
        assertContains(new Rect(1380f, 760f, 240f, 18f));
        assertMirroredPair(new Rect(1300f, 700f, 100f, 16f));
    }

    @Test
    void upperArenaPlatformsArePresent() {
        assertContains(new Rect(1380f, 1000f, 240f, 18f));
        assertMirroredPair(new Rect(760f, 900f, 240f, 18f));
    }

    @Test
    @DisplayName("the ground tunnel stays open except for its low roof lips and hatch steps")
    void tunnelUnderTheRoomStaysOpen() {
        float tunnelFloor = WorldConfig.GROUND_HEIGHT;
        float underRoomCeiling = 280f;
        Rect corridor = new Rect(
            1150f, tunnelFloor + 1f, 600f, underRoomCeiling - tunnelFloor - 2f);
        List<Rect> blockers = MapQueries.solidsOverlapping(map, corridor);
        for (Rect blocker : blockers) {
            assertTrue(
                blocker.bottom() >= 190f && blocker.top() <= 210f,
                "only low roof lips and hatch steps may sit inside the corridor, found: " + blocker);
        }
    }

    @Test
    void spawnsAreMirroredAndOnOppositeSides() {
        List<SpawnPoint> spawns = map.spawns();
        assertEquals(2, spawns.size());

        SpawnPoint left = spawns.get(0);
        SpawnPoint right = spawns.get(1);

        assertEquals(240f, left.x(), 0f);
        assertEquals(180f, left.y(), 0f);
        assertEquals(2760f, right.x(), 0f);
        assertEquals(180f, right.y(), 0f);
        assertEquals(left.mirror(AXIS).x(), right.x(), 0f);
        assertEquals(0, left.teamIndex());
        assertEquals(1, right.teamIndex());
    }

    @Test
    void spawnsAreNotInsideGeometry() {
        for (SpawnPoint spawn : map.spawns()) {
            assertFalse(
                MapQueries.solidAt(map, spawn.x(), spawn.y()),
                "spawn is embedded in a solid: " + spawn);
        }
    }

    @Test
    void solidsAreImmutable() {
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> map.solids().add(new Rect(0f, 0f, 1f, 1f)));
    }

    private void assertContains(Rect expected) {
        assertTrue(map.solids().contains(expected), "missing from the layout: " + expected);
    }

    private void assertMirroredPair(Rect left) {
        assertContains(left);
        assertContains(left.mirror(AXIS));
    }
}
