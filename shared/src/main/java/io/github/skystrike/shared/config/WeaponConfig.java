package io.github.skystrike.shared.config;

/**
 * Cross-weapon constants: burst length and spacing, pellet counts, recoil caps and spread
 * thresholds (mechanics §4.3–§4.5).
 *
 * <p>Per-weapon numbers live with the weapon definitions, not here. The {@code *_MIN}/{@code *_MAX}
 * pairs are the ranges the mechanics plan quotes for those per-weapon values; the weapon table is
 * asserted against them in tests, which is how a typo in a single weapon row gets caught.
 */
public final class WeaponConfig {

    // --- Fire modes (mechanics §4.5) ----------------------------------------------------------
    /** Rounds in one burst-weapon trigger pull. */
    public static final int BURST_ROUNDS = 3;

    /** Fixed angular spacing between the rounds of a burst, so the group reads as a pattern. */
    public static final float BURST_SPACING_DEGREES = 0.55f;

    /** Random offset per burst round, as a fraction of the current spread. */
    public static final float BURST_JITTER_FRACTION = 0.20f;

    /** Random offset per pellet while hip firing. Pellet counts are per-weapon. */
    public static final float PELLET_JITTER_HIP_DEGREES = 1.5f;

    /** Random offset per pellet while aiming. */
    public static final float PELLET_JITTER_ADS_DEGREES = 0.8f;

    /** Upper bound on the rounds one trigger event can produce (8 pellets is the worst case). */
    public static final int MAX_SHOTS_PER_VOLLEY = 8;

    // --- Melee (mechanics §5.2) ----------------------------------------------------------------
    /**
     * Half-angle of the melee arc, measured from the attacker's aim: a swing reaches
     * ±60°, or a 120° wedge in front of the player. The per-weapon numbers (damage, cadence,
     * range, knockback) live in {@code MeleeRegistry}.
     */
    public static final float MELEE_ARC_HALF_ANGLE_DEGREES = 60f;

    // --- Spread (mechanics §4.3) --------------------------------------------------------------
    /** Moving while aiming is less punishing than moving while hip firing. */
    public static final float MOVING_ADS_SPREAD_FACTOR = 0.6f;

    /** Spread recovers 1.5× faster while aiming. */
    public static final float ADS_RECOVERY_MULTIPLIER = 1.5f;

    /** Per-weapon spread kick bounds, in degrees. */
    public static final float SPREAD_KICK_MIN_DEGREES = 2.1f;
    public static final float SPREAD_KICK_MAX_DEGREES = 4.8f;

    /** Per-weapon spread ceiling bounds, as a multiple of base spread. */
    public static final float SPREAD_CEILING_MIN_MULTIPLIER = 2.7f;
    public static final float SPREAD_CEILING_MAX_MULTIPLIER = 4.0f;

    /** Per-weapon spread recovery bounds, in degrees per second. */
    public static final float SPREAD_RECOVERY_MIN = 4.0f;
    public static final float SPREAD_RECOVERY_MAX = 9.5f;

    /** Per-weapon moving spread multiplier bounds. */
    public static final float MOVING_SPREAD_MIN_MULTIPLIER = 1.8f;
    public static final float MOVING_SPREAD_MAX_MULTIPLIER = 2.8f;

    // --- Recoil (mechanics §4.4) --------------------------------------------------------------
    /** Per-weapon ADS recoil multiplier bounds. Snipers benefit most from scoping. */
    public static final float ADS_RECOIL_MIN_MULTIPLIER = 0.45f;
    public static final float ADS_RECOIL_MAX_MULTIPLIER = 0.78f;

    /** Per-weapon moving recoil multiplier bounds. */
    public static final float MOVING_RECOIL_MIN_MULTIPLIER = 1.4f;
    public static final float MOVING_RECOIL_MAX_MULTIPLIER = 2.0f;

    /** Airborne players are not thrown uncontrollably by their own gun. */
    public static final float AIRBORNE_RECOIL_MULTIPLIER = 0.25f;

    /** A burst applies 1.20× recoil once, not once per round. */
    public static final float BURST_RECOIL_MULTIPLIER = 1.20f;

    /** How fast the recoil multiplier eases toward its ADS value. Never snapped. */
    public static final float ADS_RECOIL_BLEND_RATE = 10f;

    /** Visual gun-angle kick ceiling, in degrees. */
    public static final float VISUAL_KICK_MAX_DEGREES = 35f;

    /** Visual gun-angle kick decay, in degrees per second. */
    public static final float VISUAL_KICK_DECAY_DEGREES_PER_SECOND = 120f;

    // --- Ballistics bounds (mechanics §4.2, §5.1) ---------------------------------------------
    public static final float MUZZLE_SPEED_MIN = 520f;
    public static final float MUZZLE_SPEED_MAX = 1950f;

    /** Per-shot drag coefficient bounds, quoted per simulation tick. */
    public static final float DRAG_MIN = 0.980f;
    public static final float DRAG_MAX = 0.998f;

    /** Gravity ramp-in bounds, in seconds. */
    public static final float GRAVITY_RAMP_MIN_SECONDS = 0.5f;
    public static final float GRAVITY_RAMP_MAX_SECONDS = 1.8f;

    /** Damage falloff floor bounds, as a ratio of muzzle damage. */
    public static final float DAMAGE_FLOOR_MIN_RATIO = 0.35f;
    public static final float DAMAGE_FLOOR_MAX_RATIO = 0.85f;

    private WeaponConfig() {
    }
}
