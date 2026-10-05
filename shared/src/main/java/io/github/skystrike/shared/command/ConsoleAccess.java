package io.github.skystrike.shared.command;

/**
 * The threshold policy that turns a {@link Permission} level into the single console capability
 * (console plan §4).
 *
 * <p>The capability is the only thing the client is told. It is not a request the client makes
 * and not a mode it enters: the server resolves a level, compares it against the configured
 * threshold, and pushes the resulting boolean. A client that flips the boolean locally gains
 * nothing, because every privileged command is authorised again server-side at execution time.
 *
 * <p>Changing the shipping threshold is a policy change, not a code change — dropping it to
 * {@link Permission#PLAYER} would give everyone a console whose registry is still filtered by
 * level, which is a legitimate future policy.
 */
public final class ConsoleAccess {

    /** Shipping default: moderator and above, so ordinary players have no console at all. */
    public static final Permission SHIPPING_THRESHOLD = Permission.MODERATOR;

    private ConsoleAccess() {
    }

    /** Whether {@code level} unlocks the console under the shipping threshold. */
    public static boolean isGranted(Permission level) {
        return isGranted(level, SHIPPING_THRESHOLD);
    }

    /** Whether {@code level} unlocks the console under a server-configured {@code threshold}. */
    public static boolean isGranted(Permission level, Permission threshold) {
        if (level == null) {
            return false;
        }
        return level.atLeast(threshold == null ? SHIPPING_THRESHOLD : threshold);
    }
}
