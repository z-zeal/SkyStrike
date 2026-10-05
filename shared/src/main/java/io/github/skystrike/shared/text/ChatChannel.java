package io.github.skystrike.shared.text;

/**
 * Delivery and presentation channels for one chat or console line.
 *
 * <p>{@link #ALL} and {@link #TEAM} are player-authored channels. {@link #SYSTEM} and
 * {@link #SERVER} are authoritative notices. The remaining two are console-only and must not be
 * exposed to a client that has not been granted console access.
 */
public enum ChatChannel {
    ALL,
    TEAM,
    SYSTEM,
    SERVER,
    CONSOLE,
    COMMAND_ECHO,
    DEBUG;

    /** True when this channel is an ordinary player chat destination. */
    public boolean isChatTarget() {
        return this == ALL || this == TEAM;
    }

    /**
     * True when this channel must be absent, rather than merely dimmed, for players without
     * server-granted console access.
     */
    public boolean requiresConsoleAccess() {
        return this == CONSOLE || this == COMMAND_ECHO || this == DEBUG;
    }

    /** Whether a client with the given capability may render this channel. */
    public boolean isVisibleTo(boolean consoleAccess) {
        return consoleAccess || !requiresConsoleAccess();
    }
}
