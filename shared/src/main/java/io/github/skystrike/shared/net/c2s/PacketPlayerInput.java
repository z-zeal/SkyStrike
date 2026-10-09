package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server input packet sent per input sample.
 *
 * <p>{@code fire} is the held trigger state. The server derives the press edge semi-automatic,
 * bolt and burst weapons need by comparing successive packets, so a tap that falls between two
 * ticks is still a shot.
 *
 * <p>{@code slotPress} is a loadout slot selection, as a loadout slot number (1–5). It is an
 * <i>edge</i>, not a level: the client sets it on the sample where the key was pressed (or the
 * wheel notched) and keeps setting it on later samples until the server acknowledges the sample
 * that carried it, so a dropped packet cannot silently eat a switch. The server latches it like
 * the trigger edge and applies the shared tap-swap rule exactly once per press.
 *
 * <p>{@code gadgetPress} uses {@link #GADGET_Q_PRESS} and {@link #GADGET_E_PRESS} for Q and E.
 * It follows the same birth-sequence retransmission contract, but is consumed even when the
 * player is dead or stunned so an old gadget toggle can never be banked for a later life or for
 * the end of a stun.
 *
 * <p>{@code viewAction} carries the view-cycle ({@link #VIEW_CYCLE}) and exit-surveillance
 * ({@link #VIEW_EXIT}) edges of mechanics §9. It follows the same birth-sequence contract, and
 * the server consumes it under every lock — Escape must always be able to leave a surveillance
 * view — so a piloting player can never be trapped by a dropped packet.
 */
public final class PacketPlayerInput implements Packet {

    /** Sentinel for "no slot press on this sample". */
    public static final int NO_SLOT_PRESS = -1;

    /** Sentinel for "neither Q nor E was pressed on this sample". */
    public static final int NO_GADGET_PRESS = 0;

    /** The Q and E edge values carried by {@link #gadgetPress}. */
    public static final int GADGET_Q_PRESS = 1;
    public static final int GADGET_E_PRESS = 2;

    /** Sentinel for "no view action on this sample". */
    public static final int NO_VIEW_ACTION = 0;

    /** The view-cycle edge value carried by {@link #viewAction} (key {@code 6}, mechanics §9). */
    public static final int VIEW_CYCLE = 1;

    /** The exit-surveillance edge value carried by {@link #viewAction} (Escape, mechanics §9). */
    public static final int VIEW_EXIT = 2;

    public long sequence;
    public float moveX;
    public boolean jump;
    public boolean crouch;
    public boolean jetpack;
    public boolean ads;
    public boolean fire;
    public float aimAngle;

    /**
     * Loadout slot selection pressed on this sample (1–5), or {@link #NO_SLOT_PRESS}. Repeated
     * on subsequent samples until acknowledged; the server applies it once.
     */
    public int slotPress = NO_SLOT_PRESS;

    /**
     * Identity of {@link #slotPress}: the sequence number of the first packet that carried this
     * press. The client repeats the press (with the same birth sequence) until it is
     * acknowledged, and the server applies each birth sequence exactly once — so retransmission
     * over lossy UDP can neither drop nor duplicate a switch. {@code -1} when no press rides.
     */
    public long slotPressSeq = -1L;

    /** Q or E gadget edge on this sample, or {@link #NO_GADGET_PRESS}. Appended after the slot
     * fields so the existing input wire shape remains prefix-compatible. */
    public int gadgetPress = NO_GADGET_PRESS;

    /** Birth sequence of {@link #gadgetPress}; {@code -1} when no edge rides this sample. */
    public long gadgetPressSeq = -1L;

    /**
     * View action edge on this sample: {@link #VIEW_CYCLE} or {@link #VIEW_EXIT}, or
     * {@link #NO_VIEW_ACTION}. Appended after the gadget fields so the input wire shape remains
     * prefix-compatible; consumed by the server even while the player is piloting, because the
     * view keys are the one input family that stays live under the surveillance lock.
     */
    public int viewAction = NO_VIEW_ACTION;

    /** Birth sequence of {@link #viewAction}; {@code -1} when no edge rides this sample. */
    public long viewActionSeq = -1L;

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
        this(sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, NO_SLOT_PRESS);
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
        int slotPress
    ) {
        this(sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, slotPress, NO_GADGET_PRESS);
    }

    /** Full constructor used by protocol tests and input replay tools. */
    public PacketPlayerInput(
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
            + ", slotPress=" + slotPress
            + ", gadgetPress=" + gadgetPress
            + ", viewAction=" + viewAction + "]";
    }
}
