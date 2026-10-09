package io.github.skystrike.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.command.PermissionResolver;
import io.github.skystrike.shared.command.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The built-in dev account: hardcoded in source, but elevated only while the host runs with the
 * debug master switch on. A shipping host must resolve the name to an ordinary player — the name
 * is public (it lives in this repo), and name grants match client-claimed names loosely, so an
 * ungated grant would hand every server running the build a superuser.
 */
class GameServerDevAccountTest {

    @Test
    @DisplayName("the built-in dev account is ADMIN on a debug host and an ordinary player otherwise")
    void builtinDevAccountFollowsTheDebugMasterSwitch() {
        PermissionResolver debugHost = new PermissionResolver();
        GameServer.applyBuiltinDevAccount(debugHost, true);

        assertEquals(Permission.ADMIN, debugHost.resolve(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME));
        assertEquals(Permission.ADMIN, debugHost.resolve(2, "zealdev"),
            "name matching stays case-insensitive, as configured grants are");
        assertTrue(debugHost.hasConsoleAccess(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME),
            "ADMIN clears the shipping console threshold, so the account sees every command");
        assertEquals(Permission.PLAYER, debugHost.resolve(3, "Nova"),
            "the built-in account elevates only its own name");

        PermissionResolver shippingHost = new PermissionResolver();
        GameServer.applyBuiltinDevAccount(shippingHost, false);

        assertEquals(Permission.PLAYER, shippingHost.resolve(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME),
            "a shipping host must not elevate a name just because the source names it");
        assertFalse(shippingHost.hasConsoleAccess(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME));
    }

    @Test
    @DisplayName("the built-in grant composes with --dev and --grant like any other resolver state")
    void builtinDevAccountComposesWithOtherGrants() {
        PermissionResolver resolver = new PermissionResolver();
        resolver.grant("Nova", Permission.MODERATOR);
        GameServer.applyBuiltinDevAccount(resolver, true);

        assertEquals(Permission.ADMIN, resolver.resolve(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME));
        assertEquals(Permission.MODERATOR, resolver.resolve(2, "Nova"),
            "a configured grant for another name is untouched");

        // A mid-match demotion still wins: runtime overrides outrank configured grants.
        resolver.setRuntimeLevel(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME, Permission.PLAYER);
        assertEquals(Permission.PLAYER, resolver.resolve(1, GameServer.BUILTIN_DEV_ACCOUNT_NAME));
    }
}
