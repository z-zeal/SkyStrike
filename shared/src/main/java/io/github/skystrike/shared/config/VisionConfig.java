package io.github.skystrike.shared.config;

/**
 * Cone reach and angle, falloff shape, peripheral floor, ambient floor and smoke parameters (mechanics §3, effects §4–§6).
 *
 * <p>Shared between the shader pipeline and {@link io.github.skystrike.shared.vision.VisionMath}.
 * The CPU and GPU implementations must evaluate the identical mathematical rules so players are
 * never shot by entities the renderer swore were invisible.
 */
public final class VisionConfig {

    // --- Observer reach (mechanics §3, effects §6.2) -------------------------------------------
    /** Maximum vision reach in units while hip-firing (640 units). */
    public static final float REACH_HIP = 640f;

    /** Maximum vision reach in units while aiming down sights (1024 units). */
    public static final float REACH_ADS = 1024f;

    /** Rate of exponential lerp interpolation between hip and ADS vision reach (per second). */
    public static final float ADS_TRANSITION_RATE = 10f;

    // --- Vision cone angles & feathering (mechanics §3, effects §6.2) ---------------------------
    /** Full vision cone angle in degrees for a player (120°). */
    public static final float CONE_ANGLE_DEGREES = 120f;

    /** Half-angle of the player vision cone in degrees (60°). */
    public static final float CONE_HALF_ANGLE_DEGREES = CONE_ANGLE_DEGREES / 2f;

    /** Angular feather width across the cone edge in degrees (15°). */
    public static final float FEATHER_ANGLE_DEGREES = 15f;

    // --- Falloff and floor parameters (effects §6.2, mechanics §3) ------------------------------
    /** Quadratic distance falloff exponent (2.0 = continuous ease-out quadratic falloff). */
    public static final float DISTANCE_FALLOFF_EXPONENT = 2.0f;

    /**
     * Dim peripheral floor outside the cone (0.06). Keeps the player's immediate surroundings
     * faintly readable.
     */
    public static final float PERIPHERAL_FLOOR = 0.06f;

    /**
     * Global ambient floor used in the fog composite (0.04). Unlit terrain receives this minimum
     * ambient illumination.
     */
    public static final float AMBIENT_FLOOR = 0.04f;

    /** Visibility threshold below which an entity is considered completely hidden / culled. */
    public static final float VISIBILITY_THRESHOLD = 0.01f;

    // --- SDF (Signed Distance Field) grid parameters (effects §4.2) -----------------------------
    /** Arena width in world units matching {@link WorldConfig#ARENA_WIDTH}. */
    public static final float SDF_WORLD_WIDTH = 3000f;

    /** Arena height in world units matching {@link WorldConfig#ARENA_HEIGHT}. */
    public static final float SDF_WORLD_HEIGHT = 2000f;

    /** World units per distance field texel (2.0 units/texel resolves the thinnest 14u platform). */
    public static final float SDF_TEXEL_SCALE = 2.0f;

    /** Distance field texture width (1500 texels). */
    public static final int SDF_TEXTURE_WIDTH = (int) (SDF_WORLD_WIDTH / SDF_TEXEL_SCALE);

    /** Distance field texture height (1000 texels). */
    public static final int SDF_TEXTURE_HEIGHT = (int) (SDF_WORLD_HEIGHT / SDF_TEXEL_SCALE);

    /** Maximum distance clamp value encoded in the 8-bit distance field (±127 world units). */
    public static final float SDF_MAX_DISTANCE = 127f;

    // --- SDF Raymarching & Soft Shadowing (effects §4.4) ----------------------------------------
    /** Penumbra softness constant k for SDF soft shadows (16.0). */
    public static final float SHADOW_SOFTNESS_K = 16.0f;

    /** Maximum raymarching step budget on desktop tier (48 steps). */
    public static final int MARCH_STEPS_DESKTOP = 48;

    /** Maximum raymarching step budget on mid-tier mobile (24 steps). */
    public static final int MARCH_STEPS_MID = 24;

    /** Maximum raymarching step budget on low-tier mobile (12 steps). */
    public static final int MARCH_STEPS_LOW = 12;

    // --- Smoke volumes (effects §6.2, mechanics §3) ----------------------------------------------
    /** Maximum number of concurrent smoke volumes uploaded to the visibility shader uniform array. */
    public static final int MAX_SMOKE_VOLUMES = 8;

    /** Default radius for a smoke volume in world units. */
    public static final float DEFAULT_SMOKE_RADIUS = 250f;

    /** Default opacity / density for a smoke grenade cloud. */
    public static final float DEFAULT_SMOKE_DENSITY = 1.0f;

    private VisionConfig() {
    }
}
