package io.github.skystrike.shared.vision;

import static org.junit.jupiter.api.Assertions.*;

import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VisionMathTest {

    private ArenaMap arena;

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
    }

    @Test
    void testLineOfSightClear() {
        // Line of sight in open air between ramp platforms
        assertTrue(VisionMath.hasLineOfSight(200f, 600f, 600f, 600f, arena));
    }

    @Test
    void testLineOfSightBlockedByWall() {
        // Line of sight through centre room left wall (x=1120, y=300..860)
        assertFalse(VisionMath.hasLineOfSight(1050f, 400f, 1200f, 400f, arena));
    }

    @Test
    void testLineOfSightBlockedByFloor() {
        // Line of sight vertically through centre room floor (y=280..300, x=1120..1880)
        assertFalse(VisionMath.hasLineOfSight(1400f, 200f, 1400f, 350f, arena));
    }

    @Test
    void testDistanceFalloffQuadratic() {
        float reach = 640f;
        assertEquals(1.0f, VisionMath.calculateDistanceFactor(0f, 0f, reach, 0f, 0f), 0.001f);

        // At half reach (320u): 1 - (0.5)^2 = 0.75
        assertEquals(0.75f, VisionMath.calculateDistanceFactor(0f, 0f, reach, 320f, 0f), 0.001f);

        // At reach (640u): 0.0
        assertEquals(0.0f, VisionMath.calculateDistanceFactor(0f, 0f, reach, 640f, 0f), 0.001f);

        // Beyond reach (700u): 0.0
        assertEquals(0.0f, VisionMath.calculateDistanceFactor(0f, 0f, reach, 700f, 0f), 0.001f);
    }

    @Test
    void testConeFactorFeathering() {
        float eyeX = 100f;
        float eyeY = 100f;
        float aimAngleDeg = 0f; // Aiming right (+X)
        float coneHalfAngle = VisionConfig.CONE_HALF_ANGLE_DEGREES; // 60°
        float feather = VisionConfig.FEATHER_ANGLE_DEGREES; // 15°
        float floor = VisionConfig.PERIPHERAL_FLOOR; // 0.06

        // Directly in front (0°) -> 1.0
        assertEquals(1.0f, VisionMath.calculateConeFactor(
                eyeX, eyeY, aimAngleDeg, coneHalfAngle, feather, floor, 200f, 100f), 0.001f);

        // At 30° (inside core cone: <= 45°) -> 1.0
        float rad30 = (float) Math.toRadians(30.0);
        assertEquals(1.0f, VisionMath.calculateConeFactor(
                eyeX, eyeY, aimAngleDeg, coneHalfAngle, feather, floor,
                eyeX + 100f * (float) Math.cos(rad30), eyeY + 100f * (float) Math.sin(rad30)), 0.001f);

        // At 90° (outside cone: >= 60°) -> peripheral floor
        assertEquals(floor, VisionMath.calculateConeFactor(
                eyeX, eyeY, aimAngleDeg, coneHalfAngle, feather, floor, 100f, 200f), 0.001f);

        // Directly behind (180°) -> peripheral floor
        assertEquals(floor, VisionMath.calculateConeFactor(
                eyeX, eyeY, aimAngleDeg, coneHalfAngle, feather, floor, 0f, 100f), 0.001f);
    }

    @Test
    void testSmokeVolumeAttenuation() {
        SmokeVolume smoke = new SmokeVolume(500f, 500f, 100f, 1.0f);
        assertTrue(smoke.contains(500f, 500f));
        assertTrue(smoke.contains(550f, 500f));
        assertFalse(smoke.contains(650f, 500f));

        // Density at center is 1.0
        assertEquals(1.0f, smoke.densityAt(500f, 500f), 0.01f);
        // Density at half radius (50u) is 0.5
        assertEquals(0.5f, smoke.densityAt(550f, 500f), 0.01f);

        // Ray passing directly through smoke center
        float atten = smoke.attenuationAlongSegment(300f, 500f, 700f, 500f);
        assertEquals(1.0f, atten, 0.01f);

        // Ray passing far from smoke
        float farAtten = smoke.attenuationAlongSegment(300f, 800f, 700f, 800f);
        assertEquals(0.0f, farAtten, 0.01f);
    }

    @Test
    void testObserverCanSeeTarget() {
        Player observer = new Player(1, "Observer", 0, 400f, 600f);
        observer.aimAngle = 0f; // Aiming right

        Player targetInCone = new Player(2, "TargetFront", 1, 700f, 600f);
        assertTrue(VisionMath.canObserverSee(observer, targetInCone, arena));

        // Target behind wall
        Player targetBehindWall = new Player(3, "TargetBehindWall", 1, 1200f, 400f);
        Player observerOutsideRoom = new Player(4, "ObsOutside", 0, 1050f, 400f);
        observerOutsideRoom.aimAngle = 0f;
        assertFalse(VisionMath.canObserverSee(observerOutsideRoom, targetBehindWall, arena));

        // Target far beyond hip vision reach (640u)
        Player targetFar = new Player(5, "TargetFar", 1, 1200f, 600f);
        assertFalse(VisionMath.canObserverSee(observer, targetFar, arena));

        // With ADS enabled (reach extends to 1024u), target at 800u is visible
        observer.ads = true;
        Player targetAds = new Player(6, "TargetAds", 1, 1100f, 600f);
        assertTrue(VisionMath.canObserverSee(observer, targetAds, arena));
    }

    @Test
    void testMirrorSymmetryInVisibility() {
        // Observer and target on Left side
        Player leftObs = new Player(1, "LeftObs", 0, 300f, 600f);
        leftObs.aimAngle = 0f;
        Player leftTarget = new Player(2, "LeftTarget", 1, 600f, 600f);
        boolean leftVisible = VisionMath.canObserverSee(leftObs, leftTarget, arena);

        // Reflected on Right side
        float axis = arena.mirrorAxisX();
        Player rightObs = new Player(3, "RightObs", 1, 2f * axis - 300f, 600f);
        rightObs.aimAngle = 180f; // Aiming mirrored left
        Player rightTarget = new Player(4, "RightTarget", 0, 2f * axis - 600f, 600f);
        boolean rightVisible = VisionMath.canObserverSee(rightObs, rightTarget, arena);

        assertEquals(leftVisible, rightVisible);
    }
}
