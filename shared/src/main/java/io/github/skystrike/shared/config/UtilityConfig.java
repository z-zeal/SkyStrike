package io.github.skystrike.shared.config;

/**
 * Throwable tuning: flight, bouncing, occluded blasts, stun banding and the damage-over-time
 * clock (mechanics §6).
 *
 * <p>Per-utility numbers — throw force, fuse, radius, damage, cooldown — are not here. They are
 * per-item data and live with the definitions in {@code shared/utility/UtilityRegistry}, exactly
 * as per-weapon numbers live with the weapon definitions. What is here is the cross-utility
 * physics and resolution rules that every throwable obeys.
 *
 * <p>Values marked <b>provisional</b> are ones the mechanics plan leaves blank. They are flagged
 * so that a later balance pass knows which numbers were specified and which were chosen.
 */
public final class UtilityConfig {

    // --- Flight and bouncing (mechanics §6) ----------------------------------------------------

    /**
     * Throwable gravity, independent of and much gentler than bullet drop. A grenade is meant to
     * arc readably across a lane; a bullet is meant to look flat.
     */
    public static final float GRAVITY = -500f;

    /**
     * Restitution applied to the velocity component <b>normal</b> to the surface that was hit.
     *
     * <p>The plan quotes this as "0.35 vertical restitution", which is the floor case. Stated as
     * a normal-component coefficient it generalises to walls and ceilings without a second
     * number, and it is what makes throwables settle quickly instead of skating away.
     */
    public static final float BOUNCE_RESTITUTION = 0.35f;

    /**
     * Fraction of the <b>tangential</b> velocity retained through a bounce — the plan's "0.70
     * horizontal friction", generalised the same way.
     */
    public static final float BOUNCE_TANGENT_FRICTION = 0.70f;

    /** Collision half-extent of a throwable, in world units. Small, but never a point. */
    public static final float THROWABLE_RADIUS = 4f;

    /** Below this speed, a throwable resting against a surface is parked and stops simulating. */
    public static final float SETTLE_SPEED = 18f;

    /** Terminal speed for a throwable, so a long fall cannot tunnel the thinnest geometry. */
    public static final float MAX_SPEED = 1600f;

    /**
     * Longest distance a throwable may travel in one integration substep. The thinnest piece of
     * arena geometry is the 14-unit tunnel roof, so staying well under half of that means a
     * grenade can never step over a floor it should have bounced off.
     */
    public static final float MAX_SUBSTEP_TRAVEL = 6f;

    /** Hard cap on substeps per tick, so a pathological velocity cannot stall the tick thread. */
    public static final int MAX_SUBSTEPS = 16;

    /** Separation left between a bounced throwable and the surface, to avoid re-colliding. */
    public static final float CONTACT_SKIN = 0.01f;

    /** A thrown item leaves the hand this far from the eye, matching the drawn arm. */
    public static final float THROW_OFFSET = 20f;

    /** A throwable cannot collide with its thrower's hitbox for this long after leaving the hand. */
    public static final float SELF_CONTACT_GRACE_SECONDS = 0.10f;

    /** Hard lifetime cap, so a throwable wedged somewhere unreachable still goes away. */
    public static final float MAX_LIFETIME_SECONDS = 20f;

    /** Upper bound on simultaneously live throwables, so a spam loop cannot exhaust the heap. */
    public static final int MAX_ACTIVE_THROWABLES = 128;

    // --- Explosions (mechanics §6) -------------------------------------------------------------

    /**
     * Blast damage falls off <b>linearly</b> to zero at the radius edge, and terrain occlusion is
     * absolute rather than attenuating: no line of sight to the detonation means no damage at
     * all, however close you were. Hiding behind a crate has to actually work.
     */
    public static final float BLAST_MIN_DAMAGE_FRACTION = 0f;

    /** Fraction of the blast's impulse that survives at the very edge of the radius. */
    public static final float BLAST_EDGE_IMPULSE_FRACTION = 0.25f;

    // --- Stun grenade (mechanics §6.1) ---------------------------------------------------------

    /** Upper bound of the inner band, as a fraction of the stun radius. */
    public static final float STUN_INNER_BAND = 0.30f;

    /** Upper bound of the middle band, as a fraction of the stun radius. */
    public static final float STUN_MIDDLE_BAND = 0.70f;

    public static final float STUN_INNER_BLIND_SECONDS = 7.0f;
    public static final float STUN_INNER_SLOW_SECONDS = 4.0f;
    public static final float STUN_MIDDLE_BLIND_SECONDS = 4.5f;
    public static final float STUN_MIDDLE_SLOW_SECONDS = 2.5f;
    public static final float STUN_OUTER_BLIND_SECONDS = 2.0f;
    public static final float STUN_OUTER_SLOW_SECONDS = 1.0f;

    /**
     * In range but with no line of sight to the detonation: a 0.3 s concussion and nothing else.
     * Turning away or ducking behind cover genuinely saves you.
     */
    public static final float STUN_NO_SIGHT_CONCUSSION_SECONDS = 0.3f;

    /** Stunned players move at 30% speed. */
    public static final float STUN_MOVE_SPEED_MULTIPLIER = 0.30f;

    // --- Damage over time: fire and toxic clouds (mechanics §6.2) -------------------------------

    /**
     * Interval between damage-over-time applications. <b>Provisional</b> — the plan quotes fire
     * and poison damage "per tick" without defining the tick. At 0.5 s a molotov's 21 is 42
     * damage per second, which kills a full-health player in three and a half seconds of
     * standing in it: area denial that hurts rather than area denial that kills instantly.
     */
    public static final float DOT_TICK_SECONDS = 0.5f;

    /** Radius of one molotov fire zone, in world units. */
    public static final float FIRE_ZONE_RADIUS = 40f;

    /** Fire spread zones are cast along the hit surface at this spacing. */
    public static final float FIRE_SPREAD_OFFSET = 2.0f;

    /** Damage of a molotov's outward-cast spread zones, against 21 for the central one. */
    public static final float FIRE_SPREAD_DAMAGE = 17f;

    /** Number of spread zones cast either side of the central one along the surface tangent. */
    public static final int FIRE_SPREAD_ZONES_PER_SIDE = 3;

    // --- Carried counts (mechanics §6) ---------------------------------------------------------

    /**
     * Throwables carried per utility slot. <b>Provisional</b> — the plan says "a limited carried
     * count" without giving one.
     */
    public static final int DEFAULT_CARRIED_COUNT = 2;

    /** Utility slots a player has. */
    public static final int UTILITY_SLOTS = 2;

    private UtilityConfig() {
    }
}
