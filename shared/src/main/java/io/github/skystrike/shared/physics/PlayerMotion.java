package io.github.skystrike.shared.physics;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.Player;

/**
 * Pure simulation function for player motion, collision and rotation dynamics.
 *
 * <p>Shared verbatim by the authoritative server and client-side prediction.
 */
public final class PlayerMotion {

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

        // 2. Crouch state and ceiling clearance check
        updateCrouchState(p, input, map);

        // 3. Horizontal movement and damping
        float moveX = input != null ? Lerp.clamp(input.moveX, -1f, 1f) : 0f;
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

        if (input != null && input.jump && (p.grounded || p.coyoteTimer > 0f)) {
            p.vy = PlayerConfig.JUMP_SPEED;
            p.grounded = false;
            p.coyoteTimer = 0f;
        }

        if (input != null && input.jetpack && p.fuel > 0f) {
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

    private static void updateCrouchState(Player p, PlayerInput input, ArenaMap map) {
        boolean wantsCrouch = input != null && input.crouch;
        if (wantsCrouch) {
            p.crouched = true;
        } else if (p.crouched) {
            // Uncrouch requested: check if standing height fits under geometry
            Rect standingHitbox = new Rect(
                p.x - PlayerConfig.WIDTH / 2f,
                p.y,
                PlayerConfig.WIDTH,
                PlayerConfig.STAND_HEIGHT);
            if (MapQueries.overlapsSolid(map, standingHitbox)) {
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

    private static void integrateAndCollide(Player p, float dt, ArenaMap map) {
        float width = PlayerConfig.WIDTH;
        float height = p.currentHeight();

        // --- Move along X ---
        float dx = p.vx * dt;
        float x0 = p.x - width / 2f;
        float y0 = p.y;

        if (dx > 0f) {
            float minDx = dx;
            boolean hit = false;
            for (Rect s : map.solids()) {
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
            for (Rect s : map.solids()) {
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
            float minDy = dy;
            boolean hitCeiling = false;
            for (Rect s : map.solids()) {
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
            for (Rect s : map.solids()) {
                if (x0 < s.right() && x0 + width > s.left()) {
                    if (s.top() <= y0 && s.top() >= y0 + maxDy) {
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
            for (Rect s : map.solids()) {
                if (x0 < s.right() && x0 + width > s.left()) {
                    if (s.top() <= p.y + PlayerConfig.GROUND_SKIN && s.top() >= p.y - PlayerConfig.GROUND_SKIN) {
                        if (s.top() > groundSurface) {
                            groundSurface = s.top();
                        }
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
