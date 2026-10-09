package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.ThrownUtility;

/**
 * The one throwable integrator (mechanics §6).
 *
 * <p>Pure, like {@code PlayerMotion}, and for the same reason: the server steps grenades with it
 * and {@code core/render/TrajectoryRenderer} draws the predicted arc with it. An approximation
 * on the client would mean the drawn arc and the thrown grenade disagree, which is worse than
 * drawing no arc at all.
 *
 * <p>Two details make the bouncing behave:
 *
 * <ul>
 *   <li><b>Substeps.</b> A grenade leaves the hand at up to 850 u/s, which is 14 units in one
 *       60 Hz tick — exactly the thickness of the tunnel roof. Integrating that in one go lets
 *       it pass straight through. Movement is therefore split so no substep travels further
 *       than {@link UtilityConfig#MAX_SUBSTEP_TRAVEL}.</li>
 *   <li><b>Axis separation.</b> X and Y are resolved one at a time, which gives the contact
 *       normal for free: hitting something while moving in X is a wall, hitting something while
 *       moving in Y is a floor or a ceiling. That normal is what a molotov spreads along.</li>
 * </ul>
 */
public final class ThrowablePhysics {

    private ThrowablePhysics() {
    }

    /** Why a step finished where it did. */
    public enum Contact {
        /** Nothing was hit this step. */
        NONE,
        /** A wall, floor or ceiling was hit. The contact normal is on the throwable. */
        SURFACE,
        /** The arena boundary was hit. Treated as a wall so nothing escapes the map. */
        BOUNDARY
    }

    /** Initial velocity for a throw of {@code force} along {@code aimDegrees}. */
    public static float throwVelocityX(float force, float aimDegrees) {
        return force * (float) Math.cos(Angles.toRadians(aimDegrees));
    }

    /** Initial velocity for a throw of {@code force} along {@code aimDegrees}. */
    public static float throwVelocityY(float force, float aimDegrees) {
        return force * (float) Math.sin(Angles.toRadians(aimDegrees));
    }

    /** Where a throw leaves the hand: offset from the eye along the aim direction. */
    public static float muzzleX(float eyeX, float aimDegrees) {
        return eyeX + UtilityConfig.THROW_OFFSET * (float) Math.cos(Angles.toRadians(aimDegrees));
    }

    /** Where a throw leaves the hand: offset from the eye along the aim direction. */
    public static float muzzleY(float eyeY, float aimDegrees) {
        return eyeY + UtilityConfig.THROW_OFFSET * (float) Math.sin(Angles.toRadians(aimDegrees));
    }

    /**
     * Steps one throwable forward, leaving the argument untouched.
     *
     * @return the stepped copy
     */
    public static ThrownUtility step(ThrownUtility state, float dt, ArenaMap map) {
        ThrownUtility next = state.copy();
        stepInPlace(next, dt, map);
        return next;
    }

    /**
     * In-place variant for the tick loop, which steps a pool and must not allocate.
     *
     * @return what, if anything, was hit during this step
     */
    public static Contact stepInPlace(ThrownUtility t, float dt, ArenaMap map) {
        if (t == null || dt <= 0f) {
            return Contact.NONE;
        }

        t.prevX = t.x;
        t.prevY = t.y;
        t.age += dt;

        if (t.fuseRemaining > 0f) {
            t.fuseRemaining = Math.max(0f, t.fuseRemaining - dt);
        }
        if (t.resting) {
            // A parked throwable still counts down; it just stops moving and stops colliding.
            return Contact.NONE;
        }

        int substeps = substepsFor(t, dt);
        float h = dt / substeps;
        Contact contact = Contact.NONE;

        for (int i = 0; i < substeps; i++) {
            Contact stepContact = integrateOnce(t, h, map, true);
            if (stepContact != Contact.NONE) {
                contact = stepContact;
            }
            if (t.resting) {
                break;
            }
        }
        return contact;
    }

    /**
     * Sticks-at-contact variant of {@link #stepInPlace} for devices that must stop on the first
     * surface they touch rather than bounce off it (mechanics §7.2's throw camera).
     *
     * <p>The difference is one word in the substep loop: <b>break</b> on the first contact
     * instead of carrying the bounced velocity through the remaining substeps. Without it, a
     * fast-falling camera would bounce inside a single tick and end up parked several units past
     * the surface it actually hit. The integration itself is the same {@code integrateOnce} —
     * this is the one integrator, with the bounce switched off — and the throwable is left flush
     * against the surface with the contact normal set, exactly where a stick belongs.
     *
     * @return what, if anything, was hit during this step
     */
    public static Contact stepInPlaceUntilContact(ThrownUtility t, float dt, ArenaMap map) {
        if (t == null || dt <= 0f) {
            return Contact.NONE;
        }

        t.prevX = t.x;
        t.prevY = t.y;
        t.age += dt;

        if (t.fuseRemaining > 0f) {
            t.fuseRemaining = Math.max(0f, t.fuseRemaining - dt);
        }
        if (t.resting) {
            return Contact.NONE;
        }

        int substeps = substepsFor(t, dt);
        float h = dt / substeps;

        for (int i = 0; i < substeps; i++) {
            Contact stepContact = integrateOnce(t, h, map, false);
            if (stepContact != Contact.NONE) {
                // First surface wins: park flush against it, do not bounce, do not continue.
                return stepContact;
            }
        }
        return Contact.NONE;
    }

    /** How many substeps this tick needs for the throwable not to step over thin geometry. */
    public static int substepsFor(ThrownUtility t, float dt) {
        float travel = t.speed() * dt;
        int needed = (int) Math.ceil(travel / UtilityConfig.MAX_SUBSTEP_TRAVEL);
        return Math.max(1, Math.min(UtilityConfig.MAX_SUBSTEPS, needed));
    }

    /**
     * One integration substep. With {@code bounce} the throwable reflects off the surface it
     * hit (grenades, molotovs); without it, the velocity component into the surface is spent
     * and the throwable is left flush against it (a camera sticking to its first surface).
     */
    private static Contact integrateOnce(ThrownUtility t, float h, ArenaMap map, boolean bounce) {
        t.vy += UtilityConfig.GRAVITY * h;
        clampSpeed(t);

        Contact contact = Contact.NONE;

        // --- X axis -----------------------------------------------------------------------
        float targetX = t.x + t.vx * h;
        Rect blockerX = solidAt(map, targetX, t.y);
        if (blockerX != null) {
            t.x = t.vx > 0f
                ? blockerX.left() - UtilityConfig.THROWABLE_RADIUS - UtilityConfig.CONTACT_SKIN
                : blockerX.right() + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN;
            if (bounce) {
                bounceX(t);
            } else {
                t.contactNormalX = t.vx > 0f ? -1f : 1f;
                t.contactNormalY = 0f;
                t.vx = 0f;
            }
            contact = Contact.SURFACE;
        } else if (outsideHorizontally(targetX)) {
            t.x = Lerp.clamp(
                targetX, UtilityConfig.THROWABLE_RADIUS, WorldConfig.ARENA_WIDTH - UtilityConfig.THROWABLE_RADIUS);
            if (bounce) {
                bounceX(t);
            } else {
                t.contactNormalX = t.vx > 0f ? -1f : 1f;
                t.contactNormalY = 0f;
                t.vx = 0f;
            }
            contact = Contact.BOUNDARY;
        } else {
            t.x = targetX;
        }

        // --- Y axis -----------------------------------------------------------------------
        float targetY = t.y + t.vy * h;
        Rect blockerY = solidAt(map, t.x, targetY);
        if (blockerY != null) {
            t.y = t.vy > 0f
                ? blockerY.bottom() - UtilityConfig.THROWABLE_RADIUS - UtilityConfig.CONTACT_SKIN
                : blockerY.top() + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN;
            if (bounce) {
                bounceY(t);
            } else {
                t.contactNormalX = 0f;
                t.contactNormalY = t.vy > 0f ? -1f : 1f;
                t.vy = 0f;
            }
            contact = Contact.SURFACE;
        } else if (outsideVertically(targetY)) {
            t.y = Lerp.clamp(
                targetY, UtilityConfig.THROWABLE_RADIUS, WorldConfig.ARENA_HEIGHT - UtilityConfig.THROWABLE_RADIUS);
            if (bounce) {
                bounceY(t);
            } else {
                t.contactNormalX = 0f;
                t.contactNormalY = t.vy > 0f ? -1f : 1f;
                t.vy = 0f;
            }
            contact = contact == Contact.SURFACE ? Contact.SURFACE : Contact.BOUNDARY;
        } else {
            t.y = targetY;
        }

        if (contact != Contact.NONE) {
            t.bounces++;
            if (bounce) {
                settleIfSpent(t, map);
            }
        }
        return contact;
    }

    /** Reflects the normal component and scrubs the tangential one, for a vertical surface. */
    private static void bounceX(ThrownUtility t) {
        t.contactNormalX = t.vx > 0f ? -1f : 1f;
        t.contactNormalY = 0f;
        t.vx = -t.vx * UtilityConfig.BOUNCE_RESTITUTION;
        t.vy *= UtilityConfig.BOUNCE_TANGENT_FRICTION;
    }

    /** Reflects the normal component and scrubs the tangential one, for a horizontal surface. */
    private static void bounceY(ThrownUtility t) {
        t.contactNormalX = 0f;
        t.contactNormalY = t.vy > 0f ? -1f : 1f;
        t.vy = -t.vy * UtilityConfig.BOUNCE_RESTITUTION;
        t.vx *= UtilityConfig.BOUNCE_TANGENT_FRICTION;
    }

    /**
     * Parks a throwable that has run out of energy on top of something.
     *
     * <p>Both conditions matter. Speed alone would park a grenade at the apex of its arc, where
     * it is momentarily slow and very much still flying; ground contact alone would never park
     * one, because a 0.35 restitution bounce leaves a little energy forever.
     */
    private static void settleIfSpent(ThrownUtility t, ArenaMap map) {
        if (t.speed() > UtilityConfig.SETTLE_SPEED) {
            return;
        }
        boolean supported = t.contactNormalY > 0f
            || solidAt(map, t.x, t.y - UtilityConfig.THROWABLE_RADIUS - 1f) != null
            || t.y - UtilityConfig.THROWABLE_RADIUS <= 1f;
        if (supported) {
            t.resting = true;
            t.vx = 0f;
            t.vy = 0f;
        }
    }

    private static void clampSpeed(ThrownUtility t) {
        float speed = t.speed();
        if (speed > UtilityConfig.MAX_SPEED) {
            float scale = UtilityConfig.MAX_SPEED / speed;
            t.vx *= scale;
            t.vy *= scale;
        }
    }

    /**
     * The solid a throwable centred at {@code (x, y)} would be inside, or {@code null}.
     *
     * <p>Written out rather than calling {@code MapQueries.solidsOverlapping}, which builds a
     * {@link Rect} and a stream per call — this runs up to sixteen times per throwable per tick.
     * {@code PlayerMotion} sweeps the solid list by hand for the same reason.
     */
    public static Rect solidAt(ArenaMap map, float x, float y) {
        if (map == null) {
            return null;
        }
        float r = UtilityConfig.THROWABLE_RADIUS;
        float left = x - r;
        float right = x + r;
        float bottom = y - r;
        float top = y + r;
        for (Rect solid : map.solids()) {
            if (left < solid.right() && right > solid.left()
                && bottom < solid.top() && top > solid.bottom()) {
                return solid;
            }
        }
        return null;
    }

    private static boolean outsideHorizontally(float x) {
        return x - UtilityConfig.THROWABLE_RADIUS < 0f
            || x + UtilityConfig.THROWABLE_RADIUS > WorldConfig.ARENA_WIDTH;
    }

    private static boolean outsideVertically(float y) {
        return y - UtilityConfig.THROWABLE_RADIUS < 0f
            || y + UtilityConfig.THROWABLE_RADIUS > WorldConfig.ARENA_HEIGHT;
    }
}
