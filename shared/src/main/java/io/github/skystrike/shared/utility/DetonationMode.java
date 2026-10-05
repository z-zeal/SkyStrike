package io.github.skystrike.shared.utility;

/** What makes a throwable go off (mechanics §6). */
public enum DetonationMode {

    /** A timer started when it left the hand. Bouncing does not shorten or extend it. */
    FUSE,

    /** The first surface or body it touches. There is no timer to outrun. */
    CONTACT,

    /** Placed, armed after a delay, then triggered by an enemy entering its trigger radius. */
    PROXIMITY;

    /** True when this utility is placed rather than thrown. */
    public boolean isPlaced() {
        return this == PROXIMITY;
    }
}
