package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The one table the server validates against and the picker draws from. */
class LoadoutOptionsTest {

    @Test
    @DisplayName("slot 1 takes every gun in the catalogue, in wire order")
    void primariesAreTheWholeCatalogue() {
        List<WeaponId> primaries = LoadoutOptions.primaries();
        assertEquals(WeaponId.values().length, primaries.size());
        for (int i = 0; i < primaries.size(); i++) {
            assertEquals(i, primaries.get(i).ordinal(), "option index must be the wire ordinal");
        }
        assertTrue(LoadoutOptions.isLegalPrimary(WeaponId.DEFAULT.ordinal()));
        assertTrue(LoadoutOptions.isLegalPrimary(WeaponId.values().length - 1));
        assertFalse(LoadoutOptions.isLegalPrimary(-1));
        assertFalse(LoadoutOptions.isLegalPrimary(WeaponId.values().length));
    }

    @Test
    @DisplayName("slot 2 is pistols and revolvers, and nothing else")
    void sidearmsOnlyInSlotTwo() {
        List<WeaponId> sidearms = LoadoutOptions.sidearms();
        assertFalse(sidearms.isEmpty());
        assertTrue(sidearms.contains(WeaponId.DEFAULT_SIDEARM));

        int expected = 0;
        for (WeaponId id : WeaponId.values()) {
            WeaponClass weaponClass = WeaponRegistry.of(id).ballistics().weaponClass();
            boolean sidearm = weaponClass == WeaponClass.PISTOL || weaponClass == WeaponClass.REVOLVER;
            assertEquals(sidearm, LoadoutOptions.isLegalHandgun(id.ordinal()),
                id.displayName() + " (" + weaponClass + ") is on the wrong side of the slot-2 rule");
            if (sidearm) {
                expected++;
            }
        }
        assertEquals(expected, sidearms.size());

        int previous = -1;
        for (WeaponId id : sidearms) {
            assertTrue(id.ordinal() > previous, "the sidearm list must stay in wire order");
            previous = id.ordinal();
        }

        assertFalse(LoadoutOptions.isLegalHandgun(WeaponId.DEFAULT.ordinal()),
            "the default carbine is a rifle; slot 2 must refuse it");
        assertFalse(LoadoutOptions.isLegalHandgun(-1));
        assertFalse(LoadoutOptions.isLegalHandgun(WeaponId.values().length));
    }

    @Test
    @DisplayName("melee, throwables and gadgets accept exactly their own catalogues")
    void theRemainingSlots() {
        assertEquals(MeleeId.values().length, LoadoutOptions.meleeWeapons().size());
        assertTrue(LoadoutOptions.isLegalMelee(MeleeId.DEFAULT.ordinal()));
        assertFalse(LoadoutOptions.isLegalMelee(-1));
        assertFalse(LoadoutOptions.isLegalMelee(MeleeId.values().length));

        assertEquals(UtilityId.values().length, LoadoutOptions.utilities().size());
        assertTrue(LoadoutOptions.isLegalUtility(UtilityId.CLAYMORE.ordinal()));
        assertFalse(LoadoutOptions.isLegalUtility(-1));
        assertFalse(LoadoutOptions.isLegalUtility(UtilityId.values().length));

        assertEquals(GadgetId.values().length, LoadoutOptions.gadgets().size());
        assertSame(GadgetId.NONE, LoadoutOptions.gadgets().get(0),
            "'carry nothing' is the first gadget choice, not a missing one");
        assertTrue(LoadoutOptions.isLegalGadget(GadgetId.NONE.ordinal()),
            "an empty gadget slot is a legal request");
        assertTrue(LoadoutOptions.isLegalGadget(GadgetId.FUEL_TANK.ordinal()));
        assertFalse(LoadoutOptions.isLegalGadget(-1));
        assertFalse(LoadoutOptions.isLegalGadget(GadgetId.values().length));
    }

    @Test
    @DisplayName("the tables are shared, so nobody can edit the rules from a screen")
    void listsAreImmutable() {
        assertSame(LoadoutOptions.primaries(), LoadoutOptions.primaries());
        assertThrows(UnsupportedOperationException.class,
            () -> LoadoutOptions.primaries().add(WeaponId.DEFAULT));
        assertThrows(UnsupportedOperationException.class,
            () -> LoadoutOptions.sidearms().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> LoadoutOptions.gadgets().add(GadgetId.NONE));
    }
}
