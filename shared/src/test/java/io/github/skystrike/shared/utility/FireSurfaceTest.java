package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.utility.FireSurface.Frame;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The molotov surface frame: the tangent is recovered from the cast's own patch line, the normal
 * from a short probe against the map, and the flame direction falls out of both — the rule both
 * the persistent flame renderer and the sustained ember emitter read.
 */
class FireSurfaceTest {

    private static final float GROUND = WorldConfig.GROUND_HEIGHT;

    private static UtilityZone fire(int id, float x, float y) {
        return new UtilityZone(
            id, 1, 0, UtilityId.MOLOTOV.ordinal(), x, y, UtilityConfig.FIRE_ZONE_RADIUS,
            21f, 6.0f);
    }

    @Test
    @DisplayName("a floor cast resolves an up normal and flames that rise")
    void floorCast() {
        UtilityZone centre = fire(1, 1500f, GROUND + 5f);
        UtilityZone sibling = fire(2, 1550f, GROUND + 5f);
        Frame frame = FireSurface.of(centre, List.of(centre, sibling), ArenaMap.standard());

        assertEquals(0f, frame.normalX(), 0.02f, "the normal is vertical on a floor");
        assertEquals(1f, frame.normalY(), 0.02f, "and points out of the ground");
        assertEquals(1f, (float) Math.hypot(frame.axisX(), frame.axisY()), 0.001f);
        assertEquals(0f, frame.flameX(), 0.02f);
        assertEquals(1f, frame.flameY(), 0.02f, "floor fire rises with buoyancy");
    }

    @Test
    @DisplayName("a lone patch in open space falls back to rising fire with no spread axis")
    void openSkyFallback() {
        UtilityZone lone = fire(1, 1500f, GROUND + 400f);
        Frame frame = FireSurface.of(lone, List.of(lone), ArenaMap.standard());

        assertEquals(0f, frame.axisX(), 0.001f);
        assertEquals(0f, frame.axisY(), 0.001f);
        assertEquals(0f, frame.flameX(), 0.001f);
        assertEquals(1f, frame.flameY(), 0.001f);
    }

    @Test
    @DisplayName("no map and no siblings is plain rising fire, never a crash")
    void missingInputsFallBack() {
        assertEquals(0f, FireSurface.of(null, null, null).flameX(), 0.001f);
        assertEquals(1f, FireSurface.of(null, null, null).flameY(), 0.001f);
        UtilityZone lone = fire(1, 1500f, GROUND + 5f);
        Frame frame = FireSurface.of(lone, null, null);
        assertEquals(1f, frame.flameY(), 0.001f, "a null map is open ground: fire rises");
    }

    @Test
    @DisplayName("a cast's axis is a unit vector through the nearest sibling, either way round")
    void axisIsUnitAndSiblingBased() {
        UtilityZone centre = fire(1, 1500f, GROUND + 5f);
        UtilityZone left = fire(2, 1450f, GROUND + 5f);
        UtilityZone right = fire(3, 1550f, GROUND + 5f);
        Frame frame = FireSurface.of(centre, List.of(left, centre, right), ArenaMap.standard());
        assertEquals(1f, (float) Math.hypot(frame.axisX(), frame.axisY()), 0.001f);
        assertTrue(Math.abs(frame.axisY()) < 0.02f, "the spread line lies along the floor");

        // A different cast (other owner) is never merged into the surface line.
        UtilityZone stranger = fire(4, 1550f, GROUND + 5f);
        stranger.ownerId = 2;
        Frame alone = FireSurface.of(centre, List.of(centre, stranger), ArenaMap.standard());
        assertEquals(0f, alone.axisX(), 0.001f, "a stranger's patch is not a sibling");
    }
}
