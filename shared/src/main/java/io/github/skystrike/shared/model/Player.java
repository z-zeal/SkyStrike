package io.github.skystrike.shared.model;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

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

    // --- Combat state (Phase 3) ---------------------------------------------------------------

    /** False between death and respawn: no input, no fire, not a valid target. */
    public boolean alive = true;

    /** Seconds left before respawn while dead. */
    public float respawnTimer;

    /**
     * What is in the hands, as a wire id: a {@link io.github.skystrike.shared.weapons.WeaponId}
     * ordinal while a gun is held, a {@link io.github.skystrike.shared.weapons.MeleeId} wire id
     * (1000 + ordinal) while melee is held. Derived from {@link #loadout} on the server.
     */
    public int weaponId;

    /**
     * The full loadout (slots, ammunition, reload). Server-authoritative; mirrored to the owner
     * in every snapshot, and never null.
     */
    public PlayerLoadout loadout = new PlayerLoadout();

    /** Live spread cone in degrees, for the crosshair and the debug readout. */
    public float spread;

    /** Visual gun-angle kick in degrees, decaying at 120 deg/s. Rendering only. */
    public float gunKick;

    public int kills;
    public int deaths;

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
        this.alive = other.alive;
        this.respawnTimer = other.respawnTimer;
        this.weaponId = other.weaponId;
        this.loadout.set(other.loadout);
        this.spread = other.spread;
        this.gunKick = other.gunKick;
        this.kills = other.kills;
        this.deaths = other.deaths;
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

    /**
     * The gun the {@link #weaponId} wire id names. Falls back to the default gun when the player
     * is holding melee — check {@link #holdingMelee()} or use {@link #heldWeaponDisplayName()}
     * when the distinction matters.
     */
    public WeaponId weapon() {
        return WeaponId.fromOrdinal(weaponId);
    }

    /** True while the melee slot is the active one. */
    public boolean holdingMelee() {
        return loadout != null && loadout.meleeActive();
    }

    /** Display name of whatever is in the hands, gun or melee. */
    public String heldWeaponDisplayName() {
        return WeaponRegistry.displayNameForWireId(weaponId);
    }

    /** Aim angle plus the visual recoil kick — what the gun is drawn at, not what it hits. */
    public float renderedGunAngle() {
        return Angles.wrap(aimAngle + gunKick);
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
            + ", crouched=" + crouched
            + ", alive=" + alive
            + ", weapon=" + weapon() + "]";
    }
}
