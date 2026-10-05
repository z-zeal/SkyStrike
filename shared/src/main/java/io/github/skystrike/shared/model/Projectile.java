package io.github.skystrike.shared.model;

import io.github.skystrike.shared.weapons.WeaponId;

/**
 * One round in flight.
 *
 * <p>Kinematics only. Damage is never carried here: the server resolves it from the weapon id
 * when the round lands, so a snapshot can be handed to a client without telling it how hard the
 * bullet hits.
 *
 * <p>{@code prevX}/{@code prevY} is the position at the start of the current tick. The server
 * sweeps that segment for collision and the client draws the tracer along it, which is why it is
 * state rather than a local variable.
 */
public final class Projectile {

    public int id;
    public int ownerId;
    public int teamIndex;

    /** Ordinal of the {@link WeaponId} that fired the round. */
    public int weaponId;

    public float x;
    public float y;
    public float prevX;
    public float prevY;
    public float vx;
    public float vy;

    /** Seconds since the round left the muzzle. Drives the gravity ramp. */
    public float age;

    /** Path length travelled, which is what damage falloff is measured against. */
    public float distanceTravelled;

    public Projectile() {
    }

    public Projectile(int id, int ownerId, int teamIndex, int weaponId, float x, float y, float vx, float vy) {
        this.id = id;
        this.ownerId = ownerId;
        this.teamIndex = teamIndex;
        this.weaponId = weaponId;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = vx;
        this.vy = vy;
    }

    public Projectile(Projectile other) {
        set(other);
    }

    public void set(Projectile other) {
        this.id = other.id;
        this.ownerId = other.ownerId;
        this.teamIndex = other.teamIndex;
        this.weaponId = other.weaponId;
        this.x = other.x;
        this.y = other.y;
        this.prevX = other.prevX;
        this.prevY = other.prevY;
        this.vx = other.vx;
        this.vy = other.vy;
        this.age = other.age;
        this.distanceTravelled = other.distanceTravelled;
    }

    public Projectile copy() {
        return new Projectile(this);
    }

    public WeaponId weapon() {
        return WeaponId.fromOrdinal(weaponId);
    }

    public float speed() {
        return (float) Math.sqrt(vx * vx + vy * vy);
    }

    @Override
    public String toString() {
        return "Projectile[id=" + id
            + ", owner=" + ownerId
            + ", weapon=" + weapon()
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", vel=(" + String.format("%.1f", vx) + ", " + String.format("%.1f", vy) + ")"
            + ", age=" + String.format("%.2f", age)
            + ", travelled=" + String.format("%.1f", distanceTravelled) + "]";
    }
}
