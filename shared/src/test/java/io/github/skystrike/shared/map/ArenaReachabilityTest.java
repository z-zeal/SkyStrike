package io.github.skystrike.shared.map;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for the M8 traversal contract.
 *
 * <p>The flood fill models every exposed platform that is at least one player wide. Narrow wall
 * caps and the 16/22/24-unit support tops are deliberately excluded: a 30-unit player cannot use
 * them as stable standing surfaces, and the boundary-wall crowns have no standing clearance below
 * the ceiling. The graph uses the actual jump apex and the plan's 210-unit running-gap envelope.
 */
class ArenaReachabilityTest {

    private static final float EPSILON = 0.01f;
    private static final float MAX_JUMP_RISE = PlayerConfig.JUMP_SPEED * PlayerConfig.JUMP_SPEED
        / (2f * -PlayerConfig.GRAVITY);
    private static final float MAX_JUMP_GAP = 210f;
    private static final float FUEL_BUDGET = PlayerConfig.MAX_FUEL * 0.40f;
    private static final float JETPACK_ACCELERATION = PlayerConfig.JETPACK_THRUST
        * PlayerConfig.JETPACK_VERTICAL_SPLIT + PlayerConfig.GRAVITY;

    @Test
    @DisplayName("every gameplay platform is reachable from either spawn and can be exited without fuel")
    void everyGameplaySurfaceIsReachableAndEscapable() {
        ArenaMap map = ArenaMap.standard();
        List<Surface> surfaces = gameplaySurfaces();

        for (SpawnPoint spawn : map.spawns()) {
            Surface start = spawnSurface(map, spawn);
            Set<Surface> onFoot = floodFrom(start, surfaces);
            Set<Surface> canExitWithoutFuel = reverseFloodTo(start, surfaces);

            for (Surface surface : surfaces) {
                boolean reachable = onFoot.contains(surface) || reachableWithFuel(onFoot, surface);
                assertTrue(
                    reachable,
                    surface.name + " is not reachable from team " + spawn.teamIndex()
                        + " within the M8 foot/40%-fuel envelope");
                assertTrue(
                    canExitWithoutFuel.contains(surface),
                    surface.name + " is a no-fuel trap for team " + spawn.teamIndex());
            }
        }
    }

    private static Set<Surface> floodFrom(Surface start, List<Surface> surfaces) {
        Set<Surface> reached = new HashSet<>();
        ArrayDeque<Surface> pending = new ArrayDeque<>();
        pending.add(start);

        while (!pending.isEmpty()) {
            Surface from = pending.removeFirst();
            for (Surface target : surfaces) {
                if (reached.contains(target) || !canTraverse(from, target)) {
                    continue;
                }
                reached.add(target);
                pending.addLast(target);
            }
        }
        return reached;
    }

    private static Set<Surface> reverseFloodTo(Surface destination, List<Surface> surfaces) {
        Set<Surface> reached = new HashSet<>();
        ArrayDeque<Surface> pending = new ArrayDeque<>();
        pending.add(destination);

        while (!pending.isEmpty()) {
            Surface target = pending.removeFirst();
            for (Surface candidate : surfaces) {
                if (reached.contains(candidate) || !canTraverse(candidate, target)) {
                    continue;
                }
                reached.add(candidate);
                pending.addLast(candidate);
            }
        }
        return reached;
    }

    private static boolean canTraverse(Surface from, Surface to) {
        float riseToLandingEdge = to.bottom - from.top;
        return riseToLandingEdge <= MAX_JUMP_RISE + EPSILON
            && from.horizontalGap(to) <= MAX_JUMP_GAP + EPSILON;
    }

    private static boolean reachableWithFuel(Set<Surface> footSurfaces, Surface target) {
        for (Surface launch : footSurfaces) {
            float riseToLandingEdge = Math.max(0f, target.bottom - launch.top);
            float flightSeconds = jetpackSecondsForRise(riseToLandingEdge);
            float fuelUsed = flightSeconds * PlayerConfig.FUEL_BURN_RATE;
            float horizontalReach = PlayerConfig.WALK_SPEED * flightSeconds;
            if (fuelUsed <= FUEL_BUDGET + EPSILON
                && launch.horizontalGap(target) <= horizontalReach + EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static float jetpackSecondsForRise(float rise) {
        if (rise <= 0f) {
            return 0f;
        }
        float discriminant = PlayerConfig.JUMP_SPEED * PlayerConfig.JUMP_SPEED
            + 2f * JETPACK_ACCELERATION * rise;
        return ((float) Math.sqrt(discriminant) - PlayerConfig.JUMP_SPEED) / JETPACK_ACCELERATION;
    }

    private static Surface spawnSurface(ArenaMap map, SpawnPoint spawn) {
        float ground = MapQueries.surfaceBelow(map, spawn.x(), spawn.y());
        float halfWidth = PlayerConfig.WIDTH / 2f;
        return new Surface(
            "team " + spawn.teamIndex() + " spawn",
            spawn.x() - halfWidth,
            spawn.x() + halfWidth,
            ground,
            ground);
    }

    private static List<Surface> gameplaySurfaces() {
        List<Surface> surfaces = new ArrayList<>();
        add(surfaces, "ground", new Rect(0f, 0f, WorldConfig.ARENA_WIDTH, WorldConfig.GROUND_HEIGHT));

        for (int step = 0; step < 5; step++) {
            addMirrored(surfaces, "ramp " + step, new Rect(
                120f + step * 120f, 200f + step * 50f, 120f, 18f));
        }
        addMirrored(surfaces, "mid lane", new Rect(820f, 500f, 250f, 18f));
        addMirrored(surfaces, "lower crate top", new Rect(820f, 220f, 70f, 16f));
        addMirrored(surfaces, "upper crate top", new Rect(890f, 260f, 70f, 16f));

        addMirrored(surfaces, "outer room floor", new Rect(1120f, 280f, 75f, 20f));
        add(surfaces, "centre room floor", new Rect(1285f, 280f, 430f, 20f));
        addMirrored(surfaces, "wide tunnel roof lip", new Rect(1120f, 190f, 75f, 14f));
        addMirrored(surfaces, "tunnel hatch step", new Rect(1195f, 192f, 90f, 18f));

        addMirrored(surfaces, "doorway landing", new Rect(1000f, 342f, 120f, 18f));
        addMirrored(surfaces, "room access riser", new Rect(1220f, 400f, 100f, 18f));
        addMirrored(surfaces, "catwalk shelf", new Rect(1100f, 520f, 120f, 18f));
        add(surfaces, "catwalk", new Rect(1180f, 620f, 640f, 20f));
        addMirrored(surfaces, "perch ladder", new Rect(1300f, 700f, 100f, 16f));
        add(surfaces, "sniper perch", new Rect(1380f, 760f, 240f, 18f));

        addMirrored(surfaces, "outer upper ledge", new Rect(760f, 900f, 240f, 18f));
        add(surfaces, "high centre platform", new Rect(1380f, 1000f, 240f, 18f));
        return surfaces;
    }

    private static void addMirrored(List<Surface> surfaces, String name, Rect left) {
        add(surfaces, name + " left", left);
        add(surfaces, name + " right", left.mirror(WorldConfig.MIRROR_AXIS_X));
    }

    private static void add(List<Surface> surfaces, String name, Rect rect) {
        surfaces.add(new Surface(name, rect.left(), rect.right(), rect.bottom(), rect.top()));
    }

    private record Surface(String name, float left, float right, float bottom, float top) {

        float horizontalGap(Surface other) {
            if (right >= other.left && other.right >= left) {
                return 0f;
            }
            return Math.max(other.left - right, left - other.right);
        }
    }
}
