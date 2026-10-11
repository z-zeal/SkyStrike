package io.github.skystrike.shared.physics;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockRules;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.MovementRule;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;
import java.util.List;

/**
 * Pure simulation function for player motion, collision and rotation dynamics.
 *
 * <p>Shared verbatim by the authoritative server and client-side prediction.
 *
 * <p>Collision reads the movement column of the shared {@link BlockRules} table through
 * {@link ArenaMap#movementBlockers(MovementRule)}: {@link MovementRule#SOLID} rects block on every
 * face, and a {@link MovementRule#ONE_WAY_TOP} slab only ever answers a falling body's landing from
 * above (map plan §3, §4). No kind is named here.
 */
public final class PlayerMotion {

    /**
     * Float slack for "flush against a face" tests. A body snapped onto a slab top must still land
     * on it on the following step, when the same top is met again from exactly that height; without
     * slack, float rounding a hair below the top would read as "inside the slab" and the body would
     * sink through. It is two orders of magnitude below the ground skin and the thinnest geometry.
     */
    private static final float TOUCH_EPSILON = 0.01f;

    private PlayerMotion() {
    }

    /**
     * The jetpack tank size for this player: the ordinary capacity, multiplied while a worn,
     * intact fuel tank is in the loadout (mechanics §7).
     *
     * <p>Public because the HUD's fuel bar has to divide by exactly this number. A second copy
     * of the multiplier rule would put a full bar and a full tank out of step the moment a tank
     * breaks, which is precisely the kind of disagreement the M4 gate forbids.
     */
    public static float fuelCapacity(Player p) {
        boolean hasFuelTank = p != null && p.loadout != null && p.loadout.hasFuelTank();
        return PlayerConfig.MAX_FUEL
            * (hasFuelTank
                ? io.github.skystrike.shared.config.GadgetConfig.FUEL_TANK_CAPACITY_MULTIPLIER
                : 1f);
    }

    /**
     * Steps player state forward by {@code dt} seconds given {@code input} and {@code map}.
     *
     * <p>Pure function: leaves {@code state} untouched and returns a newly stepped instance.
     */
    public static Player step(Player state, PlayerInput input, float dt, ArenaMap map) {
        return step(state, input, dt, map, false);
    }

    /**
     * {@code noclip} variant (build plan M3 §4, {@code sv_noclip}): skips gravity and the swept
     * AABB collision sweep entirely, flying freely within the arena bounds instead.
     */
    public static Player step(Player state, PlayerInput input, float dt, ArenaMap map, boolean noclip) {
        Player next = state.copy();
        stepInPlace(next, input, dt, map, noclip);
        return next;
    }

    /**
     * In-place variant for performance-sensitive loops where allocation is avoided.
     */
    public static void stepInPlace(Player p, PlayerInput input, float dt, ArenaMap map) {
        stepInPlace(p, input, dt, map, false);
    }

    /**
     * {@code noclip} variant of {@link #stepInPlace(Player, PlayerInput, float, ArenaMap)} (build
     * plan M3 §4): the one place {@code sv_noclip} actually changes simulation behaviour, per the
     * gate — a server command toggles a session flag, and this is where the flag is honoured.
     *
     * <p><b>Surveillance lock</b> (mechanics §7, §9): while the player is looking through a
     * drone or camera, the body is frozen and defenceless — its movement intent is ignored here
     * and its weapons are blocked in the loadout tick. The lock lives in this shared function so
     * server authority and client prediction freeze the body identically; the aim still tracks
     * the cursor, because that aim is what steers the device's vision cone.
     */
    public static void stepInPlace(Player p, PlayerInput input, float dt, ArenaMap map, boolean noclip) {
        if (dt <= 0f) {
            return;
        }

        // 1. Throwable status timers. They are part of movement state so shared prediction and
        // server authority age the exact same clocks.
        p.tickStatus(dt);

        // 2. Aim and ADS state
        if (input != null) {
            p.aimAngle = Angles.wrap(input.aimAngle);
            p.ads = input.ads;
            p.lastProcessedInputSequence = input.sequence;
        }

        // The surveillance lock zeroes the body's movement intent from here down; aim (above)
        // still flows, so the piloted device's cone follows the cursor.
        boolean locked = p.isSurveillanceLocked();

        // 2. Crouch state and ceiling clearance check
        updateCrouchState(p, input != null && input.crouch && !locked, map);

        // 3. Horizontal movement and damping
        float moveX = input != null ? Lerp.clamp(input.moveX, -1f, 1f) : 0f;
        if (locked) {
            moveX = 0f;
        }
        float targetSpeed = p.crouched ? PlayerConfig.CROUCH_SPEED : PlayerConfig.WALK_SPEED;
        if (p.isSlowed()) {
            targetSpeed *= io.github.skystrike.shared.config.UtilityConfig.STUN_MOVE_SPEED_MULTIPLIER;
        }
        float targetVx = moveX * targetSpeed;
        float dampingRate = p.grounded ? PlayerConfig.GROUND_DAMPING : PlayerConfig.AIR_DAMPING;
        p.vx = Lerp.smooth(p.vx, targetVx, dampingRate, dt);
        if (Math.abs(p.vx) < 0.001f) {
            p.vx = 0f;
        }
        p.vx = Lerp.clamp(p.vx, -PlayerConfig.MAX_HORIZONTAL_SPEED, PlayerConfig.MAX_HORIZONTAL_SPEED);

        // 4. Vertical movement (Jump, Jetpack, Gravity). A worn, intact fuel tank changes
        // only the shared jetpack function; both server authority and local prediction call this
        // same code. A tank breaking also clamps fuel back to the ordinary capacity immediately.
        boolean hasFuelTank = p.loadout != null && p.loadout.hasFuelTank();
        float fuelCapacity = fuelCapacity(p);
        p.fuel = Math.min(Math.max(0f, p.fuel), fuelCapacity);

        if (input != null && input.jump && !locked && (p.grounded || p.coyoteTimer > 0f)) {
            if (input.crouch && standsOnOneWayTop(p, map)) {
                // Crouch+jump on a one-way platform drops through it (map plan §4, D6): the press
                // starts the suspension window instead of a jump. The coyote clock is cleared with
                // the grounded flag so the same held press cannot launch the body back up through
                // the slab it is leaving.
                p.dropThroughTimer = PlayerConfig.PLATFORM_DROP_THROUGH_SECONDS;
                p.grounded = false;
                p.coyoteTimer = 0f;
            } else {
                p.vy = PlayerConfig.JUMP_SPEED;
                p.grounded = false;
                p.coyoteTimer = 0f;
            }
        }

        if (input != null && input.jetpack && !locked && p.fuel > 0f) {
            p.jetpacking = true;
            float aimRad = Angles.toRadians(p.aimAngle);
            float thrust = PlayerConfig.JETPACK_THRUST
                * (hasFuelTank ? io.github.skystrike.shared.config.GadgetConfig.FUEL_TANK_THRUST_MULTIPLIER : 1f);
            float thrustAx = thrust
                * PlayerConfig.JETPACK_AIM_SPLIT
                * (float) Math.cos(aimRad);
            float thrustAy = thrust
                * (PlayerConfig.JETPACK_VERTICAL_SPLIT + PlayerConfig.JETPACK_AIM_SPLIT * (float) Math.sin(aimRad));
            p.vx += thrustAx * dt;
            p.vy += thrustAy * dt;
            p.fuel = Math.max(0f, p.fuel - PlayerConfig.FUEL_BURN_RATE * dt);
        } else {
            p.jetpacking = false;
        }

        if (!noclip) {
            p.vy += PlayerConfig.GRAVITY * dt;
        }
        p.vy = Lerp.clamp(p.vy, -PlayerConfig.MAX_VERTICAL_SPEED, PlayerConfig.MAX_VERTICAL_SPEED);

        // 5. Swept AABB collision & Integration, or a free fly through everything when noclip.
        if (noclip) {
            flyFreely(p, dt);
        } else {
            integrateAndCollide(p, dt, map);
        }

        // 6. Grounded state recharge and coyote timer
        if (p.grounded) {
            p.fuel = Math.min(fuelCapacity, p.fuel + PlayerConfig.FUEL_RECHARGE_RATE * dt);
            p.coyoteTimer = PlayerConfig.COYOTE_TIME;
        } else {
            p.coyoteTimer = Math.max(0f, p.coyoteTimer - dt);
        }

        // 7. Body Rotation Dynamics
        updateRotationDynamics(p, dt);
    }

    private static void updateCrouchState(Player p, boolean wantsCrouch, ArenaMap map) {
        if (wantsCrouch) {
            p.crouched = true;
        } else if (p.crouched) {
            // Uncrouch requested: check if standing height fits under geometry that stops a body.
            // A one-way slab overhead is not such geometry — a body rises through it — while
            // intact glass and a closed door are.
            Rect standingHitbox = new Rect(
                p.x - PlayerConfig.WIDTH / 2f,
                p.y,
                PlayerConfig.WIDTH,
                PlayerConfig.STAND_HEIGHT);
            if (MapQueries.overlapsMovementBlocker(map, standingHitbox)) {
                p.crouched = true; // Ceiling blockage prevents standing
            } else {
                p.crouched = false;
            }
        }
    }

    /** {@code sv_noclip}: straight-line integration, no solids, never grounded. */
    private static void flyFreely(Player p, float dt) {
        float width = PlayerConfig.WIDTH;
        float height = p.currentHeight();
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.x = Lerp.clamp(p.x, width / 2f, WorldConfig.ARENA_WIDTH - width / 2f);
        p.y = Lerp.clamp(p.y, 0f, WorldConfig.ARENA_HEIGHT - height);
        p.grounded = false;
    }

    /**
     * True when the body is standing on a one-way top: a slab whose top face is at the body's feet
     * within the ground skin, and which the body overlaps horizontally.
     *
     * <p>This is the "is there a platform under me right now" test that decides whether a
     * crouch+jump is a drop-through or an ordinary jump. It only ever matches geometry the sweeper
     * could be supporting the body with, so on a wall-only arena it is always false and
     * crouch+jump keeps its plain-jump meaning.
     */
    private static boolean standsOnOneWayTop(Player p, ArenaMap map) {
        float left = p.x - PlayerConfig.WIDTH / 2f;
        float right = left + PlayerConfig.WIDTH;
        for (Rect slab : map.movementBlockers(MovementRule.ONE_WAY_TOP)) {
            if (left >= slab.right() || right <= slab.left()) {
                continue;
            }
            float top = slab.top();
            if (top <= p.y + PlayerConfig.GROUND_SKIN && top >= p.y - PlayerConfig.GROUND_SKIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ages the crouch+jump drop-through window (map plan §4, D6).
     *
     * <p>The window suspends one-way tops, so it ends the moment it is no longer needed: when the
     * body's feet have left the slab — below it, because the drop completed, or above it, because
     * the body rose out. The countdown is the safety cap for a body that somehow stays inside one.
     */
    private static void tickDropThrough(Player p, float dt, List<Rect> oneWayTops) {
        if (p.dropThroughTimer <= 0f) {
            return;
        }
        if (!feetInsideOneWaySlab(p, oneWayTops)) {
            p.dropThroughTimer = 0f;
            return;
        }
        p.dropThroughTimer = Math.max(0f, p.dropThroughTimer - dt);
    }

    /**
     * True when the body's feet are inside a one-way slab's vertical band while it overlaps the
     * slab horizontally.
     *
     * <p>Contact counts as inside. A body standing on a platform has its feet exactly on the top
     * face, which is the very position a drop starts from, so a strict interior test would clear
     * the window before the body moved and let it land again immediately.
     */
    private static boolean feetInsideOneWaySlab(Player p, List<Rect> oneWayTops) {
        float left = p.x - PlayerConfig.WIDTH / 2f;
        float right = left + PlayerConfig.WIDTH;
        for (Rect slab : oneWayTops) {
            if (left >= slab.right() || right <= slab.left()) {
                continue;
            }
            if (p.y <= slab.top() + TOUCH_EPSILON && p.y >= slab.bottom() - TOUCH_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static void integrateAndCollide(Player p, float dt, ArenaMap map) {
        float width = PlayerConfig.WIDTH;
        float height = p.currentHeight();

        // The movement column of the one shared rule table, asked once per step rather than once
        // per rect per axis. SOLID rects are the world's walls, floors and ceilings; one-way tops
        // may only ever answer the falling-body landing below.
        List<Rect> movementSolids = map.movementBlockers(MovementRule.SOLID);
        List<Rect> oneWayTops = map.movementBlockers(MovementRule.ONE_WAY_TOP);
        tickDropThrough(p, dt, oneWayTops);
        boolean oneWayActive = p.dropThroughTimer <= 0f;

        // --- Move along X ---
        float dx = p.vx * dt;
        float x0 = p.x - width / 2f;
        float y0 = p.y;

        if (dx > 0f) {
            float minDx = dx;
            boolean hit = false;
            for (Rect s : movementSolids) {
                if (y0 < s.top() && y0 + height > s.bottom()) {
                    if (s.left() >= x0 + width && s.left() <= x0 + width + minDx) {
                        minDx = s.left() - (x0 + width);
                        hit = true;
                    }
                }
            }
            dx = Math.max(0f, minDx);
            if (hit) {
                p.vx = 0f;
            }
        } else if (dx < 0f) {
            float maxDx = dx;
            boolean hit = false;
            for (Rect s : movementSolids) {
                if (y0 < s.top() && y0 + height > s.bottom()) {
                    if (s.right() <= x0 && s.right() >= x0 + maxDx) {
                        maxDx = s.right() - x0;
                        hit = true;
                    }
                }
            }
            dx = Math.min(0f, maxDx);
            if (hit) {
                p.vx = 0f;
            }
        }

        p.x += dx;
        // Clamp to arena horizontal bounds
        p.x = Lerp.clamp(
            p.x, width / 2f, WorldConfig.ARENA_WIDTH - width / 2f);

        // --- Move along Y ---
        float dy = p.vy * dt;
        x0 = p.x - width / 2f;
        y0 = p.y;
        boolean hitGround = false;

        if (dy > 0f) {
            // A one-way top is never a ceiling: only the SOLID movement row stops a rise.
            float minDy = dy;
            boolean hitCeiling = false;
            for (Rect s : movementSolids) {
                if (x0 < s.right() && x0 + width > s.left()) {
                    if (s.bottom() >= y0 + height && s.bottom() <= y0 + height + minDy) {
                        minDy = s.bottom() - (y0 + height);
                        hitCeiling = true;
                    }
                }
            }
            dy = Math.max(0f, minDy);
            if (hitCeiling) {
                p.vy = 0f;
            }
        } else if (dy < 0f) {
            float maxDy = dy;
            for (Rect s : movementSolids) {
                if (x0 < s.right() && x0 + width > s.left()) {
                    if (s.top() <= y0 && s.top() >= y0 + maxDy) {
                        maxDy = s.top() - y0;
                        hitGround = true;
                    }
                }
            }
            if (oneWayActive) {
                for (Rect s : oneWayTops) {
                    if (x0 >= s.right() || x0 + width <= s.left()) {
                        continue;
                    }
                    // Only a top the body was at or above on the previous step: a body already
                    // inside the slab is not caught by it, and a body below it is not pulled up.
                    // The drop-through window above suppresses even that.
                    if (s.top() > y0 + TOUCH_EPSILON) {
                        continue;
                    }
                    if (s.top() >= y0 + maxDy) {
                        maxDy = s.top() - y0;
                        hitGround = true;
                    }
                }
            }
            dy = Math.min(0f, maxDy);
            if (hitGround) {
                p.vy = 0f;
                p.grounded = true;
            }
        }

        p.y += dy;

        // Ground detection with skin when vy <= 0
        if (!hitGround && p.vy <= 0f) {
            float groundSurface = -1f;
            for (Rect s : movementSolids) {
                if (x0 < s.right() && x0 + width > s.left()) {
                    if (s.top() <= p.y + PlayerConfig.GROUND_SKIN && s.top() >= p.y - PlayerConfig.GROUND_SKIN) {
                        if (s.top() > groundSurface) {
                            groundSurface = s.top();
                        }
                    }
                }
            }
            if (oneWayActive) {
                for (Rect s : oneWayTops) {
                    if (x0 >= s.right() || x0 + width <= s.left()) {
                        continue;
                    }
                    // Only a top at or below the feet may support. A SOLID row keeps its eject-out
                    // behaviour, which is how a body embedded in a wall is freed; a one-way slab
                    // never pushes a body up, so a body already inside one is not ejected.
                    if (s.top() <= p.y + TOUCH_EPSILON
                            && s.top() >= p.y - PlayerConfig.GROUND_SKIN
                            && s.top() > groundSurface) {
                        groundSurface = s.top();
                    }
                }
            }
            if (groundSurface >= 0f) {
                p.y = groundSurface;
                p.vy = 0f;
                p.grounded = true;
            } else {
                p.grounded = false;
            }
        } else if (!hitGround) {
            p.grounded = false;
        }

        // Clamp to arena vertical bounds
        p.y = Lerp.clamp(p.y, 0f, WorldConfig.ARENA_HEIGHT - height);
    }

    private static void updateRotationDynamics(Player p, float dt) {
        if (p.grounded) {
            // Grounded: upright spring 120 deg/s², angular damping 20/s, snap to 0°
            float delta = Angles.shortestDelta(p.rotation, 0f);
            float alpha = PlayerConfig.GROUND_SPRING_STRENGTH * delta
                - PlayerConfig.GROUND_ANGULAR_DAMPING * p.angularVelocity;
            p.angularVelocity += alpha * dt;
            p.rotation = Angles.wrap(p.rotation + p.angularVelocity * dt);
            if (Math.abs(p.rotation) < 0.2f && Math.abs(p.angularVelocity) < 1.0f) {
                p.rotation = 0f;
                p.angularVelocity = 0f;
            }
        } else if (p.coyoteTimer > 0f) {
            // Coyote lock 0.10s after leaving ground: stays upright
            float delta = Angles.shortestDelta(p.rotation, 0f);
            float alpha = PlayerConfig.GROUND_SPRING_STRENGTH * delta
                - PlayerConfig.GROUND_ANGULAR_DAMPING * p.angularVelocity;
            p.angularVelocity += alpha * dt;
            p.rotation = Angles.wrap(p.rotation + p.angularVelocity * dt);
            if (Math.abs(p.rotation) < 0.2f) {
                p.rotation = 0f;
            }
        } else {
            // Airborne: spring 50 deg/s², damping 4.0
            boolean facingRight = p.isFacingRight();
            float speedX = Math.abs(p.vx);
            boolean isBackpedal = speedX > PlayerConfig.BACKPEDAL_SPEED_THRESHOLD
                && ((facingRight && p.vx < -PlayerConfig.BACKPEDAL_SPEED_THRESHOLD)
                    || (!facingRight && p.vx > PlayerConfig.BACKPEDAL_SPEED_THRESHOLD));

            float aimLean;
            float bankFactor;
            if (isBackpedal) {
                // Backpedal case: aim torque 0, velocity bank x2.40
                aimLean = 0f;
                bankFactor = PlayerConfig.VELOCITY_BANK_FACTOR * PlayerConfig.BACKPEDAL_BANK_MULTIPLIER;
            } else {
                // Aim lean: factor 0.12, capped at 30 deg influence
                float aimOffset = Angles.shortestDelta(90f, p.aimAngle);
                aimLean = Lerp.clamp(
                    -aimOffset * PlayerConfig.AIM_LEAN_FACTOR,
                    -PlayerConfig.AIM_LEAN_CAP_DEGREES,
                    PlayerConfig.AIM_LEAN_CAP_DEGREES);
                bankFactor = PlayerConfig.VELOCITY_BANK_FACTOR;
            }

            // Velocity bank: moving right tips right (negative CCW angle)
            float bankLean = -p.vx * bankFactor;

            // Jetpack lean
            float jetpackLean = 0f;
            if (p.jetpacking) {
                jetpackLean = -(float) Math.cos(Angles.toRadians(p.aimAngle))
                    * (PlayerConfig.JETPACK_THRUST * PlayerConfig.JETPACK_AIM_SPLIT)
                    * PlayerConfig.JETPACK_LEAN_SCALE;
            }

            float targetRot = aimLean + bankLean + jetpackLean;
            float delta = Angles.shortestDelta(p.rotation, targetRot);
            float alpha = PlayerConfig.AIR_SPRING_STRENGTH * delta
                - PlayerConfig.AIR_ANGULAR_DAMPING * p.angularVelocity;
            p.angularVelocity += alpha * dt;
            p.rotation = Angles.wrap(p.rotation + p.angularVelocity * dt);
        }
    }
}
