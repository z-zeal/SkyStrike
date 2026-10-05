package io.github.skystrike.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The permission ladder and the one threshold that turns it into a capability.
 *
 * <p>The cases that matter are the failure modes: an unknown value must grant nothing, and a
 * higher level must never be refused something a lower one may do.
 */
class ConsoleAccessTest {

    @Test
    @DisplayName("the ladder is ordered and 'at least' is inclusive")
    void theLadderIsOrdered() {
        assertTrue(Permission.ADMIN.atLeast(Permission.MODERATOR));
        assertTrue(Permission.MODERATOR.atLeast(Permission.MODERATOR));
        assertFalse(Permission.PLAYER.atLeast(Permission.MODERATOR));
        assertTrue(Permission.EVERYONE.atLeast(Permission.EVERYONE));

        assertTrue(Permission.ADMIN.outranks(Permission.MODERATOR));
        assertFalse(Permission.MODERATOR.outranks(Permission.MODERATOR));
    }

    @Test
    @DisplayName("an out-of-range rank decodes to the least privileged level, never the most")
    void unknownRanksDecodeDownwards() {
        assertEquals(Permission.EVERYONE, Permission.fromRank(-1));
        assertEquals(Permission.EVERYONE, Permission.fromRank(99));
        assertEquals(Permission.ADMIN, Permission.fromRank(Permission.ADMIN.rank()));
    }

    @Test
    void namesParseCaseInsensitivelyAndUnknownNamesYieldNull() {
        assertEquals(Permission.MODERATOR, Permission.parse("moderator"));
        assertEquals(Permission.ADMIN, Permission.parse("  Admin "));
        assertNull(Permission.parse("superuser"));
        assertNull(Permission.parse(null));
    }

    @Test
    @DisplayName("the shipping threshold gives a console to moderators and above only")
    void shippingThresholdIsModeratorAndAbove() {
        assertEquals(Permission.MODERATOR, ConsoleAccess.SHIPPING_THRESHOLD);
        assertFalse(ConsoleAccess.isGranted(Permission.EVERYONE));
        assertFalse(ConsoleAccess.isGranted(Permission.PLAYER));
        assertTrue(ConsoleAccess.isGranted(Permission.MODERATOR));
        assertTrue(ConsoleAccess.isGranted(Permission.ADMIN));
    }

    @Test
    @DisplayName("lowering the threshold is a policy change, not a code change")
    void thresholdIsConfigurable() {
        assertTrue(ConsoleAccess.isGranted(Permission.PLAYER, Permission.PLAYER));
        assertFalse(ConsoleAccess.isGranted(Permission.PLAYER, Permission.ADMIN));
        assertTrue(ConsoleAccess.isGranted(Permission.EVERYONE, Permission.EVERYONE));
    }

    @Test
    @DisplayName("a missing level grants nothing")
    void nullLevelGrantsNothing() {
        assertFalse(ConsoleAccess.isGranted(null));
        assertFalse(ConsoleAccess.isGranted(null, Permission.EVERYONE));
    }
}
