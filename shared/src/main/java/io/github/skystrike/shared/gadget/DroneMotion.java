package io.github.skystrike.shared.gadget;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Geometry;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.DroneEntity;

/**
 * The one drone motion routine (mechanics §7.1): free flight at {@link GadgetConfig#DRONE_SPEED}
 * with smooth velocity lerping and damping, clamped to the arena and pushed out of walls.
 *
 * <p>Pure, like {@code PlayerMotion} and for the same reason: the server steps the authoritative
 * drone with it and the client's prediction steps the same entity with it, so the piloted device
 * cannot drift between what the player steers and what the server records.
 *
 * <p>Input mapping while piloting: {@code moveX} steers left/right, {@code up} (jump or jetpack
 * held) climbs and {@code down} (crouch held) descends — the movement keys drive the device,
 * exactly as mechanics §9 puts it. With no input the drone does not stop dead: velocity damps at
 * {@link GadgetConfig#DRONE_DAMPING}, so a released drone coasts to a hover.
 *
 * <p>Collision is a box test at the target position per axis, not a swept segment: at 500 u/s a
 * drone covers 8.3 units per 60 Hz tick, inside the 14-unit thinnest arena geometry, so it cannot
 * tunnel at the authoritative tick rate. A final depenetration pass pushes the drone out of any
 * solid it still overlaps — which is also what makes a deploy point inside a low ceiling survivable.
 */
public final class DroneMotion {

    private DroneMotion() {
    }

    /**
     * Steps one drone forward by {@code dt} seconds, in place.
     *
     * @param drone  the drone to move; never null-checked away, callers guard
     * @param moveX  horizontal intent, -1 (left) to 1 (right)
     * @param up     climb intent (jump or jetpack held while piloting)
     * @param down   descend intent (crouch held while piloting)
     * @param dt     tick seconds
     * @param map    the arena, for walls and bounds
     */
    public static void stepInPlace(
            DroneEntity drone,
            float moveX,
            boolean up,
            boolean down,
            float dt,
            ArenaMap map) {
        if (drone == null || dt <= 0f) {
            return;
        }

        drone.prevX = drone.x;
        drone.prevY = drone.y;

        // 1. Velocity: lerp toward the piloted direction, damp toward rest with no input.
        float targetVx = Lerp.clamp(moveX, -1f, 1f) * GadgetConfig.DRONE_SPEED;
        float targetVy = (up ? 1f : 0f) - (down ? 1f : 0f);
        targetVy *= GadgetConfig.DRONE_SPEED;
        boolean piloted = moveX != 0f || up || down;
        float rate = piloted ? GadgetConfig.DRONE_VELOCITY_LERP_RATE : GadgetConfig.DRONE_DAMPING;
        drone.vx = Lerp.smooth(drone.vx, targetVx, rate, dt);
        drone.vy = Lerp.smooth(drone.vy, targetVy, rate, dt);
        if (Math.abs(drone.vx) < 0.001f) {
            drone.vx = 0f;
        }
        if (Math.abs(drone.vy) < 0.001f) {
            drone.vy = 0f;
        }

        // 2. Integrate one axis at a time, stopping at the first wall face on that axis.
        float radius = GadgetConfig.DRONE_RADIUS;
        drone.x = moveAxisX(drone.x, drone.y, drone.vx * dt, radius, map);
        if (movedLessThan(drone.vx * dt, drone.x, drone.prevX)) {
            drone.vx = 0f;
        }
        drone.y = moveAxisY(drone.x, drone.y, drone.vy * dt, radius, map);
        if (movedLessThan(drone.vy * dt, drone.y, drone.prevY)) {
            drone.vy = 0f;
        }

        // 3. The arena bounds are walls too.
        drone.x = Lerp.clamp(drone.x, radius, WorldConfig.ARENA_WIDTH - radius);
        drone.y = Lerp.clamp(drone.y, radius, WorldConfig.ARENA_HEIGHT - radius);

        // 4. Safety net: never rest inside geometry (a deploy under a low ceiling lands here).
        depenetrate(drone, map);
    }

    /** Moves along X by {@code dx}, stopping flush at the nearest solid face ahead. */
    private static float moveAxisX(float x, float y, float dx, float radius, ArenaMap map) {
        if (dx > 0f) {
            float allowed = dx;
            for (Rect solid : map.solids()) {
                if (!yOverlaps(solid, y, radius)) {
                    continue;
                }
                float boxRight = x + radius;
                if (solid.left() >= boxRight && solid.left() <= boxRight + allowed) {
                    allowed = solid.left() - boxRight;
                }
            }
            return x + Math.max(0f, allowed);
        }
        if (dx < 0f) {
            float allowed = dx;
            for (Rect solid : map.solids()) {
                if (!yOverlaps(solid, y, radius)) {
                    continue;
                }
                float boxLeft = x - radius;
                if (solid.right() <= boxLeft && solid.right() >= boxLeft + allowed) {
                    allowed = solid.right() - boxLeft;
                }
            }
            return x + Math.min(0f, allowed);
        }
        return x;
    }

    /** Moves along Y by {@code dy}, stopping flush at the nearest solid face ahead. */
    private static float moveAxisY(float x, float y, float dy, float radius, ArenaMap map) {
        if (dy > 0f) {
            float allowed = dy;
            for (Rect solid : map.solids()) {
                if (!xOverlaps(solid, x, radius)) {
                    continue;
                }
                float boxTop = y + radius;
                if (solid.bottom() >= boxTop && solid.bottom() <= boxTop + allowed) {
                    allowed = solid.bottom() - boxTop;
                }
            }
            return y + Math.max(0f, allowed);
        }
        if (dy < 0f) {
            float allowed = dy;
            for (Rect solid : map.solids()) {
                if (!xOverlaps(solid, x, radius)) {
                    continue;
                }
                float boxBottom = y - radius;
                if (solid.top() <= boxBottom && solid.top() >= boxBottom + allowed) {
                    allowed = solid.top() - boxBottom;
                }
            }
            return y + Math.min(0f, allowed);
        }
        return y;
    }

    /** True when the drone's vertical span crosses the solid's vertical span. */
    private static boolean yOverlaps(Rect solid, float y, float radius) {
        return y + radius > solid.bottom() && y - radius < solid.top();
    }

    /** True when the drone's horizontal span crosses the solid's horizontal span. */
    private static boolean xOverlaps(Rect solid, float x, float radius) {
        return x + radius > solid.left() && x - radius < solid.right();
    }

    /**
     * Pushes the drone out of any solid it still overlaps, along the axis of least
     * penetration, and kills the velocity component that drove it in.
     */
    private static void depenetrate(DroneEntity drone, ArenaMap map) {
        float radius = GadgetConfig.DRONE_RADIUS;
        Rect box = new Rect(drone.x - radius, drone.y - radius, radius * 2f, radius * 2f);
        Rect blocker = null;
        for (Rect solid : map.solids()) {
            if (Geometry.aabbOverlaps(solid, box)) {
                blocker = solid;
                break;
            }
        }
        if (blocker == null) {
            return;
        }
        float pushLeft = box.right() - blocker.left();
        float pushRight = blocker.right() - box.left();
        float pushDown = box.top() - blocker.bottom();
        float pushUp = blocker.top() - box.bottom();
        float least = Math.min(Math.min(pushLeft, pushRight), Math.min(pushDown, pushUp));
        if (least == pushLeft) {
            drone.x -= pushLeft;
            drone.vx = Math.min(drone.vx, 0f);
        } else if (least == pushRight) {
            drone.x += pushRight;
            drone.vx = Math.max(drone.vx, 0f);
        } else if (least == pushDown) {
            drone.y -= pushDown;
            drone.vy = Math.min(drone.vy, 0f);
        } else {
            drone.y += pushUp;
            drone.vy = Math.max(drone.vy, 0f);
        }
    }

    private static boolean movedLessThan(float intended, float after, float before) {
        return Math.abs(after - before) < Math.abs(intended) - 1e-6f;
    }
}
