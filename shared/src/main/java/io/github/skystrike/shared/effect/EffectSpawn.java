package io.github.skystrike.shared.effect;

/**
 * One effect request on the wire (playable build plan M7 §8.1): a type, a world position, a
 * direction, a scale and a deterministic seed.
 *
 * <p>Plain mutable data with a public no-argument constructor, exactly like every other packet
 * payload: the serialiser instantiates it reflectively and the server builds one per event. It is
 * deliberately small and unreliable-safe — losing one costs a missing spark, never a wrong game
 * state.
 *
 * <p>The meaning of {@code angle} and {@code scale} is per type, and both sides must agree:
 * <ul>
 *   <li>{@code angle} — degrees. The blast or cone direction (claymore aim), the surface tangent
 *       (molotov splash), the barrel direction (muzzle flash, shell eject, bullet impact travel),
 *       or 90 (up) for fire zones. Ignored by omnidirectional bursts.</li>
 *   <li>{@code scale} — a multiplier, 1 = the type's default size. For {@code SMOKE_BURST} and
 *       {@code POISON_BURST} it encodes the zone radius in units of
 *       {@code VisionConfig.DEFAULT_SMOKE_RADIUS}, so the client's cloud grows into exactly the
 *       vision-blocking circle the snapshot already carries.</li>
 * </ul>
 *
 * <p>{@code seed} is assigned by the server's broadcaster, not the emitter: every client that
 * receives the same event lays out the same particles.
 */
public final class EffectSpawn {

    public EffectType type = EffectType.FRAG_EXPLOSION;
    public float x;
    public float y;
    /** Direction in degrees; per-type meaning, see the class javadoc. */
    public float angle;
    /** Size multiplier, 1 = default; per-type meaning, see the class javadoc. */
    public float scale = 1f;
    /** Deterministic layout seed, assigned by the server's effect broadcaster. */
    public int seed;

    public EffectSpawn() {
    }

    public EffectSpawn(EffectType type, float x, float y, float angle, float scale) {
        this.type = type == null ? EffectType.FRAG_EXPLOSION : type;
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.scale = scale;
    }

    public EffectSpawn(EffectSpawn other) {
        set(other);
    }

    public void set(EffectSpawn other) {
        this.type = other.type;
        this.x = other.x;
        this.y = other.y;
        this.angle = other.angle;
        this.scale = other.scale;
        this.seed = other.seed;
    }

    public EffectSpawn copy() {
        return new EffectSpawn(this);
    }

    @Override
    public String toString() {
        return "EffectSpawn[type=" + type
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", angle=" + String.format("%.1f", angle)
            + ", scale=" + String.format("%.2f", scale)
            + ", seed=" + seed + "]";
    }
}
