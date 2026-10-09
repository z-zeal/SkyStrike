package io.github.skystrike.shared.vision;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Geometry;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import java.util.Collection;
import java.util.List;

/**
 * The canonical visibility and line-of-sight mathematics.
 *
 * <p>Both the server (for anti-cheat state culling and combat checks) and the client (for
 * gameplay queries and visibility caching) evaluate these exact formulas. The GPU shader in
 * {@code assets/shaders/light/visibility_cone.frag} mirrors this math identically.
 */
public final class VisionMath {

    private VisionMath() {
    }

    /**
     * Checks if a direct unobstructed line of sight exists between two world points through the
     * arena geometry.
     *
     * @return {@code true} if no solid rectangle intersects the segment from {@code (x0, y0)} to {@code (x1, y1)}
     */
    public static boolean hasLineOfSight(float x0, float y0, float x1, float y1, ArenaMap map) {
        if (map == null) {
            return true;
        }
        return hasLineOfSight(x0, y0, x1, y1, map.solids());
    }

    /**
     * Checks if a direct unobstructed line of sight exists between two world points through a list of solids.
     */
    public static boolean hasLineOfSight(float x0, float y0, float x1, float y1, List<Rect> solids) {
        if (solids == null || solids.isEmpty()) {
            return true;
        }
        for (Rect rect : solids) {
            if (Geometry.segmentIntersectsAabb(x0, y0, x1, y1, rect)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if an unobstructed line of sight exists taking both terrain and smoke volumes into account.
     */
    public static boolean hasLineOfSight(
            float x0, float y0, float x1, float y1, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        if (!hasLineOfSight(x0, y0, x1, y1, map)) {
            return false;
        }
        if (smokeVolumes != null && !smokeVolumes.isEmpty()) {
            for (SmokeVolume smoke : smokeVolumes) {
                if (smoke.attenuationAlongSegment(x0, y0, x1, y1) >= 0.90f) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Computes the quadratic distance falloff factor in {@code [0, 1]}.
     *
     * <p>Continuous quadratic ease-out {@code 1 - (d/R)^2} up to {@code reach}, then zero.
     */
    public static float calculateDistanceFactor(float eyeX, float eyeY, float reach, float targetX, float targetY) {
        if (reach <= 0f) {
            return 0f;
        }
        float dx = targetX - eyeX;
        float dy = targetY - eyeY;
        float distSq = dx * dx + dy * dy;
        float reachSq = reach * reach;
        if (distSq >= reachSq) {
            return 0f;
        }
        float u = (float) Math.sqrt(distSq) / reach;
        return Math.max(0f, 1f - u * u);
    }

    /**
     * Computes the angular cone factor in {@code [PERIPHERAL_FLOOR, 1.0]}.
     *
     * <p>Full 1.0 inside the cone core, smoothly feathered across the edge via smoothstep, and
     * holding {@link VisionConfig#PERIPHERAL_FLOOR} in peripheral space.
     */
    public static float calculateConeFactor(
            float eyeX,
            float eyeY,
            float aimAngleDeg,
            float coneHalfAngleDeg,
            float featherAngleDeg,
            float peripheralFloor,
            float targetX,
            float targetY) {
        float dx = targetX - eyeX;
        float dy = targetY - eyeY;
        if (dx == 0f && dy == 0f) {
            return 1.0f;
        }
        float angleToTargetDeg = Angles.toDegrees((float) Math.atan2(dy, dx));
        float deltaAngle = Math.abs(Angles.shortestDelta(aimAngleDeg, angleToTargetDeg));

        float innerAngle = Math.max(0f, coneHalfAngleDeg - featherAngleDeg);
        if (deltaAngle <= innerAngle) {
            return 1.0f;
        }
        if (deltaAngle >= coneHalfAngleDeg) {
            return peripheralFloor;
        }

        // Smoothstep between innerAngle (1.0) and coneHalfAngleDeg (peripheralFloor)
        float t = (deltaAngle - innerAngle) / Math.max(0.001f, coneHalfAngleDeg - innerAngle);
        float smoothT = t * t * (3f - 2f * t);
        return Lerp.mix(1.0f, peripheralFloor, smoothT);
    }

    /**
     * Evaluates total visibility of a point {@code (targetX, targetY)} from an observer with the
     * standard player cone ({@link VisionConfig#CONE_HALF_ANGLE_DEGREES}).
     *
     * @return a visibility value in {@code [0, 1]}. Returns 0 if occluded by terrain or out of reach.
     */
    public static float calculateVisibility(
            float observerEyeX,
            float observerEyeY,
            float aimAngleDeg,
            float reach,
            float targetX,
            float targetY,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        return calculateVisibility(
                observerEyeX,
                observerEyeY,
                aimAngleDeg,
                reach,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                targetX,
                targetY,
                map,
                smokeVolumes);
    }

    /**
     * Evaluates total visibility of a point from an observer whose cone half-angle is not the
     * player's — a gadget device projects a narrower one (mechanics §7.1: a drone's cone is
     * 70° against a player's 120°).
     *
     * <p>This is the same formula as the overload above with one more parameter, not a second
     * implementation: the player-cone version delegates here, so the CPU query, the GPU cone and
     * every consumer of either stay one maths. Before this existed a device observer was judged
     * with the player's wide cone, which over-reported what a drone or a stuck camera can see.
     *
     * @param coneHalfAngleDeg the observer's own cone half-angle, in degrees
     * @return a visibility value in {@code [0, 1]}. Returns 0 if occluded by terrain or out of reach.
     */
    public static float calculateVisibility(
            float observerEyeX,
            float observerEyeY,
            float aimAngleDeg,
            float reach,
            float coneHalfAngleDeg,
            float targetX,
            float targetY,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        float distFactor = calculateDistanceFactor(observerEyeX, observerEyeY, reach, targetX, targetY);
        if (distFactor <= 0f) {
            return 0f;
        }

        float coneFactor = calculateConeFactor(
                observerEyeX,
                observerEyeY,
                aimAngleDeg,
                coneHalfAngleDeg,
                VisionConfig.FEATHER_ANGLE_DEGREES,
                VisionConfig.PERIPHERAL_FLOOR,
                targetX,
                targetY);

        // Terrain occlusion: hard block
        if (!hasLineOfSight(observerEyeX, observerEyeY, targetX, targetY, map)) {
            return 0f;
        }

        float vis = distFactor * coneFactor;

        // Smoke attenuation
        if (smokeVolumes != null && !smokeVolumes.isEmpty()) {
            float smokeAtten = 0f;
            for (SmokeVolume smoke : smokeVolumes) {
                smokeAtten += smoke.attenuationAlongSegment(observerEyeX, observerEyeY, targetX, targetY);
            }
            vis = Math.max(0f, vis - smokeAtten);
        }

        return vis;
    }

    /**
     * Tests if an observer with the standard player cone can see any key sample point of a target
     * bounding box.
     *
     * <p>Samples head/eye, centre, feet, and corner points on {@code targetBox} so corner-peeking
     * players are detected immediately.
     */
    public static boolean canObserverSeeTarget(
            float observerEyeX,
            float observerEyeY,
            float aimAngleDeg,
            float reach,
            Rect targetBox,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        return anySampleClears(
                observerEyeX,
                observerEyeY,
                aimAngleDeg,
                reach,
                VisionConfig.CONE_HALF_ANGLE_DEGREES,
                VisionConfig.VISIBILITY_THRESHOLD,
                targetBox,
                map,
                smokeVolumes);
    }

    /**
     * The same five-point sample test on the <b>presentation</b> bar: the target must be lit above
     * the dim peripheral floor, not merely above zero.
     *
     * <p>Why two bars exist. Outside its cone an observer keeps
     * {@link VisionConfig#PERIPHERAL_FLOOR} — 6% — so the raw visibility of a body standing
     * directly behind you is small but comfortably above
     * {@link VisionConfig#VISIBILITY_THRESHOLD}, and {@link #canObserverSeeTarget} reports it as
     * seen. For an authoritative gameplay question that is the right answer: the body really is
     * faintly lit, and the server must not pretend otherwise. For anything facing the screen it is
     * the wrong one, because 6% of a sprite is indistinguishable from the black the composite
     * draws around it. M6's player lights and M7's effect culler already use this stricter bar;
     * this is its one spelling, and the minimap's blip gate is the third consumer.
     *
     * @param coneHalfAngleDeg the observer's own cone half-angle, in degrees — a gadget device
     *                         projects a narrower cone than a body, and must be judged on its own
     */
    public static boolean isTargetLit(
            float observerEyeX,
            float observerEyeY,
            float aimAngleDeg,
            float reach,
            float coneHalfAngleDeg,
            Rect targetBox,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        return anySampleClears(
                observerEyeX,
                observerEyeY,
                aimAngleDeg,
                reach,
                coneHalfAngleDeg,
                VisionConfig.LIT_VISIBILITY_THRESHOLD,
                targetBox,
                map,
                smokeVolumes);
    }

    /**
     * One implementation of the five-point sample test, parameterised by the observer's cone
     * half-angle and by the bar a sample has to clear. Every public form above delegates here.
     */
    private static boolean anySampleClears(
            float observerEyeX,
            float observerEyeY,
            float aimAngleDeg,
            float reach,
            float coneHalfAngleDeg,
            float threshold,
            Rect targetBox,
            ArenaMap map,
            List<SmokeVolume> smokeVolumes) {
        if (targetBox == null) {
            return false;
        }

        // Test points on target
        float centerX = targetBox.centerX();
        float centerY = targetBox.centerY();
        float headY = targetBox.bottom() + targetBox.height() * PlayerConfig.EYE_HEIGHT_FRACTION;
        float feetY = targetBox.bottom() + 4f;
        float leftX = targetBox.left() + 2f;
        float rightX = targetBox.right() - 2f;
        float topY = targetBox.top() - 2f;

        // 1. Center
        if (calculateVisibility(
                        observerEyeX, observerEyeY, aimAngleDeg, reach, coneHalfAngleDeg,
                        centerX, centerY, map, smokeVolumes)
                >= threshold) {
            return true;
        }
        // 2. Head / Eye
        if (calculateVisibility(
                        observerEyeX, observerEyeY, aimAngleDeg, reach, coneHalfAngleDeg,
                        centerX, headY, map, smokeVolumes)
                >= threshold) {
            return true;
        }
        // 3. Feet
        if (calculateVisibility(
                        observerEyeX, observerEyeY, aimAngleDeg, reach, coneHalfAngleDeg,
                        centerX, feetY, map, smokeVolumes)
                >= threshold) {
            return true;
        }
        // 4. Top-left corner
        if (calculateVisibility(
                        observerEyeX, observerEyeY, aimAngleDeg, reach, coneHalfAngleDeg,
                        leftX, topY, map, smokeVolumes)
                >= threshold) {
            return true;
        }
        // 5. Top-right corner
        if (calculateVisibility(
                        observerEyeX, observerEyeY, aimAngleDeg, reach, coneHalfAngleDeg,
                        rightX, topY, map, smokeVolumes)
                >= threshold) {
            return true;
        }

        return false;
    }

    /**
     * Determines whether an authoritative observer player can see a target player.
     */
    public static boolean canObserverSee(Player observer, Player target, ArenaMap map) {
        return canObserverSee(observer, target, map, null);
    }

    /**
     * Determines whether an authoritative observer player can see a target player with smoke volumes.
     */
    public static boolean canObserverSee(
            Player observer, Player target, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        if (observer == null || target == null) {
            return false;
        }
        if (observer.id == target.id) {
            return true;
        }
        float reach = observer.ads ? VisionConfig.REACH_ADS : VisionConfig.REACH_HIP;
        return canObserverSeeTarget(
                observer.eyeX(),
                observer.eyeY(),
                observer.aimAngle,
                reach,
                target.hitbox(),
                map,
                smokeVolumes);
    }

    /**
     * Checks if any member of a team can see the target player.
     */
    public static boolean canTeamSee(
            Collection<Player> teamMembers, Player target, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        if (teamMembers == null || target == null) {
            return false;
        }
        for (Player member : teamMembers) {
            if (canObserverSee(member, target, map, smokeVolumes)) {
                return true;
            }
        }
        return false;
    }
}
