package io.github.skystrike.shared.config;

/**
 * The build-wide debug master switch, now a runtime decision (playable build plan M1 §2.1).
 *
 * <p>Resolution sources, checked in order, first hit wins:
 *
 * <ol>
 *   <li>{@code -Dskystrike.debug=true} — the client developer flag;</li>
 *   <li>{@code SKYSTRIKE_DEBUG=1} in the environment;</li>
 *   <li>{@code --dev} on the launcher command line.</li>
 * </ol>
 *
 * <p>The rule lives in {@code shared} and resolves once, so client and server processes read the
 * same policy. The shipping default stays {@code false}: with the master off, {@link DebugState}
 * reads are their safe defaults regardless of what was typed, which is what makes a stray
 * {@code /noclip 1} inert in a release build at the <i>read</i> site and not just at the parse
 * site.
 */
public final class DebugFlags {

    /** The value used when no source says otherwise. Never true in a shipping build. */
    public static final boolean DEFAULT_ENABLED = false;

    private static boolean initialised;
    private static boolean enabled = DEFAULT_ENABLED;

    private DebugFlags() {
    }

    /**
     * Resolves the master switch once from the system property, the environment and the launcher
     * arguments. Called by each process entry point before anything reads {@link #enabled()};
     * later calls are ignored so an early resolution cannot be retuned mid-run.
     */
    public static synchronized void initialise(String[] launcherArgs) {
        if (initialised) {
            return;
        }
        enabled = resolve(launcherArgs);
        initialised = true;
    }

    /** The resolved master switch. Initialises with no launcher arguments on first read. */
    public static boolean enabled() {
        if (!initialised) {
            initialise(new String[0]);
        }
        return enabled;
    }

    /** Test hook: drops the cached resolution so the next read re-resolves. Not for game code. */
    public static synchronized void resetForTests() {
        initialised = false;
        enabled = DEFAULT_ENABLED;
    }

    private static boolean resolve(String[] launcherArgs) {
        String property = System.getProperty("skystrike.debug");
        if (isTruthy(property)) {
            return true;
        }
        String environment = System.getenv("SKYSTRIKE_DEBUG");
        if (isTruthy(environment)) {
            return true;
        }
        if (launcherArgs != null) {
            for (String arg : launcherArgs) {
                if ("--dev".equals(arg)) {
                    return true;
                }
            }
        }
        return DEFAULT_ENABLED;
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "1", "true", "on", "yes" -> true;
            default -> false;
        };
    }
}
