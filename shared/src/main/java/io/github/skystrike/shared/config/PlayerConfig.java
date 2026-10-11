package io.github.skystrike.shared.config;

/**
 * Character tuning: walk and cap speeds, crouch, gravity, damping, jump, jetpack fuel and the
 * rotation springs (mechanics §2).
 */
public final class PlayerConfig {

    // --- Hitbox dimensions (mechanics §2) ----------------------------------------------------
    public static final float WIDTH = 30f;
    public static final float STAND_HEIGHT = 50f;
    public static final float CROUCH_HEIGHT = 30f;
    public static final float EYE_HEIGHT_FRACTION = 0.85f;

    // --- Health ------------------------------------------------------------------------------
    public static final float MAX_HEALTH = 150f;

    // --- Ground & Air Movement (mechanics §2.1) ----------------------------------------------
    public static final float WALK_SPEED = 200f;
    public static final float CROUCH_SPEED = 100f;
    public static final float MAX_HORIZONTAL_SPEED = 300f;
    public static final float MAX_VERTICAL_SPEED = 1000f;
    public static final float GRAVITY = -800f;
    public static final float GROUND_DAMPING = 16f;
    public static final float AIR_DAMPING = 1.0f;
    public static final float GROUND_SKIN = 2.0f;

    // --- One-way platforms (map plan §4, D6) ---------------------------------------------------
    /**
     * Seconds a drop-through suspends one-way platform tops. The window is started by a fresh
     * crouch press — S alone, no jump — while standing on a one-way top (map plan §4, D6). It
     * normally ends much earlier — the moment the body's feet are no longer in a slab — so this is
     * the safety cap for a body that somehow stays inside one. Half a second is several times the
     * fall time of the thinnest authored slab at any tick rate, and short enough that a body can
     * never carry the suspension into a second manoeuvre.
     */
    public static final float PLATFORM_DROP_THROUGH_SECONDS = 0.35f;

    // --- Jump and Jetpack (mechanics §2.2) ----------------------------------------------------
    public static final float JUMP_SPEED = 420f;
    public static final float MAX_FUEL = 100f;
    public static final float FUEL_BURN_RATE = 20f;
    public static final float FUEL_RECHARGE_RATE = 20f;
    public static final float JETPACK_THRUST = 1000f;
    public static final float JETPACK_VERTICAL_SPLIT = 0.85f;
    public static final float JETPACK_AIM_SPLIT = 0.15f;

    // --- Body Rotation Dynamics (mechanics §2.3) ---------------------------------------------
    public static final float GROUND_SPRING_STRENGTH = 120f;
    public static final float GROUND_ANGULAR_DAMPING = 20f;
    public static final float AIR_SPRING_STRENGTH = 50f;
    public static final float AIR_ANGULAR_DAMPING = 4.0f;
    public static final float COYOTE_TIME = 0.10f;
    public static final float JETPACK_LEAN_SCALE = 0.04f;
    public static final float AIM_LEAN_FACTOR = 0.12f;
    public static final float AIM_LEAN_CAP_DEGREES = 30f;
    public static final float VELOCITY_BANK_FACTOR = 0.08f;
    public static final float BACKPEDAL_SPEED_THRESHOLD = 25f;
    public static final float BACKPEDAL_BANK_MULTIPLIER = 2.40f;

    // --- Aim & ADS (mechanics §2.4, §3) ------------------------------------------------------
    public static final float ADS_CAMERA_PAN = 150f;
    public static final float ADS_TRANSITION_RATE = 10f;
    public static final float VISION_REACH_HIP = 640f;
    public static final float VISION_REACH_ADS = 1024f;

    private PlayerConfig() {
    }
}
