package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.net.Packet;

/** Reply to a ping, echoing the client's stamp and adding the server's tick. */
public final class PacketPong implements Packet {

    public long clientTimeMillis;
    public long serverTick;

    public PacketPong() {
    }

    public PacketPong(long clientTimeMillis, long serverTick) {
        this.clientTimeMillis = clientTimeMillis;
        this.serverTick = serverTick;
    }

    @Override
    public String toString() {
        return "PacketPong[clientTimeMillis=" + clientTimeMillis + ", serverTick=" + serverTick + "]";
    }
}
