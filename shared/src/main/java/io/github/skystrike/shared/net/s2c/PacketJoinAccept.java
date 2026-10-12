package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;
import java.util.ArrayList;
import java.util.List;

/** Successful join. Tells the client who it is and what rate the authority runs at. */
public final class PacketJoinAccept implements Packet {

    /** Server-assigned, stable for the lifetime of the connection. */
    public int playerId;

    /** Name the server actually accepted, which may differ from the requested one. */
    public String playerName;

    /** Authoritative simulation rate, so the client can size its prediction buffers. */
    public int tickRateHz;

    /** Tick the server was on when it accepted. */
    public long serverTick;

    /**
     * The authored map's hash in hex — the map the destruction records below are indexed
     * against. A client whose own map hashes differently must not apply them: authored indices
     * only name blocks when both sides parsed the same list.
     */
    public String mapHashHex = "";

    /** How many blocks have been destroyed this round. Zero on a fresh map. */
    public int mapRevision;

    /**
     * The round's break history so far, in destruction order — the same records the live path
     * would have carried, so a late joiner lands on the current removed set without replaying
     * the match. Empty on a fresh map.
     */
    public List<PacketBlockBreak> destroyedBlocks = new ArrayList<>();

    /**
     * The crack state at the moment of the accept (protocol 17): one record per block that is
     * chipped right now, carrying the authority's accumulated damage for it — the totals, not the
     * hit history, because a joiner needs the picture rather than the strikes that drew it. Same
     * packet as the live path, so both paths take one route into the mirror; like
     * {@link #destroyedBlocks} it is applied only after the map-hash check.
     */
    public List<PacketBlockDamage> damagedBlocks = new ArrayList<>();

    /**
     * Every door's state at the moment of the accept, so a late joiner lands on the current
     * doors without waiting for one to move — the door counterpart of {@link #destroyedBlocks},
     * and like it a plain replay of the records the live path would have carried. A fresh map
     * ships the all-closed authored states rather than an empty list, so both join paths —
     * fresh round and mid-round — leave the mirror in a fully-stated shape with no special
     * "untouched means closed" rule on the receiving side.
     */
    public List<PacketDoorState> doors = new ArrayList<>();

    public PacketJoinAccept() {
    }

    public PacketJoinAccept(int playerId, String playerName, int tickRateHz, long serverTick) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.tickRateHz = tickRateHz;
        this.serverTick = serverTick;
    }

    @Override
    public String toString() {
        return "PacketJoinAccept[playerId=" + playerId
            + ", playerName=" + playerName
            + ", tickRateHz=" + tickRateHz
            + ", serverTick=" + serverTick
            + ", mapHashHex=" + mapHashHex
            + ", mapRevision=" + mapRevision
            + ", destroyedBlocks=" + destroyedBlocks.size()
            + ", damagedBlocks=" + damagedBlocks.size()
            + ", doors=" + doors.size() + "]";
    }
}
