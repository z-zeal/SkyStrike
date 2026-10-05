package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Round-trip probe. The server echoes {@code clientTimeMillis} back untouched so the client can
 * measure latency without the two clocks needing to agree.
 */
public final class PacketPing implements Packet {

    public long clientTimeMillis;

    public PacketPing() {
    }

    public PacketPing(long clientTimeMillis) {
        this.clientTimeMillis = clientTimeMillis;
    }

    @Override
    public String toString() {
        return "PacketPing[clientTimeMillis=" + clientTimeMillis + "]";
    }
}
