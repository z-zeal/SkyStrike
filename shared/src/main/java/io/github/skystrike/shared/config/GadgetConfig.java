package io.github.skystrike.shared.config;

/**
 * Drone, throw camera, shield and fuel tank parameters (mechanics §7, roadmap Phase 6).
 *
 * <p>Cross-gadget shape — identity, behaviour, durability — lives with the definitions in
 * {@code shared/gadget/GadgetRegistry}; what is here is the per-gadget tuning only one gadget
 * has, exactly as the structure plan assigns it.
 *
 * <p><b>Reference units.</b> Mechanics §7 quotes the gadget distances and speeds in the plan's
 * old small-scale reference units ("speed 8", "10 units at 70°", "a 4-unit radius", "1 unit
 * above you"), while the arena, the throwables and the vision system are specified directly in
 * world units (3000×2000 arena, 640-unit cone, 350-unit frag radius). The two scales are
 * related through the player: 50 world units of standing height against the reference scale's
 * two-unit character gives {@link #WORLD_UNITS_PER_REFERENCE_UNIT} = 25. Every converted
 * constant below states its §7 source value; the conversion itself exists exactly once, here.
 *
 * <p>Values marked <b>provisional</b> are ones the mechanics plan leaves blank. They are
 * flagged so that a later balance pass knows which numbers were specified and which were
 * chosen.
 */
public final class GadgetConfig {

    // --- Unit conversion (mechanics §7 reference scale → world units) --------------------------

    /**
     * World units per mechanics-§7 reference unit. Derived from the player: 50 world units of
     * standing height ({@link PlayerConfig#STAND_HEIGHT}) for the reference scale's two-unit
     * character, so 25. Sanity check at this scale: drone speed 8 → 200 u/s, exactly a player's
     * walk speed; its 10-unit cone → 250 units, well inside the player's 640 and "narrower";
     * the fuel tank's 4-unit blast → 100 units, a close-quarters pocket against the frag's 350.
     */
    public static final float WORLD_UNITS_PER_REFERENCE_UNIT = 25f;

    // --- Slots (mechanics §7, §8) ---------------------------------------------------------------

    /** Gadget slots a player has: Q and E, independent of the five main slots. */
    public static final int GADGET_SLOTS = 2;

    // --- Drone (mechanics §7.1) -----------------------------------------------------------------

    /** Drone hit points. Destroyed by gunfire. */
    public static final float DRONE_HEALTH = 30f;

    /** Free-flight speed: §7.1's "speed 8" × 25 = 200 u/s — a drone flies at player walk speed. */
    public static final float DRONE_SPEED = 8f * WORLD_UNITS_PER_REFERENCE_UNIT;

    /** The drone deploys this far above the owner: §7.1's "1 unit above you" × 25. */
    public static final float DRONE_SPAWN_OFFSET_Y = 1f * WORLD_UNITS_PER_REFERENCE_UNIT;

    /** Reach of the drone's own vision cone: §7.1's "10 units" × 25 = 250, against 640 for a player. */
    public static final float DRONE_VISION_RANGE = 10f * WORLD_UNITS_PER_REFERENCE_UNIT;

    /** Full angle of the drone's vision cone in degrees, against 120° for a player. */
    public static final float DRONE_VISION_ANGLE_DEGREES = 70f;

    /**
     * Brightness multiplier of the drone's cone relative to a player's. <b>Provisional</b> —
     * §7.1 says "dimmer than a player's" without a number.
     */
    public static final float DRONE_VISION_BRIGHTNESS = 0.7f;

    /**
     * Exponential rate at which drone velocity lerps toward the piloted input direction, per
     * second. <b>Provisional</b> — §7.1 asks for "smooth velocity lerping" without a rate; 8/s
     * reaches ~63% of a direction change in an eighth of a second, responsive but visibly smooth.
     */
    public static final float DRONE_VELOCITY_LERP_RATE = 8f;

    /**
     * Linear damping applied to a drone with no pilot input, per second. <b>Provisional</b> —
     * §7.1 asks for "damping" without a number; 3/s coasts to a near-stop in about a second.
     */
    public static final float DRONE_DAMPING = 3f;

    /**
     * Collision half-extent of a drone, in world units. <b>Provisional</b> — §7.1 sizes nothing;
     * a drone reads as roughly half a player wide (player half-width 15), so 8 it is until the
     * art pass says otherwise. Both the server's motion and the bullet sweep use this one value.
     */
    public static final float DRONE_RADIUS = 8f;

    // --- Throw camera (mechanics §7.2) ------------------------------------------------------------

    /** Camera hit points. Destructible, and destroyed if it leaves the arena. */
    public static final float CAMERA_HEALTH = 20f;

    /** Launch speed along the aim direction: §7.2's "speed 12" × 25 = 300 u/s. */
    public static final float CAMERA_THROW_SPEED = 12f * WORLD_UNITS_PER_REFERENCE_UNIT;

    /**
     * Gravity on the camera's flight arc. §7.2 says "a gravity arc" without its own number, so
     * the camera flies under the one throwable gravity ({@link UtilityConfig#GRAVITY}) — which
     * also lets the trajectory preview reuse the identical shared integrator.
     */
    public static final float CAMERA_GRAVITY = UtilityConfig.GRAVITY;

    /** View magnification while watching through the camera. */
    public static final float CAMERA_ZOOM = 1.2f;

    /**
     * A stuck camera is permanent: no lifetime, no decay. It dies only to damage or to leaving
     * the arena — persistence is the whole trade against the drone's mobility.
     */
    public static final boolean CAMERA_PERMANENT = true;

    /**
     * Reach of the camera's vision cone. <b>Provisional</b> — §7.2 gives the camera zoom and
     * persistence but no cone; the drone's 250-unit reach is reused until tuned.
     */
    public static final float CAMERA_VISION_RANGE = DRONE_VISION_RANGE;

    /**
     * Full angle of the camera's vision cone in degrees. <b>Provisional</b> — reuses the
     * drone's 70° until tuned.
     */
    public static final float CAMERA_VISION_ANGLE_DEGREES = DRONE_VISION_ANGLE_DEGREES;

    /**
     * Collision half-extent of a camera, in world units. The camera flies with the one shared
     * throwable integrator, whose contact position is computed at {@link UtilityConfig#THROWABLE_RADIUS}
     * — so the camera's radius <b>is</b> the throwable radius, and a camera stuck to a floor rests
     * exactly tangent to it, neither sunk in nor floating above.
     */
    public static final float CAMERA_RADIUS = UtilityConfig.THROWABLE_RADIUS;

    // --- Shield (mechanics §7.3) -----------------------------------------------------------------

    /** Total damage the shield absorbs before breaking permanently. */
    public static final float SHIELD_DURABILITY = 150f;

    /** Full frontal protection arc while equipped, in degrees, centred on the aim direction. */
    public static final float SHIELD_FRONT_ARC_DEGREES = 90f;

    /**
     * Full rear protection arc while stowed, in degrees, centred directly behind the aim.
     * <b>Provisional</b> — §7.3 says stowed "protects your rear from anything hitting from
     * behind" without quantifying the arc; it is kept symmetric with the frontal 90° until the
     * plan says otherwise.
     */
    public static final float SHIELD_REAR_ARC_DEGREES = 90f;

    // --- Fuel tank (mechanics §7.4) ---------------------------------------------------------------

    /** Jetpack fuel capacity multiplier while the tank is worn and intact. */
    public static final float FUEL_TANK_CAPACITY_MULTIPLIER = 1.75f;

    /** Jetpack thrust multiplier while the tank is worn and intact. */
    public static final float FUEL_TANK_THRUST_MULTIPLIER = 1.40f;

    /**
     * Blast damage of a detonating tank, for anyone caught in the radius. §7.4 separately says
     * the detonation kills <i>the wearer</i> outright — 120 alone does not finish a full-health
     * 150 player, so the wearer's death is an unconditional rule for the authoritative explosion
     * increment, not a property of this number.
     */
    public static final float FUEL_TANK_EXPLOSION_DAMAGE = 120f;

    /** Blast radius of a detonating tank: §7.4's "4-unit radius" × 25 = 100 world units. */
    public static final float FUEL_TANK_EXPLOSION_RADIUS = 4f * WORLD_UNITS_PER_REFERENCE_UNIT;

    /**
     * Physics impulse at the centre of a tank detonation. <b>Provisional</b> — §7.4 gives no
     * impulse; sized just under the frag's 950 for a blast that is deadlier but tighter.
     */
    public static final float FUEL_TANK_EXPLOSION_IMPULSE = 800f;

    private GadgetConfig() {
    }
}
