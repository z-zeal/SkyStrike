package io.github.skystrike.shared.config;

/**
 * Arena bounds and simulation rate — the frame of reference every other system is expressed in.
 *
 * <p>Gravity, spawn placement rules and the rest of the world tuning land here in Phase 1.
 */
public final class WorldConfig {

    /** Arena width in world units (mechanics §1). */
    public static final float ARENA_WIDTH = 3000f;

    /** Arena height in world units (mechanics §1). */
    public static final float ARENA_HEIGHT = 2000f;

    /** Top surface of the solid ground plane. Everything standable rests on or above this. */
    public static final float GROUND_HEIGHT = 100f;

    /** The arena is mirror-symmetric about this vertical line. */
    public static final float MIRROR_AXIS_X = ARENA_WIDTH / 2f;

    /** Authoritative simulation rate. The server ticks at exactly this frequency. */
    public static final int TICK_RATE_HZ = 60;

    /** Fixed simulation step in seconds. */
    public static final float TICK_SECONDS = 1f / TICK_RATE_HZ;

    private WorldConfig() {
    }
}
