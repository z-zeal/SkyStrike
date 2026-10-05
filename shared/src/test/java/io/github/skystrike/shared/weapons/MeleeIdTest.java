package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The melee wire encoding: 1000 + ordinal, disjoint from every gun ordinal, append-only. The
 * catalog order of the 21 sprite-backed melee weapons is frozen the same way the gun order is.
 */
class MeleeIdTest {

    @Test
    @DisplayName("wire ids are the base plus the ordinal, and round-trip")
    void wireIdRoundTrip() {
        for (MeleeId id : MeleeId.values()) {
            assertEquals(MeleeId.WIRE_ID_BASE + id.ordinal(), id.wireId());
            assertEquals(id, MeleeId.fromWireId(id.wireId()));
        }
        assertEquals(1000, MeleeId.POCKET_THORN.wireId());
    }

    @Test
    @DisplayName("the melee range can never collide with a gun ordinal")
    void meleeRangeIsDisjointFromGuns() {
        for (WeaponId gun : WeaponId.values()) {
            assertFalse(MeleeId.isMeleeWireId(gun.ordinal()), gun + " collides with the melee range");
        }
        assertTrue(MeleeId.WIRE_ID_BASE > WeaponId.values().length);
    }

    @Test
    @DisplayName("the wire-id discriminator is exact at both edges")
    void wireIdBounds() {
        assertFalse(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE - 1));
        assertTrue(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE));
        assertTrue(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE + MeleeId.values().length - 1));
        assertFalse(MeleeId.isMeleeWireId(MeleeId.WIRE_ID_BASE + MeleeId.values().length));
        assertFalse(MeleeId.isMeleeWireId(-1));
    }

    @Test
    @DisplayName("ordinal lookups fall back instead of throwing")
    void ordinalFallbacks() {
        assertEquals(MeleeId.DEFAULT, MeleeId.fromOrdinal(-1));
        assertEquals(MeleeId.DEFAULT, MeleeId.fromOrdinal(999));
        assertEquals(MeleeId.WINTER_KATANA, MeleeId.fromOrdinal(9));
        assertTrue(MeleeId.isValidOrdinal(0));
        assertTrue(MeleeId.isValidOrdinal(MeleeId.values().length - 1));
        assertFalse(MeleeId.isValidOrdinal(MeleeId.values().length));
        assertFalse(MeleeId.isValidOrdinal(-1));
    }

    @Test
    @DisplayName("the catalog roster is frozen: 21 weapons, knife default")
    void rosterIsFrozen() {
        assertEquals(21, MeleeId.values().length);
        assertEquals(MeleeId.TRENCH_KNUCKLE, MeleeId.DEFAULT);
        assertEquals("Trench Knuckle", MeleeId.DEFAULT.displayName());
        assertEquals(0, MeleeId.POCKET_THORN.ordinal());
        assertEquals(20, MeleeId.BLACK_WAKIZASHI.ordinal());
        assertEquals("winter-katana", MeleeId.WINTER_KATANA.assetId());
    }
}
