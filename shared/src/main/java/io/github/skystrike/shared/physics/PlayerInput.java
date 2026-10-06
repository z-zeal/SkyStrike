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
    public int slotPress = PacketPlayerInput.NO_SLOT_PRESS; // loadout slot 1-5, -1 = no press
    public int gadgetPress = PacketPlayerInput.NO_GADGET_PRESS; // Q/E edge, 0 = no press

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
            PacketPlayerInput.NO_SLOT_PRESS);
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
        int slotPress
    ) {
        this(sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, slotPress,
            PacketPlayerInput.NO_GADGET_PRESS);
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
        int slotPress,
        int gadgetPress
    ) {
        this.sequence = sequence;
        this.moveX = moveX;
        this.jump = jump;
        this.crouch = crouch;
        this.jetpack = jetpack;
        this.ads = ads;
        this.fire = fire;
        this.aimAngle = aimAngle;
        this.slotPress = slotPress;
        this.gadgetPress = gadgetPress;
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
            packet.slotPress,
            packet.gadgetPress
        );
    }

    public PlayerInput copy() {
        return new PlayerInput(
            sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, slotPress, gadgetPress);
    }
}
