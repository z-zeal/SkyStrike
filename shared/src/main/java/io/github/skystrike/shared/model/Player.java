package io.github.skystrike.shared.model;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;

/**
 * Full state record for a networked player character.
 *
 * <p>Shared between server and client. The position {@code (x, y)} defines the bottom-centre of
 * the player hitbox so the feet remain flush with the ground during crouch transitions.
 */
public final class Player {

    public int id;
    public String name = "";
    public int teamIndex;

    public float x;
    public float y;
    public float vx;
    public float vy;

    public float rotation;
    public float angularVelocity;
    public float aimAngle;

    public float fuel = PlayerConfig.MAX_FUEL;
    public float health = PlayerConfig.MAX_HEALTH;

    public boolean crouched;
    public boolean grounded = true;
    public boolean jetpacking;
    public boolean ads;

    public float coyoteTimer;
    public long lastProcessedInputSequence;

    public Player() {
    }

    public Player(int id, String name, int teamIndex, float x, float y) {
        this.id = id;
        this.name = name;
        this.teamIndex = teamIndex;
        this.x = x;
        this.y = y;
    }

    public Player(Player other) {
        set(other);
    }

    public void set(Player other) {
        this.id = other.id;
        this.name = other.name;
        this.teamIndex = other.teamIndex;
        this.x = other.x;
        this.y = other.y;
        this.vx = other.vx;
        this.vy = other.vy;
        this.rotation = other.rotation;
        this.angularVelocity = other.angularVelocity;
        this.aimAngle = other.aimAngle;
        this.fuel = other.fuel;
        this.health = other.health;
        this.crouched = other.crouched;
        this.grounded = other.grounded;
        this.jetpacking = other.jetpacking;
        this.ads = other.ads;
        this.coyoteTimer = other.coyoteTimer;
        this.lastProcessedInputSequence = other.lastProcessedInputSequence;
    }

    public Player copy() {
        return new Player(this);
    }

    public float currentHeight() {
        return crouched ? PlayerConfig.CROUCH_HEIGHT : PlayerConfig.STAND_HEIGHT;
    }

    public Rect hitbox() {
        return new Rect(x - PlayerConfig.WIDTH / 2f, y, PlayerConfig.WIDTH, currentHeight());
    }

    public float centerX() {
        return x;
    }

    public float centerY() {
        return y + currentHeight() / 2f;
    }

    public float eyeX() {
        return x;
    }

    public float eyeY() {
        return y + currentHeight() * PlayerConfig.EYE_HEIGHT_FRACTION;
    }

    public boolean isFacingRight() {
        return Math.cos(Angles.toRadians(aimAngle)) >= 0;
    }

    @Override
    public String toString() {
        return "Player[id=" + id
            + ", name=" + name
            + ", team=" + teamIndex
            + ", pos=(" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")"
            + ", vel=(" + String.format("%.1f", vx) + ", " + String.format("%.1f", vy) + ")"
            + ", rot=" + String.format("%.1f", rotation)
            + ", aim=" + String.format("%.1f", aimAngle)
            + ", fuel=" + String.format("%.1f", fuel)
            + ", health=" + String.format("%.1f", health)
            + ", grounded=" + grounded
            + ", crouched=" + crouched + "]";
    }
}
