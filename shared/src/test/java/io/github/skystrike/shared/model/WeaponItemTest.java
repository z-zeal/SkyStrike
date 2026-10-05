package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Magazine and reserve are tracked separately, and every arithmetic path through them clamps:
 * no negative magazines, no overfilled magazines, no reserve spent twice.
 */
class WeaponItemTest {

    @Test
    @DisplayName("a fresh item is full to the registry table")
    void freshItemIsFull() {
        WeaponItem item = new WeaponItem(WeaponId.AWP);
        assertEquals(WeaponId.AWP, item.weaponId());
        assertEquals(5, item.magazine);
        assertEquals(25, item.reserve);
        assertEquals(WeaponRegistry.of(WeaponId.AWP).magazineSize(), item.magazineSize());
        assertFalse(item.needsReload(), "a full magazine needs nothing");
    }

    @Test
    @DisplayName("consumption clamps at zero")
    void consumptionClamps() {
        WeaponItem item = new WeaponItem(WeaponId.AWP);
        assertEquals(3, item.consume(3));
        assertEquals(2, item.magazine);
        assertEquals(2, item.consume(5), "cannot spend what is not there");
        assertEquals(0, item.magazine);
        assertFalse(item.hasRounds());
    }

    @Test
    @DisplayName("a reload is needed exactly when the magazine is short and the reserve holds")
    void needsReloadConditions() {
        WeaponItem item = new WeaponItem(WeaponId.DESERT_EAGLE);
        item.magazine = 3;
        assertTrue(item.needsReload());

        item.reserve = 0;
        assertFalse(item.needsReload(), "an empty reserve changes nothing");

        item.reserve = 20;
        item.magazine = item.magazineSize();
        assertFalse(item.needsReload(), "a full magazine changes nothing");
    }

    @Test
    @DisplayName("reserve transfer clamps on both ends, partial top-ups included")
    void reserveTransferMath() {
        WeaponItem item = new WeaponItem(WeaponId.SHOTGUN); // mag 8, reserve 40
        item.magazine = 6;
        assertEquals(2, item.transferFromReserve(Integer.MAX_VALUE), "only the shortfall moves");
        assertEquals(8, item.magazine);
        assertEquals(38, item.reserve);

        item.magazine = 0;
        item.reserve = 3;
        assertEquals(3, item.transferFromReserve(Integer.MAX_VALUE), "a short reserve moves whole");
        assertEquals(3, item.magazine);
        assertEquals(0, item.reserve);
    }

    @Test
    @DisplayName("an empty slot stays empty under every operation")
    void emptySlotBehaves() {
        WeaponItem item = new WeaponItem();
        assertFalse(item.hasWeapon());
        assertNull(item.weaponId());
        assertNull(item.definition());
        assertEquals(0, item.magazineSize());
        assertFalse(item.needsReload());
        item.refill();
        assertFalse(item.hasWeapon(), "refilling nothing does nothing");
    }

    @Test
    @DisplayName("copies are independent")
    void copiesAreIndependent() {
        WeaponItem item = new WeaponItem(WeaponId.P90);
        WeaponItem twin = item.copy();
        twin.consume(10);
        assertEquals(50, item.magazine, "editing the copy must not touch the original");
        assertEquals(40, twin.magazine);
    }
}
