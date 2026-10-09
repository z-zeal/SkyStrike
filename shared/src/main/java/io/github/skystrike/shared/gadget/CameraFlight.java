package io.github.skystrike.shared.gadget;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.ThrowablePhysics;

/**
 * The throw camera's flight (mechanics §7.2), built on the one throwable integrator rather than a
 * second one: the camera's arc <b>is</b> a throwable's arc, under the same gravity and the same
 * substepped collision, so a camera thrown along an aim lands where a grenade thrown the same way
 * would — and the client can preview it with the identical code.
 *
 * <p>The only camera-specific rule is the stick decision: the first surface the flight touches
 * parks the camera permanently (the integrator's contact position is already flush with the
 * surface, so no bounce is needed or wanted), while reaching the arena boundary means the camera
 * has left the arena and is destroyed. {@link #step} classifies the integrator's contact into
 * that outcome; the system applies it.
 */
public final class CameraFlight {

    private CameraFlight() {
    }

    /** Why a flight step finished where it did. */
    public enum Outcome {
        /** Still in the air; the flight continues next tick. */
        FLYING,
        /** The first surface was hit: the camera sticks there and becomes a fixed post. */
        STUCK,
        /** The flight reached the arena boundary: the camera has left the arena and is lost. */
        OFF_ARENA
    }

    /**
     * Steps one camera's flight state forward by {@code dt} and classifies the result. The state
     * is a {@link ThrownUtility} — kinematics and timers only — because that is the type the one
     * shared integrator is written against, and the camera flies the integrator's
     * <b>sticks-at-contact</b> variant: the first surface stops it flush, with no bounce to carry
     * it past the surface it actually hit.
     */
    public static Outcome step(ThrownUtility flight, float dt, ArenaMap map) {
        if (flight == null || dt <= 0f) {
            return Outcome.FLYING;
        }
        return outcomeOf(ThrowablePhysics.stepInPlaceUntilContact(flight, dt, map));
    }

    /**
     * The stick decision as a pure mapping: a surface contact sticks the camera, the arena
     * boundary loses it (§7.2's "destroyed if it leaves the arena"), and no contact means the
     * flight continues.
     */
    public static Outcome outcomeOf(ThrowablePhysics.Contact contact) {
        if (contact == null) {
            return Outcome.FLYING;
        }
        return switch (contact) {
            case NONE -> Outcome.FLYING;
            case SURFACE -> Outcome.STUCK;
            case BOUNDARY -> Outcome.OFF_ARENA;
        };
    }

    /** Where a camera throw leaves the hand: offset from the eye along the aim, like a grenade. */
    public static float launchX(float eyeX, float aimDegrees) {
        return ThrowablePhysics.muzzleX(eyeX, aimDegrees);
    }

    /** Where a camera throw leaves the hand: offset from the eye along the aim, like a grenade. */
    public static float launchY(float eyeY, float aimDegrees) {
        return ThrowablePhysics.muzzleY(eyeY, aimDegrees);
    }

    /** Initial horizontal velocity for a camera throw along {@code aimDegrees}. */
    public static float launchVelocityX(float aimDegrees) {
        return ThrowablePhysics.throwVelocityX(GadgetConfig.CAMERA_THROW_SPEED, aimDegrees);
    }

    /** Initial vertical velocity for a camera throw along {@code aimDegrees}. */
    public static float launchVelocityY(float aimDegrees) {
        return ThrowablePhysics.throwVelocityY(GadgetConfig.CAMERA_THROW_SPEED, aimDegrees);
    }
}
