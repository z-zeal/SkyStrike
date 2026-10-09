package io.github.skystrike.shared.model;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.Rect;

/**
 * One thrown observation camera in the world (mechanics §7.2), carried in every snapshot.
 *
 * <p>From the moment it is thrown the entity exists: while {@code stuck} is false it flies the arc
 * (its velocity is live and the shared {@code ThrowablePhysics} integrator is what moves it), and
 * the first surface it touches parks it permanently — {@code contactNormalX/Y} records that surface
 * so the renderer can draw the camera mounted on it. A stuck camera is a fixed observation post:
 * it cannot move, it projects a vision cone along {@code aimAngle}, and it lives until it is shot
 * down or otherwise destroyed.
 *
 * <p>Kinematics, ownership and health only; the tuning (throw speed, gravity, zoom, vision cone)
 * lives in {@link GadgetConfig}.
 */
public final class CameraEntity {

    public int id;
    public int ownerId;
    public int teamIndex;

    /** Centre position in world units. */
    public float x;
    public float y;

    /** Position at the start of the current tick, for client interpolation of the flight. */
    public float prevX;
    public float prevY;

    /** Velocity in units per second; zero once the camera has stuck. */
    public float vx;
    public float vy;

    /** True once the camera has stuck to a surface and become a fixed observation post. */
    public boolean stuck;

    /** Surface normal of the surface the camera stuck to; zero while flying. */
    public float contactNormalX;
    public float contactNormalY;

    /** Direction the camera's vision cone faces while viewed, in degrees. */
    public float aimAngle;

    /** Remaining hit points; the matching gadget slot's durability mirrors this. */
    public float health;

    public CameraEntity() {
    }

    public CameraEntity(
            int id,
            int ownerId,
            int teamIndex,
            float x,
            float y,
            float vx,
            float vy,
            float aimAngle,
            float health) {
        this.id = id;
        this.ownerId = ownerId;
        this.teamIndex = teamIndex;
        this.x = x;
        this.y = y;
        this.prevX = x;
        this.prevY = y;
        this.vx = vx;
        this.vy = vy;
        this.aimAngle = aimAngle;
        this.health = health;
    }

    public CameraEntity(CameraEntity other) {
        set(other);
    }

    public void set(CameraEntity other) {
        this.id = other.id;
        this.ownerId = other.ownerId;
        this.teamIndex = other.teamIndex;
        this.x = other.x;
        this.y = other.y;
        this.prevX = other.prevX;
        this.prevY = other.prevY;
        this.vx = other.vx;
        this.vy = other.vy;
        this.stuck = other.stuck;
        this.contactNormalX = other.contactNormalX;
        this.contactNormalY = other.contactNormalY;
        this.aimAngle = other.aimAngle;
        this.health = other.health;
    }

    public CameraEntity copy() {
        return new CameraEntity(this);
    }

    /** The camera's collision box, centred on its position. */
    public Rect hitbox() {
        float r = GadgetConfig.CAMERA_RADIUS;
        return new Rect(x - r, y - r, r * 2f, r * 2f);
    }

    /** The gadget this entity is the live form of. */
    public GadgetId gadgetId() {
        return GadgetId.CAMERA;
    }

    /** Applies one damage instance; health never goes below zero. */
    public void applyDamage(float amount) {
        if (amount > 0f) {
            health = Math.max(0f, health - amount);
        }
    }

    /** True once the camera's health pool is spent — the owning system destroys it. */
    public boolean isDestroyed() {
        return health <= 0f;
    }

    @Override
    public String toString() {
        return "CameraEntity[id=" + id
            + ", owner=" + ownerId
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", vel=(" + String.format("%.1f", vx) + ", " + String.format("%.1f", vy) + ")"
            + ", aim=" + String.format("%.1f", aimAngle)
            + ", hp=" + String.format("%.1f", health)
            + (stuck ? ", stuck" : ", flying") + "]";
    }
}
