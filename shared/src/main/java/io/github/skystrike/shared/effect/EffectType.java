package io.github.skystrike.shared.effect;

/**
 * Every visual effect the server can raise, as a wire enum (playable build plan M7 §8.1).
 *
 * <p>Effects are presentation only: they never change gameplay state, so the packet that carries
 * them travels unreliably and a dropped event costs a missing spark, never a wrong result. The
 * server culls them per recipient with {@code VisionMath} before batching them into the snapshot
 * broadcast; the client drains them on the render thread into an {@code FxEventQueue} and never
 * spawns anything from the network thread.
 *
 * <p><b>On the wire</b> the type is the enum ordinal. The encoding is part of the protocol:
 * <b>append only, never reorder, never reuse</b> — same rule as {@code UtilityId}.
 *
 * <p>Each constant also carries the radius the server's culling disc samples around the effect,
 * which is deliberately the radius of what an observer can actually see (the attached light's
 * reach) rather than the particle spread: a frag detonated just behind a wall must still reach
 * observers who can see the wall face its light lands on (M7 gate).
 */
public enum EffectType {

    /** A frag grenade's phased detonation: flash, rings, fireball, debris, smoke. */
    FRAG_EXPLOSION(330f),

    /** An impact grenade's detonation: the frag schedule, smaller. */
    IMPACT_EXPLOSION(250f),

    /** A smoke grenade's cloud, sized to grow into the zone's vision-blocking circle. */
    SMOKE_BURST(250f),

    /** A molotov's impact: glass sparks and a fire splash along the surface tangent. */
    MOLOTOV_SPLASH(170f),

    /** One patch of molotov fire: flame particles plus a flickering attached light. */
    FIRE_ZONE(60f),

    /** A poison smoke's cloud, smaller and greener than the smoke grenade's. */
    POISON_BURST(220f),

    /** A flashbang's (or stun grenade's) white burst; blindness itself is server state. */
    FLASH_DETONATION(300f),

    /** A claymore's directional blast, shaped by the aim angle it was placed with. */
    CLAYMORE_BLAST(220f),

    /** A round hitting concrete: dust cloud plus small chips. */
    BULLET_IMPACT_CONCRETE(24f),

    /** A round hitting metal: bright sparks plus molten droplets. No surface material yet. */
    BULLET_IMPACT_METAL(24f),

    /** A round hitting wood: brown splinters plus floating dust. No surface material yet. */
    BULLET_IMPACT_WOOD(24f),

    /** A gun's muzzle flash at the barrel: core disc, gas burst, one-frame light. */
    MUZZLE_FLASH(30f),

    /** A spent shell casing ejected from the weapon, simulated on the CPU tier. */
    SHELL_EJECT(24f);

    private final float cullRadius;

    EffectType(float cullRadius) {
        this.cullRadius = cullRadius;
    }

    /**
     * The radius of the disc the server samples for per-recipient visibility culling, in world
     * units. Roughly the attached light's reach; the client's per-particle occlusion test does the
     * fine-grained work after that.
     */
    public float cullRadius() {
        return cullRadius;
    }

    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }

    /** Decodes a wire ordinal; throws for a value from a newer protocol, like {@code UtilityId}. */
    public static EffectType fromOrdinal(int ordinal) {
        if (!isValidOrdinal(ordinal)) {
            throw new IllegalArgumentException("not an effect type ordinal: " + ordinal);
        }
        return values()[ordinal];
    }
}
