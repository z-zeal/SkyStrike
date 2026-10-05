package io.github.skystrike.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.command.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PermissionResolverTest {

    private PermissionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PermissionResolver();
    }

    @Test
    @DisplayName("an unrecognised player is an ordinary player with no console")
    void theFallbackGrantsNothing() {
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));
        assertFalse(resolver.hasConsoleAccess(1, "Nova"));
        assertEquals(Permission.PLAYER, resolver.resolve(2, null));
        assertEquals(Permission.PLAYER, resolver.resolve(3, "   "));
    }

    @Test
    @DisplayName("configured grants are case- and whitespace-insensitive")
    void grantsMatchLoosely() {
        resolver.grant("Nova", Permission.MODERATOR);

        assertEquals(Permission.MODERATOR, resolver.resolve(1, "nova"));
        assertEquals(Permission.MODERATOR, resolver.resolve(1, "  NOVA  "));
        assertTrue(resolver.hasConsoleAccess(1, "Nova"));

        assertTrue(resolver.revoke("NOVA"));
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));
    }

    @Test
    @DisplayName("a runtime promotion outranks the configured grant and reports the change")
    void runtimePromotionTakesPrecedence() {
        resolver.grant("Nova", Permission.PLAYER);

        assertTrue(resolver.setRuntimeLevel(1, "Nova", Permission.ADMIN));
        assertEquals(Permission.ADMIN, resolver.resolve(1, "Nova"));
        assertTrue(resolver.hasConsoleAccess(1, "Nova"));

        // Setting the same level again changes nothing.
        assertFalse(resolver.setRuntimeLevel(1, "Nova", Permission.ADMIN));

        // Clearing the override falls back to the grant.
        assertTrue(resolver.setRuntimeLevel(1, "Nova", null));
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));
    }

    @Test
    @DisplayName("a promotion is scoped to the session, so a reused id does not inherit it")
    void forgettingDropsTheOverride() {
        resolver.setRuntimeLevel(7, "Nova", Permission.ADMIN);
        assertEquals(Permission.ADMIN, resolver.resolve(7, "Nova"));

        resolver.forget(7);
        assertEquals(Permission.PLAYER, resolver.resolve(7, "Rook"));
        assertFalse(resolver.hasConsoleAccess(7, "Rook"));
    }

    @Test
    @DisplayName("lowering the console threshold needs no code change")
    void thresholdIsConfigurable() {
        assertFalse(resolver.hasConsoleAccess(1, "Nova"));

        resolver.setConsoleThreshold(Permission.PLAYER);
        assertEquals(Permission.PLAYER, resolver.consoleThreshold());
        assertTrue(resolver.hasConsoleAccess(1, "Nova"));

        resolver.setConsoleThreshold(null);
        assertEquals(Permission.MODERATOR, resolver.consoleThreshold());
        assertFalse(resolver.hasConsoleAccess(1, "Nova"));
    }
}
