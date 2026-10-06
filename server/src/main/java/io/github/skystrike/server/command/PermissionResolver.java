package io.github.skystrike.server.command;

import io.github.skystrike.shared.command.ConsoleAccess;
import io.github.skystrike.shared.command.Permission;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns an identity into a {@link Permission} level (console plan §10,
 * {@code server/command/PermissionResolver}).
 *
 * <p>Two sources, checked in order:
 *
 * <ol>
 *   <li>a <b>runtime override</b> set for a live player id — this is what a mid-match promotion
 *       writes, and it is deliberately scoped to the session so it disappears on disconnect;</li>
 *   <li>a <b>configured name grant</b>, which is the stand-in for the authenticated-identity
 *       lookup a real deployment would do.</li>
 * </ol>
 *
 * <p>Everything else resolves to {@link #DEFAULT_LEVEL}. The fallback is the least privileged
 * useful level, never the most: a lookup failure must not hand out a console.
 *
 * <p>Name matching is case-insensitive and deliberately crude. It is a placeholder for a real
 * identity provider, and it is isolated here so that replacing it later touches one file.
 */
public final class PermissionResolver {

    /** What an unrecognised joined player gets. */
    public static final Permission DEFAULT_LEVEL = Permission.PLAYER;

    private final Map<String, Permission> grantsByName = new LinkedHashMap<>();
    private final Map<Integer, Permission> overridesByPlayerId = new HashMap<>();
    private Permission consoleThreshold = ConsoleAccess.SHIPPING_THRESHOLD;

    /**
     * Dev mode ({@code --dev} on the launcher): every joined player resolves to
     * {@link Permission#ADMIN} regardless of grants or overrides. It exists so a development
     * host exercises every command without identity plumbing, it is off by default, and it is
     * deliberately loud: {@code GameServer} announces it on startup.
     */
    private boolean devMode;

    /** Statically grants {@code name} a level, as a deployment would from its config. */
    public PermissionResolver grant(String name, Permission level) {
        if (name == null || name.isBlank() || level == null) {
            return this;
        }
        grantsByName.put(normalise(name), level);
        return this;
    }

    /** Removes a configured grant. */
    public boolean revoke(String name) {
        return name != null && grantsByName.remove(normalise(name)) != null;
    }

    /**
     * Promotes or demotes a live player for the rest of their session.
     *
     * @return true when the effective level actually changed
     */
    public boolean setRuntimeLevel(int playerId, String name, Permission level) {
        Permission before = resolve(playerId, name);
        if (level == null) {
            overridesByPlayerId.remove(playerId);
        } else {
            overridesByPlayerId.put(playerId, level);
        }
        return resolve(playerId, name) != before;
    }

    /** Drops session state for a departing player. */
    public void forget(int playerId) {
        overridesByPlayerId.remove(playerId);
    }

    /** The effective level for a joined player. Never null. */
    public Permission resolve(int playerId, String name) {
        if (devMode) {
            return Permission.ADMIN;
        }
        Permission override = overridesByPlayerId.get(playerId);
        if (override != null) {
            return override;
        }
        if (name != null && !name.isBlank()) {
            Permission granted = grantsByName.get(normalise(name));
            if (granted != null) {
                return granted;
            }
        }
        return DEFAULT_LEVEL;
    }

    /** Whether this player's console exists at all, under the configured threshold. */
    public boolean hasConsoleAccess(int playerId, String name) {
        return ConsoleAccess.isGranted(resolve(playerId, name), consoleThreshold);
    }

    /** The level a caller must reach for the console to exist. */
    public Permission consoleThreshold() {
        return consoleThreshold;
    }

    /** Retunes the policy without a code change (console plan §4.3). */
    public PermissionResolver setConsoleThreshold(Permission threshold) {
        this.consoleThreshold = threshold == null ? ConsoleAccess.SHIPPING_THRESHOLD : threshold;
        return this;
    }

    /** Whether every joined player resolves to admin. See the field comment before enabling. */
    public PermissionResolver setDevMode(boolean devMode) {
        this.devMode = devMode;
        return this;
    }

    public boolean devMode() {
        return devMode;
    }

    public void clear() {
        grantsByName.clear();
        overridesByPlayerId.clear();
        consoleThreshold = ConsoleAccess.SHIPPING_THRESHOLD;
        devMode = false;
    }

    private static String normalise(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
