package io.github.skystrike.shared.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.DroneEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The drone motion routine (mechanics §7.1): velocity lerp and damping, free flight in all four
 * directions, arena clamping and wall push-out. Both the server and the client prediction run
 * this exact code, so these tests pin the feel both sides share.
 */
class DroneMotionTest {

    private static final float DT = 1f / 60f;

    private final ArenaMap map = ArenaMap.standard();

    private static DroneEntity droneAt(float x, float y) {
        return new DroneEntity(1, 1, 0, x, y, 0f, GadgetConfig.DRONE_HEALTH);
    }

    @Test
    @DisplayName("piloted input accelerates responsively but still eases toward the flight speed")
    void pilotedInputLerpsVelocity() {
        DroneEntity drone = droneAt(400f, 600f);
        DroneMotion.stepInPlace(drone, 1f, false, false, DT, map);

        assertTrue(drone.vx > 0f, "the drone accelerates right");
        assertTrue(drone.vx > GadgetConfig.DRONE_SPEED * 0.2f,
            "the 16/s response rate gets the faster drone moving promptly");
        assertTrue(drone.vx < GadgetConfig.DRONE_SPEED,
            "one tick is a lerp toward the speed, not an instant reach");
        assertTrue(drone.x > 400f, "and it moves");
        assertEquals(0f, drone.vy, 1e-6f);
    }

    @Test
    @DisplayName("with no input the velocity damps toward a hover instead of stopping dead")
    void noInputDampsToHover() {
        DroneEntity drone = droneAt(400f, 600f);
        drone.vx = GadgetConfig.DRONE_SPEED;

        DroneMotion.stepInPlace(drone, 0f, false, false, DT, map);

        assertTrue(drone.vx > 0f, "damping is a coast, not a stop");
        assertTrue(drone.vx < GadgetConfig.DRONE_SPEED, "but it is losing speed");
        assertTrue(drone.x > 400f, "the drone carries its momentum");
    }

    @Test
    @DisplayName("up climbs and down descends at the same flight speed")
    void verticalFlight() {
        DroneEntity climbing = droneAt(400f, 600f);
        DroneMotion.stepInPlace(climbing, 0f, true, false, DT, map);
        assertTrue(climbing.vy > 0f, "up is +Y");
        assertTrue(climbing.vy < GadgetConfig.DRONE_SPEED);

        DroneEntity descending = droneAt(400f, 600f);
        DroneMotion.stepInPlace(descending, 0f, false, true, DT, map);
        assertTrue(descending.vy < 0f, "down is -Y");

        DroneEntity both = droneAt(400f, 600f);
        DroneMotion.stepInPlace(both, 0f, true, true, DT, map);
        assertEquals(0f, both.vy, 1e-6f, "up and down cancel");
    }

    @Test
    @DisplayName("the arena shell stops the drone flush with its inner face")
    void arenaShellStopsTheDrone() {
        // Open sky at the arena's east edge: the shell wall's inner face is x = 2960
        // (3000 wide minus the 40-unit boundary), and the drone stops radius-out from it.
        DroneEntity drone = droneAt(WorldConfig.ARENA_WIDTH - 60f, 1500f);
        drone.vx = GadgetConfig.DRONE_SPEED;
        for (int i = 0; i < 60; i++) {
            DroneMotion.stepInPlace(drone, 1f, false, false, DT, map);
        }
        assertEquals(2960f - GadgetConfig.DRONE_RADIUS, drone.x, 1e-3f,
            "the drone stops flush with the arena edge, radius in");
        assertEquals(0f, drone.vx, 1e-6f, "the velocity into the boundary is spent");
    }

    @Test
    @DisplayName("a wall stops the drone flush with its face and kills the velocity into it")
    void wallStopsTheDrone() {
        // Open air between the mid lane and the centre room's left wall (x=1120, y=300..860).
        DroneEntity drone = droneAt(1100f, 600f);
        for (int i = 0; i < 120; i++) {
            DroneMotion.stepInPlace(drone, 1f, false, false, DT, map);
        }
        assertEquals(1120f - GadgetConfig.DRONE_RADIUS, drone.x, 1e-3f,
            "the drone rests against the wall face, radius out");
        assertEquals(0f, drone.vx, 1e-6f, "the velocity into the wall is spent");
        assertEquals(600f, drone.y, 1e-3f, "the wall does not deflect it vertically");
    }

    @Test
    @DisplayName("a drone spawned inside geometry is pushed out along the least-penetration axis")
    void depenetrationPushesOutOfSolids() {
        // Inside the centre room's left wall (x=1120..1144, y=300..860).
        DroneEntity drone = droneAt(1130f, 600f);
        DroneMotion.stepInPlace(drone, 0f, false, false, DT, map);

        float radius = GadgetConfig.DRONE_RADIUS;
        boolean clear = drone.x + radius <= 1120f + 1e-3f
            || drone.x - radius >= 1144f - 1e-3f
            || drone.y + radius <= 300f + 1e-3f
            || drone.y - radius >= 860f - 1e-3f;
        assertTrue(clear, "the drone ends outside the wall: " + drone);
        assertEquals(1120f - radius, drone.x, 1e-3f,
            "the least-penetration axis is out through the near face");
    }

    @Test
    @DisplayName("prevX/prevY track the tick's start so the client can interpolate")
    void prevPositionTracks() {
        DroneEntity drone = droneAt(400f, 600f);
        drone.vx = 100f;
        DroneMotion.stepInPlace(drone, 0f, false, false, DT, map);
        assertEquals(400f, drone.prevX, 1e-6f);
        assertEquals(600f, drone.prevY, 1e-6f);
        assertTrue(drone.x > drone.prevX);
    }

    @Test
    @DisplayName("a non-positive dt is a no-op")
    void nonPositiveDtIsNoOp() {
        DroneEntity drone = droneAt(400f, 600f);
        drone.vx = 100f;
        DroneMotion.stepInPlace(drone, 1f, true, false, 0f, map);
        DroneMotion.stepInPlace(drone, 1f, true, false, -1f, map);
        assertEquals(400f, drone.x, 1e-6f);
        assertEquals(600f, drone.y, 1e-6f);
        assertEquals(100f, drone.vx, 1e-6f);
    }
}
