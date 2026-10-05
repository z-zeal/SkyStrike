package io.github.skystrike.shared.physics;

import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * Pure input snapshot used to step player simulation and prediction deterministically.
 */
public final class PlayerInput {

    public long sequence;
    public float moveX; // -1 (left), 0 (idle), 1 (right)
    public boolean jump;
    public boolean crouch;
    public boolean jetpack;
    public boolean ads;
    public boolean fire;
    public float aimAngle; // degrees
    public int weaponSelect = PacketPlayerInput.NO_WEAPON_CHANGE; // WeaponId ordinal, -1 = unchanged

    public PlayerInput() {
    }

    public PlayerInput(
        long sequence,
        float moveX,
        boolean jump,
        boolean crouch,
        boolean jetpack,
        boolean ads,
        boolean fire,
        float aimAngle
    ) {
        this(sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle,
            PacketPlayerInput.NO_WEAPON_CHANGE);
    }

    public PlayerInput(
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

    public static PlayerInput fromPacket(PacketPlayerInput packet) {
        if (packet == null) {
            return new PlayerInput();
        }
        return new PlayerInput(
            packet.sequence,
            packet.moveX,
            packet.jump,
            packet.crouch,
            packet.jetpack,
            packet.ads,
            packet.fire,
            packet.aimAngle,
            packet.weaponSelect
        );
    }

    public PlayerInput copy() {
        return new PlayerInput(
            sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, weaponSelect);
    }
}
