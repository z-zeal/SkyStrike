package io.github.skystrike.shared.model;

import io.github.skystrike.shared.utility.UtilityEffect;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.vision.SmokeVolume;

/**
 * A persistent result of a thrown utility: smoke, toxic smoke, or one patch of molotov fire.
 *
 * <p>The authoritative server owns the lifetime and damage clock. This compact state is copied
 * into snapshots so the client can feed the exact same smoke circles to the visibility shader
 * that the server exposes to gameplay visibility queries. Damage is intentionally carried too:
 * it lets a spectator/debug client describe a zone without reimplementing the molotov spread
 * rule, while resolution still trusts the server's copy.
 */
public final class UtilityZone {

    public int id;
    public int ownerId;
    public int teamIndex;
    /** Ordinal of the {@link UtilityId} that made this zone. */
    public int utilityId;

    public float x;
    public float y;
    public float radius;
    /** Damage on one shared damage-over-time tick; zero for ordinary smoke. */
    public float damage;
    /** Authoritative seconds until this zone vanishes. */
    public float remainingSeconds;

    public UtilityZone() {
    }

    public UtilityZone(
        int id,
        int ownerId,
        int teamIndex,
        int utilityId,
        float x,
        float y,
        float radius,
        float damage,
        float durationSeconds
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.teamIndex = teamIndex;
        this.utilityId = utilityId;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.damage = damage;
        this.remainingSeconds = durationSeconds;
    }

    public UtilityZone(UtilityZone other) {
        set(other);
    }

    public void set(UtilityZone other) {
        this.id = other.id;
        this.ownerId = other.ownerId;
        this.teamIndex = other.teamIndex;
        this.utilityId = other.utilityId;
        this.x = other.x;
        this.y = other.y;
        this.radius = other.radius;
        this.damage = other.damage;
        this.remainingSeconds = other.remainingSeconds;
    }

    public UtilityZone copy() {
        return new UtilityZone(this);
    }

    public UtilityId utility() {
        return UtilityId.fromOrdinal(utilityId);
    }

    public UtilityEffect effect() {
        return UtilityRegistry.of(utility()).effect();
    }

    /** Whether this zone must be supplied to both the shader and shared visibility math. */
    public boolean blocksVision() {
        return effect().blocksVision();
    }

    /**
     * The shader/query representation of an opaque cloud. Fire deliberately does not become a
     * smoke circle, even though it is a persistent zone.
     */
    public SmokeVolume smokeVolume() {
        return new SmokeVolume(x, y, radius, 1f);
    }

    @Override
    public String toString() {
        return "UtilityZone[id=" + id
            + ", owner=" + ownerId
            + ", utility=" + utility()
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", radius=" + String.format("%.1f", radius)
            + ", damage=" + String.format("%.1f", damage)
            + ", remaining=" + String.format("%.2f", remainingSeconds) + "]";
    }
}
