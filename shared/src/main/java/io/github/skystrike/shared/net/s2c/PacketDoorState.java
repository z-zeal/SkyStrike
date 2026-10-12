package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.map.DoorState;
import io.github.skystrike.shared.net.Packet;

/**
 * The authoritative state change of one door (map plan D3, §8).
 *
 * <p>Sent reliably on the tick the machine moves a door, and replayed in the join payload for a
 * client that arrives mid-round — the same record both ways, the pattern {@link PacketBlockBreak}
 * sets for the destruction history. Reliable by design for the same reason: a lost door record
 * would leave a doorway passable on one client and walled on another, and doors change at human
 * pace, so the reliable channel costs nothing.
 *
 * <p>The record answers the wire's two questions with two ticks. {@link #tick} is the server's
 * dispatch tick: it orders the two wire paths (a stale baseline replayed at join can never undo
 * a live record that already landed, see {@code MapDoorState#applyRemote}). {@link #sinceTick}
 * is the tick, again on the server's clock, at which the door entered this state; the recipient
 * translates it onto its own clock estimate so the swing is drawn where the authority actually
 * started it. No wall-clock time ever crosses the wire for doors — only ticks both sides
 * reconcile against the same snapshots.
 *
 * <p>The door's identity is its authored id ({@code DoorSpec.id}), never an index into the live
 * map — an open door has no live-map presence at all, and the authored id is the one stable
 * identity the wire can state in one integer (plan D3's ids).
 */
public final class PacketDoorState implements Packet {

    /** The server's simulation tick at dispatch: the record's ordering on the wire. */
    public long tick;

    /** The authored door id ({@code DoorSpec.id}), the door's stable identity on the wire. */
    public int doorId;

    /**
     * The door state as its {@link DoorState} ordinal. Carried as a plain int like
     * {@code CommandResult}'s severity, so a mismatched receiver degrades by
     * {@link DoorState#fromOrdinal} rather than by a registration error; the enum's wire order
     * is pinned in {@code DoorStateTest}.
     */
    public int stateOrdinal;

    /** The tick, on the server's clock, at which the door entered this state. */
    public long sinceTick;

    public PacketDoorState() {
    }

    /** @param state the new machine state; carried as its ordinal, see {@link #stateOrdinal} */
    public PacketDoorState(long tick, int doorId, DoorState state, long sinceTick) {
        this.tick = tick;
        this.doorId = doorId;
        this.stateOrdinal = state.ordinal();
        this.sinceTick = sinceTick;
    }

    /** The decoded state; unknown ordinals resolve to {@link DoorState#CLOSED} defensively. */
    public DoorState state() {
        return DoorState.fromOrdinal(stateOrdinal);
    }

    @Override
    public String toString() {
        return "PacketDoorState[door=" + doorId + ", state=" + state()
            + ", tick=" + tick + ", since=" + sinceTick + "]";
    }
}
