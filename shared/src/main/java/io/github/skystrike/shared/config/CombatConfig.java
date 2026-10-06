package io.github.skystrike.shared.config;

/**
 * Hit-zone ratios, damage multipliers, distance falloff and friendly-fire rules (mechanics §4).
 *
 * <p>Cross-weapon firing constants (burst spacing, pellet counts, recoil caps) live in
 * {@link WeaponConfig}; per-weapon numbers live with the weapon definitions. Nothing here is a
 * per-weapon value.
 */
public final class CombatConfig {

    // --- Health and hit zones (mechanics §4.1) -----------------------------------------------
    public static final float MAX_HEALTH = 150f;

    /** Head is the top 28% of the player's <b>current</b> height, so crouching lowers it. */
    public static final float HEAD_ZONE_FRACTION = 0.28f;

    /** Fraction of current height at which the head zone starts (1 − 0.28). */
    public static final float HEAD_ZONE_START_FRACTION = 1f - HEAD_ZONE_FRACTION;

    public static final float HEAD_DAMAGE_MULTIPLIER = 2.0f;
    public static final float BODY_DAMAGE_MULTIPLIER = 1.0f;

    /**
     * Fuel-tank zone multiplier. Deliberately 1.0: the tank's answer to being shot is the
     * {@code GadgetConfig} detonation applied by the server's gadget system, not a scaled
     * version of the bullet's damage. The zone resolves only while a worn, intact tank is in
     * the target's loadout.
     */
    public static final float FUEL_TANK_DAMAGE_MULTIPLIER = 1.0f;

    /** Width of the rear-mounted fuel tank as a fraction of the player hitbox width. */
    public static final float FUEL_TANK_WIDTH_FRACTION = 0.30f;

    /** Vertical band the rear tank occupies, as fractions of current height. */
    public static final float FUEL_TANK_BOTTOM_FRACTION = 0.30f;
    public static final float FUEL_TANK_TOP_FRACTION = 0.72f;

    // --- Friendly fire (mechanics §4.6) ------------------------------------------------------
    /** On by default: your rounds, grenades and fire hurt your own team. */
    public static final boolean FRIENDLY_FIRE = true;

    /** On by default: you can kill yourself with your own ordnance. */
    public static final boolean SELF_DAMAGE = true;

    /** Team index that fights, and is fought by, everyone. */
    public static final int NEUTRAL_TEAM_INDEX = 2;

    // --- Ballistics (mechanics §4.2) ---------------------------------------------------------
    /**
     * Units/s² of bullet drop per unit of per-class gravity. The mechanics plan quotes the class
     * numbers as relative weights (sniper 1.0 → shotgun 5.0); this is the scale that turns them
     * into an acceleration. Halved for the big map: every class drops half as hard while class
     * differences remain intact. Sidearms use modest 1.5/2.0 weights alongside their dedicated
     * speed and drag conversion, keeping normal-range handgun drop below two world units without
     * turning rifles and snipers into hitscan weapons.
     */
    public static final float BULLET_GRAVITY_BASE = 150f;

    /**
     * Above this speed a projectile is swept from its previous to its new position instead of
     * being point-tested. At 60 Hz the slowest gun still covers 15 units a tick and the thinnest
     * geometry in the arena is the 14-unit tunnel roof, so every gun is swept.
     */
    public static final float SWEEP_SPEED_THRESHOLD = 100f;

    /** Distance from the player's eye at which a round is spawned, matching the drawn barrel. */
    public static final float MUZZLE_OFFSET = 22f;

    /** A round cannot hit its owner for this long, so the muzzle is never inside your own hitbox. */
    public static final float SELF_HIT_GRACE_SECONDS = 0.05f;

    /**
     * Hard lifetime cap: a stalled round falls out of the fight instead of raining down later.
     * Raised for the big map — a fast round crossing the arena end to end is airborne for well
     * over two seconds, and it should land before it is deleted.
     */
    public static final float MAX_PROJECTILE_LIFETIME = 6.0f;

    /**
     * Rounds slower than this have bled out their energy and are removed. Low enough that a
     * dragged-out pistol round still counts as flying rather than vanishing at half speed.
     */
    public static final float PROJECTILE_MIN_SPEED = 60f;

    /**
     * Rounds are removed once they have travelled this multiple of the weapon's maximum range.
     * Maximum range is the damage-floor distance, not the end of the flight: past it a round
     * keeps flying at floor damage instead of vanishing mid-air, and only this multiple reaps it.
     */
    public static final float MAX_RANGE_OVERSHOOT = 4.0f;

    /**
     * Upper bound on simultaneously live rounds, so a stuck trigger cannot exhaust the heap.
     * Longer-lived rounds need the headroom: at the previous cap a busy big-map fight could hit
     * the ceiling simply because every round stayed alive three times as long.
     */
    public static final int MAX_ACTIVE_PROJECTILES = 2048;

    /**
     * Visual half-thickness of a round, in world units. Collision treats a round as a point —
     * it is the hitboxes that have size — so this only affects what the tracer looks like.
     */
    public static final float PROJECTILE_RADIUS = 1.6f;

    // --- Stance (mechanics §4.3) --------------------------------------------------------------
    /** Horizontal speed above which a player counts as "moving" for spread and recoil. */
    public static final float MOVING_SPEED_THRESHOLD = 25f;

    /**
     * Deviation is drawn from a normal distribution whose sigma is the cone half-angle over this
     * divisor, then clamped to the cone. At 2.5 roughly 99% of rounds land inside the cone
     * without clamping, and about 79% inside its inner half.
     */
    public static final float SPREAD_SIGMA_DIVISOR = 2.5f;

    // --- Death and respawn --------------------------------------------------------------------
    public static final float RESPAWN_DELAY_SECONDS = 3.0f;

    private CombatConfig() {
    }
}
