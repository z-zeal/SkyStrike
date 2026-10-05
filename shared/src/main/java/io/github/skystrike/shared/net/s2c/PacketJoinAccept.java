package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

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
            + ", serverTick=" + serverTick + "]";
    }
}
