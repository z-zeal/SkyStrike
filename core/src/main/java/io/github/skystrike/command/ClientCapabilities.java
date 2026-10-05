package io.github.skystrike.command;

import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;

/**
 * The client's cached copy of what the server said it may do (console plan §2, §4).
 *
 * <p>This is one of exactly two pieces of state in the whole chat/console system, and it is the
 * one the client does not own. It starts at "no console", it only ever changes because a
 * {@link PacketCapabilities} arrived, and nothing here asks for anything.
 *
 * <p>The derived rule lives here too, because it is derived and not stored: a line is a command
 * if, and only if, the console exists for this client <b>and</b> the text begins with exactly one
 * slash. There is no mode to enter, nothing to transition, and nothing that can disagree with
 * what submit will actually do — recompute it on every keystroke and it is always right.
 */
public final class ClientCapabilities {

    /** The console-escape prefix: {@code //hello} is chat reading {@code /hello}. */
    public static final String COMMAND_PREFIX = "/";

    private Permission level = Permission.EVERYONE;
    private boolean consoleAccess;
    private boolean everReceived;

    /**
     * Applies a server push.
     *
     * @return true when the capability actually changed, so the caller can print one system line
     *     rather than one per push
     */
    public boolean apply(PacketCapabilities packet) {
        if (packet == null) {
            return false;
        }
        Permission newLevel = packet.level == null ? Permission.EVERYONE : packet.level;
        boolean changed = !everReceived || newLevel != level || packet.consoleAccess != consoleAccess;
        this.level = newLevel;
        this.consoleAccess = packet.consoleAccess;
        this.everReceived = true;
        return changed;
    }

    /** Resets to the safe default. Called when a connection drops. */
    public void reset() {
        level = Permission.EVERYONE;
        consoleAccess = false;
        everReceived = false;
    }

    public Permission level() {
        return level;
    }

    /** Whether the console exists for this client at all. False until told otherwise. */
    public boolean consoleAccess() {
        return consoleAccess;
    }

    /** Whether the server has pushed a capability yet. */
    public boolean received() {
        return everReceived;
    }

    /**
     * The whole "is this a command?" rule, recomputed as the player types.
     *
     * <p>Without console access a leading slash is ordinary text, which is why the access flag
     * is the first term rather than an afterthought at submit time.
     */
    public boolean isCommandLine(String text) {
        if (!consoleAccess || text == null) {
            return false;
        }
        return text.startsWith(COMMAND_PREFIX) && !text.startsWith(COMMAND_PREFIX + COMMAND_PREFIX);
    }

    /**
     * What a non-command line should actually be sent as.
     *
     * <p>Only the {@code //} escape is special, and it is special for everyone: a player without
     * a console typing {@code //x} still means {@code /x}, because the escape is a typing
     * convention and not a permission.
     */
    public static String chatBodyFor(String text) {
        if (text == null) {
            return "";
        }
        if (text.startsWith(COMMAND_PREFIX + COMMAND_PREFIX)) {
            return text.substring(1);
        }
        return text;
    }
}
