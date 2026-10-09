package io.github.skystrike.shared.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical observer set: whose eyes a viewer looks through, and what those eyes light.
 *
 * <p>Every coordinate here is chosen against today's {@link ArenaMap#standard()} geometry. Open
 * air at y≈600 between x=200 and x=700 has nothing in it — the stepped ramp tops out at y≈450 and
 * the mid lane starts at x=820. The centre room's left wall spans x=1120..1144, y=300..860, and
 * the room's interior at y≈320..370, x≈1285..1400 is clear: the inner pillar ends at x=1200, the
 * access step sits at y=400..418 and the catwalk at y=620..640.
 */
class ObserverSetTest {

    private static final float HIP = VisionConfig.REACH_HIP;

    private final ArenaMap arena = ArenaMap.standard();

    private static Player player(int id, int teamIndex, float x, float y, float aimAngleDeg) {
        Player player = new Player(id, "P" + id, teamIndex, x, y);
        player.aimAngle = aimAngleDeg;
        return player;
    }

    private static DroneEntity drone(
            int id, int ownerId, int teamIndex, float x, float y, float aimAngleDeg) {
        return new DroneEntity(id, ownerId, teamIndex, x, y, aimAngleDeg, GadgetConfig.DRONE_HEALTH);
    }

    private static CameraEntity camera(
            int id, int ownerId, int teamIndex, float x, float y, float aimAngleDeg, boolean stuck) {
        CameraEntity camera =
            new CameraEntity(id, ownerId, teamIndex, x, y, 0f, 0f, aimAngleDeg, GadgetConfig.CAMERA_HEALTH);
        camera.stuck = stuck;
        return camera;
    }

    // --- Observer factories ---------------------------------------------------------------------

    @Test
    @DisplayName("a body's observer is its own eye, aim and the caller's reach")
    void bodyObserverCarriesTheEasedReach() {
        Player viewer = player(1, 0, 400f, 600f, 12f);
        Observer body = Observer.body(viewer, HIP);

        assertEquals(viewer.eyeX(), body.eyeX());
        assertEquals(viewer.eyeY(), body.eyeY(), "the eye is y + height × 0.85, never the feet");
        assertEquals(12f, body.aimAngleDeg());
        assertEquals(HIP, body.reach());
        assertEquals(VisionConfig.CONE_HALF_ANGLE_DEGREES, body.coneHalfAngleDeg());
        assertEquals(VisionConfig.FEATHER_ANGLE_DEGREES, body.featherAngleDeg());
        assertEquals(1f, body.brightness(), "a body's cone is the reference brightness");
    }

    @Test
    @DisplayName("device observers quote the gadget config's narrower, dimmer cones")
    void deviceObserversUseTheirOwnConeNumbers() {
        Observer droneObserver = Observer.drone(drone(7, 1, 0, 500f, 700f, 90f));
        assertEquals(500f, droneObserver.eyeX());
        assertEquals(GadgetConfig.DRONE_VISION_RANGE, droneObserver.reach());
        assertEquals(GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f, droneObserver.coneHalfAngleDeg());
        assertEquals(GadgetConfig.DRONE_VISION_BRIGHTNESS, droneObserver.brightness());
        assertEquals(90f, droneObserver.aimAngleDeg());

        Observer cameraObserver = Observer.camera(camera(8, 1, 0, 300f, 200f, 0f, true));
        assertEquals(GadgetConfig.CAMERA_VISION_RANGE, cameraObserver.reach());
        assertEquals(GadgetConfig.CAMERA_VISION_ANGLE_DEGREES / 2f, cameraObserver.coneHalfAngleDeg());
        assertEquals(1f, cameraObserver.brightness(), "a stuck camera's cone is not dimmed");
    }

    // --- Assembly -------------------------------------------------------------------------------

    @Test
    @DisplayName("an unpiloted viewer is one body plus every device they own")
    void bodyAndOwnDevices() {
        Player viewer = player(1, 0, 400f, 600f, 0f);
        ObserverSet set = ObserverSet.forViewer(
            viewer,
            HIP,
            List.of(drone(10, 1, 0, 500f, 700f, 0f), drone(11, 2, 0, 900f, 700f, 0f)),
            null,
            List.of(
                camera(20, 1, 0, 300f, 200f, 0f, true),
                camera(21, 1, 0, 350f, 400f, 0f, false),
                camera(22, 2, 1, 900f, 200f, 0f, true)));

        assertEquals(3, set.size(),
            "body, own drone, own stuck camera: the flying one and the enemy's two are not eyes");
        assertEquals(viewer.eyeX(), set.observers().get(0).eyeX(), "the body comes first");
        assertEquals(1f, set.observers().get(0).brightness());
        assertEquals(GadgetConfig.DRONE_VISION_BRIGHTNESS, set.observers().get(1).brightness(),
            "then the drone, at its dimmer brightness");
        assertEquals(1f, set.observers().get(2).brightness(), "then the stuck camera");
    }

    @Test
    @DisplayName("someone else's device never widens the viewer's sight")
    void enemyDevicesAreNotEyes() {
        Player viewer = player(1, 0, 400f, 600f, 180f);
        Player target = player(2, 1, 700f, 600f, 0f);
        // Aimed at the target, so the only thing hiding it is who owns the drone.
        DroneEntity enemyDrone = drone(11, 2, 1, 690f, 600f, 0f);

        assertFalse(ObserverSet.forViewer(viewer, HIP, List.of(enemyDrone), null, List.of())
            .isLit(target, arena, null),
            "an enemy drone parked beside the target does not reveal it to a viewer facing away");

        DroneEntity ownDrone = drone(10, 1, 0, 690f, 600f, 0f);
        assertTrue(ObserverSet.forViewer(viewer, HIP, List.of(ownDrone), null, List.of())
            .isLit(target, arena, null),
            "the same drone, owned by the viewer, does");
    }

    @Test
    @DisplayName("a camera in flight is not an observer until it sticks")
    void flyingCameraSeesNothing() {
        Player viewer = player(1, 0, 400f, 600f, 180f);
        Player target = player(2, 1, 700f, 600f, 0f);
        CameraEntity own = camera(21, 1, 0, 690f, 600f, 0f, false);

        assertFalse(ObserverSet.forViewer(viewer, HIP, List.of(), null, List.of(own))
            .isLit(target, arena, null),
            "the viewer faces away and their only device is still in the air");

        own.stuck = true;
        assertTrue(ObserverSet.forViewer(viewer, HIP, List.of(), null, List.of(own))
            .isLit(target, arena, null),
            "stuck, the same camera is an observation post beside the target");
    }

    @Test
    @DisplayName("while piloting, the body's cone is gone and the drone is counted once")
    void pilotingReplacesTheBodyCone() {
        Player viewer = player(1, 0, 400f, 600f, 0f);
        Player target = player(2, 1, 700f, 600f, 0f);

        // The interpolated copy in the world list and the predicted copy handed over separately are
        // the same drone: it must be counted once, at the predicted position.
        DroneEntity interpolated = drone(10, 1, 0, 1500f, 600f, 180f);
        DroneEntity predicted = drone(10, 1, 0, 1500f, 600f, 180f);

        viewer.surveillanceView = SurveillanceView.DRONE.ordinal();
        ObserverSet piloting =
            ObserverSet.forViewer(viewer, HIP, List.of(interpolated), predicted, List.of());
        assertEquals(1, piloting.size(), "the body is not an observer while the view has moved");
        assertFalse(piloting.isLit(target, arena, null),
            "the drone is 800 units away and out of reach, and the body that could see it is gone");

        viewer.surveillanceView = SurveillanceView.SELF.ordinal();
        assertTrue(ObserverSet.forViewer(viewer, HIP, List.of(interpolated), null, List.of())
            .isLit(target, arena, null),
            "back in the body, the same target is lit again");
    }

    @Test
    @DisplayName("no viewer means no eyes, and an empty set sees nothing")
    void emptySetSeesNothing() {
        assertTrue(ObserverSet.forViewer(null, HIP, List.of(), null, List.of()).isEmpty());
        assertEquals(0, ObserverSet.empty().size());
        assertFalse(ObserverSet.empty().isLit(player(2, 1, 700f, 600f, 0f), arena, null),
            "an empty set answers false rather than throwing, so no caller needs a guard");
        assertFalse(ObserverSet.empty().isLit((Rect) null, arena, null));
    }

    @Test
    @DisplayName("null entries in a hand-assembled list are dropped")
    void ofDropsNulls() {
        ObserverSet set = ObserverSet.of(Arrays.asList(
            Observer.body(player(1, 0, 400f, 600f, 0f), HIP), null));
        assertEquals(1, set.size());
        assertEquals(0, ObserverSet.of(null).size());
        assertEquals(0, ObserverSet.of(List.of()).size());
    }

    // --- The query ------------------------------------------------------------------------------

    @Test
    @DisplayName("a body lights what is inside its cone, in reach and in line of sight")
    void bodyConeBasics() {
        Player viewer = player(1, 0, 400f, 600f, 0f);
        ObserverSet eyes = ObserverSet.forViewer(viewer, HIP, List.of(), null, List.of());

        assertTrue(eyes.isLit(player(2, 1, 700f, 600f, 0f), arena, null), "ahead, in reach, open air");
        assertFalse(eyes.isLit(player(3, 1, 200f, 600f, 0f), arena, null),
            "directly behind: the peripheral floor is not enough for a screen-facing query");
        assertFalse(eyes.isLit(player(4, 1, 700f, 1400f, 0f), arena, null), "beyond the 640-unit reach");
    }

    @Test
    @DisplayName("terrain blocks the set exactly as it blocks the shader")
    void terrainOccludes() {
        Player viewer = player(1, 0, 1050f, 400f, 0f);
        Player target = player(2, 1, 1300f, 320f, 0f);
        ObserverSet bodyOnly = ObserverSet.forViewer(viewer, HIP, List.of(), null, List.of());

        assertFalse(bodyOnly.isLit(target, arena, null),
            "the centre room's left wall spans x=1120..1144 between them");

        // A drone the viewer owns, hovering on the far side of that wall, sees what the body cannot.
        DroneEntity inside = drone(10, 1, 0, 1400f, 350f, 180f);
        assertTrue(ObserverSet.forViewer(viewer, HIP, List.of(inside), null, List.of())
            .isLit(target, arena, null));
    }

    @Test
    @DisplayName("smoke the set is told about hides a target it would otherwise light")
    void smokeHides() {
        Player viewer = player(1, 0, 400f, 600f, 0f);
        Player target = player(2, 1, 700f, 600f, 0f);
        ObserverSet eyes = ObserverSet.forViewer(viewer, HIP, List.of(), null, List.of());

        assertTrue(eyes.isLit(target, arena, null));
        assertFalse(eyes.isLit(target, arena, List.of(new SmokeVolume(550f, 625f, 250f, 1f))),
            "an opaque cloud across the segment is the same answer the shader gives");
    }

    @Test
    @DisplayName("a device's 70° cone reveals less than a body's 120° from the same spot")
    void deviceConeIsNarrower() {
        // 50° off axis and ~145 units away: inside a body's feathered 60° half-angle, well outside
        // a device's 35°. Both observers sit at the same place and aim the same way.
        Player target = player(2, 1, 496f, 700f, 0f);

        Observer body = Observer.body(player(1, 0, 400f, 600f, 0f), HIP);
        assertTrue(ObserverSet.of(List.of(body)).isLit(target, arena, null));

        Observer device = Observer.drone(drone(10, 1, 0, 400f, 600f, 0f));
        assertFalse(ObserverSet.of(List.of(device)).isLit(target, arena, null),
            "the device's own narrower cone is what judges it, not the player's");
    }
}
