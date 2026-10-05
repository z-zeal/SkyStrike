package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Voluntary disconnect. Lets the server free the slot immediately instead of waiting for the
 * keep-alive timeout.
 */
public final class PacketLeaveRequest implements Packet {

    public PacketLeaveRequest() {
    }

    @Override
    public String toString() {
        return "PacketLeaveRequest[]";
    }
}
