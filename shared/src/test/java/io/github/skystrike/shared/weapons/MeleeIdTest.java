package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The melee wire encoding: one int on the wire can name a gun or a melee weapon, and the cut
 * between the two ranges must be exact, because damage events and the kill feed read it.
 */
class MeleeIdTest {

    @Test
    @DisplayName("wire ids are the base plus the ordinal, and round-trip")
    void wireIdsRoundTrip() {
        for (MeleeId id : MeleeId.values()) {
            assertEquals(MeleeId.WIRE_ID_BASE + id.ordinal(), id.wireId());
            assertEquals(id, MeleeId.fromWireId(id.wireId()));
        }
        assertEquals(1000, MeleeId.COMBAT_KNIFE.wireId());
    }

    @Test
    @DisplayName("the melee range starts exactly above every gun ordinal")
    void rangesDoNotOverlap() {
        for (WeaponId gun : WeaponId.values()) {
            assertFalse(MeleeId.isMeleeWireId(gun.ordinal()), gun + " collides with the melee range");
        }
        assertTrue(MeleeId.WIRE_ID_BASE > WeaponId.values().length);
    }

    @Test
    @DisplayName("range checks are exact at the boundaries")
    void boundariesAreExact() {
        assertFalse(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE - 1));
        assertTrue(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE));
        assertTrue(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE + MeleeId.values().length - 1));
        assertFalse(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE + MeleeId.values().length));
        assertFalse(MeleeId.isMeleeWireId(-1));
    }

    @Test
    @DisplayName("out-of-range lookups fall back instead of throwing")
    void lookupsFallBack() {
        assertEquals(MeleeId.DEFAULT, MeleeId.fromOrdinal(-1));
        assertEquals(MeleeId.DEFAULT, MeleeId.fromOrdinal(999));
        assertEquals(MeleeId.KATANA, MeleeId.fromOrdinal(3));
        assertTrue(MeleeId.isValidOrdinal(0));
        assertTrue(MeleeId.isValidOrdinal(3));
        assertFalse(MeleeId.isValidOrdinal(4));
        assertFalse(MeleeId.isValidOrdinal(-1));
    }

    @Test
    @DisplayName("slot 3 is never empty: the default melee weapon always exists")
    void defaultExists() {
        assertEquals(MeleeId.COMBAT_KNIFE, MeleeId.DEFAULT);
        assertEquals("Combat Knife", MeleeId.DEFAULT.displayName());
    }
}
