package io.github.skystrike.shared.vision;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;

/**
 * One eye in the world: where it is, which way it looks, and the shape of the cone it projects.
 *
 * <p>A viewer has more than one pair of eyes from M10 onwards — their own, plus every drone and
 * every stuck camera they own — and two consumers need that identical list: the GPU visibility
 * pass, which draws one cone per observer, and any CPU query that has to agree with what the pass
 * drew. Describing an observer once, in {@code shared}, is what keeps those two from drifting: the
 * device cone numbers ({@link GadgetConfig#DRONE_VISION_RANGE}, the 70° device angle, the drone's
 * dimmer brightness) are quoted here and nowhere else.
 *
 * <p>The fields are the shader's own vocabulary, including {@code featherAngleDeg} and
 * {@code brightness}, which a CPU sight test does not use. They ride along anyway so that one list
 * can be handed to the renderer and to {@link ObserverSet#isLit} without translating it — a
 * second, leaner observer type would be a second thing to keep in step.
 *
 * @param eyeX             world x of the eye; a player's is {@link Player#eyeX()}
 * @param eyeY             world y of the eye; a player's is {@code y + height × 0.85}
 * @param aimAngleDeg      the cone's axis, in degrees
 * @param reach            how far the cone extends, in world units
 * @param coneHalfAngleDeg half of the cone's full angle, in degrees
 * @param featherAngleDeg  the soft edge width, in degrees
 * @param brightness       the composite's multiplier for this cone: 1 for a body, the configured
 *                         dimmer value for a device
 */
public record Observer(
    float eyeX,
    float eyeY,
    float aimAngleDeg,
    float reach,
    float coneHalfAngleDeg,
    float featherAngleDeg,
    float brightness
) {

    /**
     * An all-round vision bubble: a full circle of sight of the given radius around one point
     * (M14). It is an ordinary observer whose cone is 360° wide, so the visibility pass, the CPU
     * sight test and the minimap all read it the same way. Its feather is zero: a bubble has no
     * edge direction to feather across, and a nonzero feather would dim a sliver behind it.
     *
     * @param brightness the composite's multiplier for this bubble, as for a cone
     */
    public static Observer bubble(float eyeX, float eyeY, float radius, float brightness) {
        return new Observer(
            eyeX,
            eyeY,
            0f,
            radius,
            VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES,
            0f,
            brightness);
    }

    /** A player's bubble, around the body's eye: {@link VisionConfig#BODY_BUBBLE_RADIUS}, undimmed. */
    public static Observer bodyBubble(Player viewer) {
        return bubble(viewer.eyeX(), viewer.eyeY(), VisionConfig.BODY_BUBBLE_RADIUS, 1f);
    }

    /** A deployed drone's bubble: {@link GadgetConfig#DEVICE_BUBBLE_RADIUS}, at the drone's dimmer brightness. */
    public static Observer droneBubble(DroneEntity drone) {
        return bubble(drone.x, drone.y, GadgetConfig.DEVICE_BUBBLE_RADIUS, GadgetConfig.DRONE_VISION_BRIGHTNESS);
    }

    /** A stuck camera's bubble: {@link GadgetConfig#DEVICE_BUBBLE_RADIUS}, undimmed like its cone. */
    public static Observer cameraBubble(CameraEntity camera) {
        return bubble(camera.x, camera.y, GadgetConfig.DEVICE_BUBBLE_RADIUS, 1f);
    }

    /** Whether this eye is a full-circle bubble rather than a directional cone. */
    public boolean isBubble() {
        return coneHalfAngleDeg >= VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES;
    }

    /**
     * A viewer's own eyes.
     *
     * @param reach the caller's reach, not {@code viewer.ads ? ADS : HIP}: the client eases reach
     *              across the ADS transition so the cone never snaps, and that eased value is the
     *              one the shader draws with. The server, which has no transition to ease, passes
     *              the discrete value it judges with.
     */
    public static Observer body(Player viewer, float reach) {
        return new Observer(
            viewer.eyeX(),
            viewer.eyeY(),
            viewer.aimAngle,
            reach,
            VisionConfig.CONE_HALF_ANGLE_DEGREES,
            VisionConfig.FEATHER_ANGLE_DEGREES,
            1f);
    }

    /** A deployed drone's own cone: 250 units, 70°, dimmer than a body's (mechanics §7.1). */
    public static Observer drone(DroneEntity drone) {
        return new Observer(
            drone.x,
            drone.y,
            drone.aimAngle,
            GadgetConfig.DRONE_VISION_RANGE,
            GadgetConfig.DRONE_VISION_ANGLE_DEGREES / 2f,
            VisionConfig.FEATHER_ANGLE_DEGREES,
            GadgetConfig.DRONE_VISION_BRIGHTNESS);
    }

    /**
     * A stuck camera's cone (mechanics §7.2). A camera in flight is not an observer: it sees
     * nothing until it sticks, which is why {@link ObserverSet} filters on {@code stuck}.
     */
    public static Observer camera(CameraEntity camera) {
        return new Observer(
            camera.x,
            camera.y,
            camera.aimAngle,
            GadgetConfig.CAMERA_VISION_RANGE,
            GadgetConfig.CAMERA_VISION_ANGLE_DEGREES / 2f,
            VisionConfig.FEATHER_ANGLE_DEGREES,
            1f);
    }
}
