package io.github.skystrike.shared.vision;

import static org.junit.jupiter.api.Assertions.*;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
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
    void testConeHalfAngleIsTheObserverOwn() {
        // A body at (496, 700) seen from an eye at (400, 600) sits ~47-60° off axis: inside a
        // player's 60° half-angle, outside a gadget device's 35°. Same eye, same reach, same
        // target — only the cone angle differs, which is the whole point of the parameter.
        Rect target = new Rect(496f - 15f, 700f, 30f, 50f);

        assertTrue(VisionMath.isTargetLit(
            400f, 600f, 0f, 640f, VisionConfig.CONE_HALF_ANGLE_DEGREES, target, arena, null));
        assertFalse(VisionMath.isTargetLit(
            400f, 600f, 0f, 640f, GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f, target, arena, null),
            "a device's narrower cone reveals less from the very same spot");

        float wide = VisionMath.calculateVisibility(
            400f, 600f, 0f, 640f, VisionConfig.CONE_HALF_ANGLE_DEGREES, 496f, 704f, arena, null);
        float narrow = VisionMath.calculateVisibility(
            400f, 600f, 0f, 640f, GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f, 496f, 704f, arena, null);
        assertTrue(narrow < wide, "the narrow cone is dimmer at the same point: " + narrow + " < " + wide);
    }

    @Test
    void testABubbleIsFullCircleInEveryDirection() {
        // M14: a vision bubble has no periphery. At one distance every direction is inside it, so
        // the cone factor is 1 whatever the aim, and the CPU agrees with the shader's zero-feather
        // full-circle branch. The player's 120° cone still falls to the floor behind the observer.
        for (int deg = 0; deg < 360; deg += 30) {
            double rad = Math.toRadians(deg);
            float tx = 400f + (float) (Math.cos(rad) * 100.0);
            float ty = 600f + (float) (Math.sin(rad) * 100.0);
            assertEquals(1f,
                VisionMath.calculateConeFactor(400f, 600f, 0f,
                    VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES, 0f, VisionConfig.PERIPHERAL_FLOOR, tx, ty),
                1e-6f, "bubble at " + deg + " degrees");
        }
        assertEquals(VisionConfig.PERIPHERAL_FLOOR,
            VisionMath.calculateConeFactor(400f, 600f, 0f,
                VisionConfig.CONE_HALF_ANGLE_DEGREES, VisionConfig.FEATHER_ANGLE_DEGREES,
                VisionConfig.PERIPHERAL_FLOOR, 300f, 600f),
            1e-6f, "the player's cone still has a periphery");
    }

    @Test
    void testThePresentationBarIsStricterThanTheEntityBar() {
        Player observer = new Player(1, "Observer", 0, 400f, 600f);
        observer.aimAngle = 0f;
        Player behind = new Player(2, "Behind", 1, 200f, 600f);

        assertTrue(VisionMath.canObserverSee(observer, behind, arena),
            "the peripheral floor really is non-zero, so the authoritative query reports a body "
                + "standing directly behind the observer as seen");
        assertFalse(VisionMath.isTargetLit(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                VisionConfig.REACH_HIP,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                behind.hitbox(),
                arena,
                null),
            "6% of a sprite is black on screen, so nothing facing the screen may report it");

        Player inFront = new Player(3, "InFront", 1, 700f, 600f);
        assertTrue(VisionMath.isTargetLit(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                VisionConfig.REACH_HIP,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                inFront.hitbox(),
                arena,
                null),
            "inside the cone both bars agree");
    }

    @Test
    void testTheOlderSignaturesStillMeanThePlayerCone() {
        Player observer = new Player(1, "Observer", 0, 400f, 600f);
        observer.aimAngle = 0f;

        // The parameterised overload at the player's own half-angle must be the old function, so
        // threading the cone angle through changed no existing answer anywhere in the codebase.
        assertEquals(
            VisionMath.calculateVisibility(
                observer.eyeX(), observer.eyeY(), 0f, VisionConfig.REACH_HIP, 700f, 625f, arena, null),
            VisionMath.calculateVisibility(
                observer.eyeX(),
                observer.eyeY(),
                0f,
                VisionConfig.REACH_HIP,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                700f,
                625f,
                arena,
                null),
            0.0001f);

        Player target = new Player(2, "Target", 1, 700f, 600f);
        assertEquals(
            VisionMath.canObserverSeeTarget(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                VisionConfig.REACH_HIP,
                target.hitbox(),
                arena,
                null),
            VisionMath.isTargetLit(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                VisionConfig.REACH_HIP,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                target.hitbox(),
                arena,
                null),
            "in the open cone both bars give the same answer, so the stricter one only bites at "
                + "the periphery and behind terrain");
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
