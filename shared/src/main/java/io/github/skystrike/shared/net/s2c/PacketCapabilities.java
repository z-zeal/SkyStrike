package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.command.ConsoleAccess;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.net.Packet;

/**
 * What this client is allowed to see, pushed by the server (console plan §4.1).
 *
 * <p>Sent on join and again whenever the level changes, so a promotion takes effect mid-match
 * without a reconnect. The client never requests it and never computes it.
 *
 * <p>Both fields are sent even though one is derived from the other: the boolean is what the UI
 * actually branches on, and the level is what a future {@code help} listing filters by. Deriving
 * the boolean client-side would mean shipping the server's threshold policy to the client, which
 * leaks exactly the information the "the console is invisible, not disabled" rule exists to hide.
 */
public final class PacketCapabilities implements Packet {

    /** The caller's resolved level. Defaults to the least privileged value, never the most. */
    public Permission level = Permission.EVERYONE;

    /** Whether the console exists for this client at all. */
    public boolean consoleAccess;

    public PacketCapabilities() {
    }

    public PacketCapabilities(Permission level, boolean consoleAccess) {
        this.level = level == null ? Permission.EVERYONE : level;
        this.consoleAccess = consoleAccess;
    }

    /** Builds the packet for {@code level} under the shipping console threshold. */
    public static PacketCapabilities forLevel(Permission level) {
        return new PacketCapabilities(level, ConsoleAccess.isGranted(level));
    }

    /** Builds the packet for {@code level} under a server-configured threshold. */
    public static PacketCapabilities forLevel(Permission level, Permission consoleThreshold) {
        return new PacketCapabilities(level, ConsoleAccess.isGranted(level, consoleThreshold));
    }

    /** True when the two packets say the same thing, used to suppress redundant pushes. */
    public boolean matches(PacketCapabilities other) {
        return other != null && other.level == level && other.consoleAccess == consoleAccess;
    }

    @Override
    public String toString() {
        return "PacketCapabilities[level=" + level + ", consoleAccess=" + consoleAccess + "]";
    }
}
