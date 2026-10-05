package io.github.skystrike.shared.command;

/**
 * The four authorisation levels (console plan §10).
 *
 * <p>Ordered by rank, so a check is always "at least this level" rather than an equality test —
 * an admin must never be refused something a moderator can do.
 *
 * <p>This lives in {@code shared} because both sides need the same ladder: the server resolves a
 * level and authorises against it, and the client only ever reflects what it was told. Nothing
 * here grants anything by itself; {@link ConsoleAccess} turns a level into the one capability the
 * client is allowed to see.
 */
public enum Permission {

    /** Anything at all, including a connection that has not finished joining. */
    EVERYONE,

    /** An ordinary joined player. The shipping default. */
    PLAYER,

    /** Mute, kick, and the rest of the match-management set. */
    MODERATOR,

    /** Everything, including the dedicated server's own terminal. */
    ADMIN;

    /** Rank on the ladder; higher is more privileged. */
    public int rank() {
        return ordinal();
    }

    /** True when this level satisfies a requirement of {@code required}. */
    public boolean atLeast(Permission required) {
        return required == null || rank() >= required.rank();
    }

    /** True when this level is strictly more privileged than {@code other}. */
    public boolean outranks(Permission other) {
        return other != null && rank() > other.rank();
    }

    /**
     * Defensive decode for a value that arrived from outside the process. An unknown rank is
     * {@link #EVERYONE}, which grants nothing — never {@link #ADMIN}.
     */
    public static Permission fromRank(int rank) {
        Permission[] values = values();
        if (rank < 0 || rank >= values.length) {
            return EVERYONE;
        }
        return values[rank];
    }

    /** Case-insensitive name lookup, returning {@code null} rather than throwing. */
    public static Permission parse(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (Permission level : values()) {
            if (level.name().equalsIgnoreCase(trimmed)) {
                return level;
            }
        }
        return null;
    }
}
