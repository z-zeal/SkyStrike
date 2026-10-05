package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server input packet sent per input sample.
 */
public final class PacketPlayerInput implements Packet {

    public long sequence;
    public float moveX;
    public boolean jump;
    public boolean crouch;
    public boolean jetpack;
    public boolean ads;
    public boolean fire;
    public float aimAngle;

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
        this.sequence = sequence;
        this.moveX = moveX;
        this.jump = jump;
        this.crouch = crouch;
        this.jetpack = jetpack;
        this.ads = ads;
        this.fire = fire;
        this.aimAngle = aimAngle;
    }

    @Override
    public String toString() {
        return "PacketPlayerInput[seq=" + sequence
            + ", moveX=" + moveX
            + ", jump=" + jump
            + ", crouch=" + crouch
            + ", jetpack=" + jetpack
            + ", ads=" + ads
            + ", aim=" + aimAngle + "]";
    }
}
