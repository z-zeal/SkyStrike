package io.github.skystrike.shared.model;

import io.github.skystrike.shared.utility.UtilityId;

/**
 * One throwable in flight, or settled and still counting down.
 *
 * <p>Kinematics and timers only. Damage, radius and fuse length are never carried here: the
 * server looks them up from the utility id when it goes off, exactly as {@link Projectile} does
 * for bullets, so a snapshot can be handed to a client without telling it how hard the grenade
 * hits.
 *
 * <p>{@code prevX}/{@code prevY} is the position at the start of the current tick, so the client
 * can interpolate the arc and the server can sweep it.
 */
public final class ThrownUtility {

    public int id;
    public int ownerId;
    public int teamIndex;

    /** Ordinal of the {@link UtilityId} that was thrown. */
    public int utilityId;

    public float x;
    public float y;
    public float prevX;
    public float prevY;
    public float vx;
    public float vy;

    /**
     * Direction the utility was placed or thrown. Flight derives its velocity from this on spawn;
     * a placed claymore keeps it so its eventual blast can remain directional.
     */
    public float aimAngle;

    /** Seconds since it left the hand. Drives the self-contact grace and the lifetime cap. */
    public float age;

    /**
     * Seconds left on a timed fuse. Meaningless for contact and proximity devices, which is why
     * the detonation mode is looked up rather than inferred from this reaching zero.
     */
    public float fuseRemaining;

    /** True once it has stopped moving and been parked against a surface. */
    public boolean resting;

    /** How many surfaces it has bounced off. Drives the impact sound and the settle heuristic. */
    public int bounces;

    /**
     * Surface normal of the most recent bounce, which is what a molotov spreads its fire along.
     * Zero until something has been hit.
     */
    public float contactNormalX;
    public float contactNormalY;

    public ThrownUtility() {
    }

    public ThrownUtility(
        int id, int ownerId, int teamIndex, int utilityId, float x, float y, float vx, float vy, float fuseSeconds) {
        this.id = id;
        this.ownerId = ownerId;
        this.teamIndex = teamIndex;
        this.utilityId = utilityId;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = vx;
        this.vy = vy;
        this.fuseRemaining = fuseSeconds;
    }

    public ThrownUtility(ThrownUtility other) {
        set(other);
    }

    public void set(ThrownUtility other) {
        this.id = other.id;
        this.ownerId = other.ownerId;
        this.teamIndex = other.teamIndex;
        this.utilityId = other.utilityId;
        this.x = other.x;
        this.y = other.y;
        this.prevX = other.prevX;
        this.prevY = other.prevY;
        this.vx = other.vx;
        this.vy = other.vy;
        this.aimAngle = other.aimAngle;
        this.age = other.age;
        this.fuseRemaining = other.fuseRemaining;
        this.resting = other.resting;
        this.bounces = other.bounces;
        this.contactNormalX = other.contactNormalX;
        this.contactNormalY = other.contactNormalY;
    }

    public ThrownUtility copy() {
        return new ThrownUtility(this);
    }

    public UtilityId utility() {
        return UtilityId.fromOrdinal(utilityId);
    }

    /** This throwable's encoding in the shared weapon-id space, for damage and kill events. */
    public int weaponWireId() {
        return UtilityId.WIRE_ID_BASE + utilityId;
    }

    public float speed() {
        return (float) Math.sqrt(vx * vx + vy * vy);
    }

    /** True when something has been hit and a contact normal is available. */
    public boolean hasContactNormal() {
        return contactNormalX != 0f || contactNormalY != 0f;
    }

    @Override
    public String toString() {
        return "ThrownUtility[id=" + id
            + ", owner=" + ownerId
            + ", utility=" + utility()
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", vel=(" + String.format("%.1f", vx) + ", " + String.format("%.1f", vy) + ")"
            + ", aim=" + String.format("%.1f", aimAngle)
            + ", fuse=" + String.format("%.2f", fuseRemaining)
            + ", bounces=" + bounces
            + (resting ? ", resting" : "") + "]";
    }
}
