package io.github.skystrike.shared.model;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.Rect;

/**
 * One deployed surveillance drone in the world (mechanics §7.1), carried in every snapshot.
 *
 * <p>Kinematics, ownership and health only — the tuning (speed, vision cone, spawn offset) lives in
 * {@link GadgetConfig} and is looked up by both sides, exactly as {@link ThrownUtility} carries no
 * damage numbers. The server owns the entity; clients render it, add its cone to the visibility
 * pass and (for its owner) steer it through the input packet while piloting.
 *
 * <p>{@code prevX}/{@code prevY} is the position at the start of the current tick, so the client
 * can interpolate the flight. {@code aimAngle} is the direction the drone's vision cone faces: the
 * owner's aim while piloting, and the last aim afterwards — a hovering drone keeps revealing the
 * lane it was left watching.
 */
public final class DroneEntity {

    public int id;
    public int ownerId;
    public int teamIndex;

    /** Centre position in world units. */
    public float x;
    public float y;

    /** Position at the start of the current tick, for client interpolation. */
    public float prevX;
    public float prevY;

    /** Velocity in units per second; damped to zero when nobody is piloting. */
    public float vx;
    public float vy;

    /** Direction the drone's vision cone faces, in degrees. */
    public float aimAngle;

    /** Remaining hit points; the matching gadget slot's durability mirrors this. */
    public float health;

    public DroneEntity() {
    }

    public DroneEntity(
            int id,
            int ownerId,
            int teamIndex,
            float x,
            float y,
            float aimAngle,
            float health) {
        this.id = id;
        this.ownerId = ownerId;
        this.teamIndex = teamIndex;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.aimAngle = aimAngle;
        this.health = health;
    }

    public DroneEntity(DroneEntity other) {
        set(other);
    }

    public void set(DroneEntity other) {
        this.id = other.id;
        this.ownerId = other.ownerId;
        this.teamIndex = other.teamIndex;
        this.x = other.x;
        this.y = other.y;
        this.prevX = other.prevX;
        this.prevY = other.prevY;
        this.vx = other.vx;
        this.vy = other.vy;
        this.aimAngle = other.aimAngle;
        this.health = other.health;
    }

    public DroneEntity copy() {
        return new DroneEntity(this);
    }

    /** The drone's collision box, centred on its position. */
    public Rect hitbox() {
        float r = GadgetConfig.DRONE_RADIUS;
        return new Rect(x - r, y - r, r * 2f, r * 2f);
    }

    /** The gadget this entity is the live form of. */
    public GadgetId gadgetId() {
        return GadgetId.DRONE;
    }

    /** Applies one damage instance; health never goes below zero. */
    public void applyDamage(float amount) {
        if (amount > 0f) {
            health = Math.max(0f, health - amount);
        }
    }

    /** True once the drone's health pool is spent — the owning system destroys it. */
    public boolean isDestroyed() {
        return health <= 0f;
    }

    @Override
    public String toString() {
        return "DroneEntity[id=" + id
            + ", owner=" + ownerId
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", vel=(" + String.format("%.1f", vx) + ", " + String.format("%.1f", vy) + ")"
            + ", aim=" + String.format("%.1f", aimAngle)
            + ", hp=" + String.format("%.1f", health) + "]";
    }
}
