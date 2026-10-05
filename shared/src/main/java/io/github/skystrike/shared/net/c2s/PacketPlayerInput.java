package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server input packet sent per input sample.
 *
 * <p>{@code fire} is the held trigger state. The server derives the press edge semi-automatic,
 * bolt and burst weapons need by comparing successive packets, so a tap that falls between two
 * ticks is still a shot.
 */
public final class PacketPlayerInput implements Packet {

    /** Sentinel for "keep the weapon I am holding". */
    public static final int NO_WEAPON_CHANGE = -1;

    public long sequence;
    public float moveX;
    public boolean jump;
    public boolean crouch;
    public boolean jetpack;
    public boolean ads;
    public boolean fire;
    public float aimAngle;

    /**
     * Requested weapon, as a {@link io.github.skystrike.shared.weapons.WeaponId} ordinal, or
     * {@code -1} for "no change". Phase 4 replaces this with full loadout slot selection.
     */
    public int weaponSelect = NO_WEAPON_CHANGE;

    public PacketPlayerInput() {
    }

    public PacketPlayerInput(
        long sequence,
        float moveX,
        boolean jump,
        boolean crouch,
        boolean jetpack,
        boolean ads,
        boolean fire,
        float aimAngle
    ) {
        this(sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, NO_WEAPON_CHANGE);
    }

    public PacketPlayerInput(
        long sequence,
        float moveX,
        boolean jump,
        boolean crouch,
        boolean jetpack,
        boolean ads,
        boolean fire,
        float aimAngle,
        int weaponSelect
    ) {
        this.sequence = sequence;
        this.moveX = moveX;
        this.jump = jump;
        this.crouch = crouch;
        this.jetpack = jetpack;
        this.ads = ads;
        this.fire = fire;
        this.aimAngle = aimAngle;
        this.weaponSelect = weaponSelect;
    }

    @Override
    public String toString() {
        return "PacketPlayerInput[seq=" + sequence
            + ", moveX=" + moveX
            + ", jump=" + jump
            + ", crouch=" + crouch
            + ", jetpack=" + jetpack
            + ", ads=" + ads
            + ", fire=" + fire
            + ", aim=" + aimAngle
            + ", weaponSelect=" + weaponSelect + "]";
    }
}
