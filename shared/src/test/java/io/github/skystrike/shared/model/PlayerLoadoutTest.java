package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The switching rules of mechanics §8, on the one class the client prediction and the server
 * both run — if they ever disagreed, this is where a desync would be born.
 */
class PlayerLoadoutTest {

    private PlayerLoadout loadout;

    @BeforeEach
    void setUp() {
        loadout = new PlayerLoadout();
    }

    // --- Composition and slot shape ---------------------------------------------------------------

    @Test
    @DisplayName("the default loadout is rifle, sidearm, knife, hands on the primary")
    void defaultComposition() {
        assertEquals(WeaponId.DEFAULT, loadout.primary.weaponId());
        assertEquals(WeaponId.DEFAULT_SIDEARM, loadout.handgun.weaponId());
        assertEquals(MeleeId.DEFAULT, loadout.meleeId());
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot);
        assertFalse(loadout.reloading);
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);

        // Everyone spawns with a full tank.
        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT).magazineSize(), loadout.primary.magazine);
        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT_SIDEARM).magazineSize(), loadout.handgun.magazine);
    }

    @Test
    @DisplayName("slots 1-3 are filled, utility slots are not (until Phase 5)")
    void filledSemantics() {
        assertTrue(loadout.isFilled(1));
        assertTrue(loadout.isFilled(2));
        assertTrue(loadout.isFilled(3), "slot 3 can never be empty");
        assertFalse(loadout.isFilled(4), "utilities do not exist yet");
        assertFalse(loadout.isFilled(5), "utilities do not exist yet");
        assertFalse(PlayerLoadout.isValidSlot(0));
        assertFalse(PlayerLoadout.isValidSlot(6));
    }

    // --- Selection rules ---------------------------------------------------------------------------

    @Test
    @DisplayName("direct selection only ever lands on filled slots")
    void selectFilledOnly() {
        assertTrue(loadout.selectSlot(2));
        assertEquals(2, loadout.activeSlot);
        assertFalse(loadout.selectSlot(4), "empty slots cannot be selected");
        assertEquals(2, loadout.activeSlot);
        assertFalse(loadout.selectSlot(0), "out-of-range slots cannot be selected");
        assertFalse(loadout.selectSlot(9));
        assertEquals(2, loadout.activeSlot);
        assertFalse(loadout.selectSlot(2), "selecting the active slot is a no-op, not a toggle");
        assertEquals(2, loadout.activeSlot);
    }

    @Test
    @DisplayName("tap on the active primary quick-swaps to melee and back, remembering the origin")
    void tapSwapFromPrimaryRoundTrips() {
        assertTrue(loadout.tapSlot(1), "tapping the active primary drops to melee");
        assertEquals(PlayerLoadout.SLOT_MELEE, loadout.activeSlot);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.quickSwapOrigin,
            "the origin is remembered");

        assertTrue(loadout.tapSlot(1), "tapping the same key again returns");
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot);
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);
    }

    @Test
    @DisplayName("tap on the active handgun quick-swaps to melee and back")
    void tapSwapFromHandgunRoundTrips() {
        loadout.selectSlot(PlayerLoadout.SLOT_HANDGUN);

        assertTrue(loadout.tapSlot(PlayerLoadout.SLOT_HANDGUN));
        assertEquals(PlayerLoadout.SLOT_MELEE, loadout.activeSlot);
        assertEquals(PlayerLoadout.SLOT_HANDGUN, loadout.quickSwapOrigin);

        assertTrue(loadout.tapSlot(PlayerLoadout.SLOT_HANDGUN));
        assertEquals(PlayerLoadout.SLOT_HANDGUN, loadout.activeSlot);
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);
    }

    @Test
    @DisplayName("the memory is forgotten the moment any other slot is chosen")
    void swapMemoryIsClearedOnRealSwitch() {
        loadout.tapSlot(1); // to melee, remembering slot 1
        loadout.selectSlot(PlayerLoadout.SLOT_HANDGUN);
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);

        // And the tap rule does not fire from a memory-free slot press.
        loadout.tapSlot(PlayerLoadout.SLOT_MELEE); // explicit melee select
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);
    }

    @Test
    @DisplayName("tapping the active melee slot does nothing")
    void tapMeleeIsNoOp() {
        loadout.tapSlot(1); // to melee
        assertFalse(loadout.tapSlot(3), "already holding melee");
        assertEquals(PlayerLoadout.SLOT_MELEE, loadout.activeSlot);
    }

    @Test
    @DisplayName("idempotent select never quick-swaps, even repeated forever")
    void selectIsNotTap() {
        assertFalse(loadout.selectSlot(1), "already active");
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot);
        assertFalse(loadout.selectSlot(1));
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot,
            "the server's re-applied request must not toggle");
    }

    @Test
    @DisplayName("the wheel cycles filled slots only, in both directions, wrapping")
    void wheelCyclesFilledOnly() {
        assertEquals(2, loadout.cycle(1));
        assertEquals(3, loadout.cycle(1));
        assertEquals(1, loadout.cycle(1), "wrapping skips empty 4 and 5");

        loadout = new PlayerLoadout();
        assertEquals(3, loadout.cycle(-1), "backwards from 1 wraps to the last filled slot");
        assertEquals(2, loadout.cycle(-1));
        assertEquals(1, loadout.cycle(-1));
    }

    @Test
    @DisplayName("the wheel stays put when only melee is filled")
    void wheelWithOnlyMelee() {
        PlayerLoadout lonely = new PlayerLoadout(null, null, MeleeId.TRENCH_KNUCKLE);
        assertEquals(PlayerLoadout.SLOT_MELEE, lonely.activeSlot);
        assertEquals(PlayerLoadout.SLOT_MELEE, lonely.cycle(1));
        assertEquals(PlayerLoadout.SLOT_MELEE, lonely.cycle(-1));
    }

    // --- Switching side effects ---------------------------------------------------------------------

    @Test
    @DisplayName("every kind of switch cancels an in-progress reload")
    void switchingCancelsReload() {
        loadout.primary.magazine = 4;
        assertTrue(loadout.startReload());
        assertTrue(loadout.reloading);

        loadout.selectSlot(2);
        assertFalse(loadout.reloading, "a direct switch cancels");

        loadout.selectSlot(1);
        assertTrue(loadout.startReload());
        loadout.tapSlot(1); // quick-swap to melee
        assertFalse(loadout.reloading, "a tap swap cancels");

        assertFalse(loadout.startReload(), "melee cannot reload: hands are full of knife");
    }

    @Test
    @DisplayName("melee can never start a reload, and never holds a gun's ammo")
    void meleeDoesNotReload() {
        loadout.tapSlot(1);
        assertNull(loadout.activeItem());
        assertFalse(loadout.canReload());
        assertFalse(loadout.startReload());
        assertFalse(loadout.updateReload(1f), "nothing is ticking");
    }

    // --- Reload timing ------------------------------------------------------------------------------

    @Test
    @DisplayName("reload tops up the magazine from the reserve, never past full")
    void reloadRefills() {
        loadout.primary.magazine = 5; // Iron Carbine: mag 30, reserve 120
        assertTrue(loadout.startReload());
        float seconds = WeaponRegistry.of(WeaponId.DEFAULT).reloadSeconds();
        assertEquals(seconds, loadout.reloadTimer, 1e-4f);
        assertEquals(seconds, loadout.reloadDuration(), 1e-4f);

        boolean completed = false;
        for (int i = 0; i < 60 * 4 && !completed; i++) {
            completed = loadout.updateReload(1f / 60f);
        }
        assertTrue(completed, "the reload must finish inside four seconds of ticks");
        assertEquals(30, loadout.primary.magazine);
        assertEquals(95, loadout.primary.reserve);
        assertFalse(loadout.reloading);
        assertEquals(0f, loadout.reloadDuration());
    }

    @Test
    @DisplayName("a short reserve tops up partially")
    void reloadWithShortReserve() {
        loadout.primary.magazine = 0;
        loadout.primary.reserve = 7;
        assertTrue(loadout.startReload());
        while (!loadout.updateReload(1f / 60f)) {
            // tick until done; completion is asserted by the loop condition ending
        }
        assertEquals(7, loadout.primary.magazine);
        assertEquals(0, loadout.primary.reserve);
    }

    @Test
    @DisplayName("reload timing is float-dust safe: exactly reloadSeconds of 60 Hz ticks is enough")
    void reloadFloatDust() {
        // The respawn timer trap: 2.1 - 126*(1/60) != 0 exactly. Reproduce it here and require
        // the epsilon to absorb the dust — a reload must not hang for an extra tick.
        loadout.primary.magazine = 0; // Iron Carbine reload: 2.1 s = 126 ticks
        assertTrue(loadout.startReload());

        int ticks = Math.round(loadout.reloadDuration() * 60f);
        boolean completedEarly = false;
        for (int i = 0; i < ticks - 1; i++) {
            if (loadout.updateReload(1f / 60f)) {
                completedEarly = true;
            }
        }
        assertFalse(completedEarly, "the reload should not complete before its quoted time");
        assertTrue(loadout.updateReload(1f / 60f),
            "the final tick of the quoted duration completes the reload, dust or not");
        assertEquals(30, loadout.primary.magazine);
    }

    @Test
    @DisplayName("reload does not start when full, when dry of reserve, or twice")
    void reloadGuards() {
        assertFalse(loadout.canReload(), "full magazine");
        assertFalse(loadout.startReload());

        loadout.primary.magazine = 0;
        loadout.primary.reserve = 0;
        assertFalse(loadout.startReload(), "no reserve, no reload");

        loadout.primary.reserve = 30;
        assertTrue(loadout.startReload());
        assertFalse(loadout.startReload(), "already reloading");
    }

    // --- Composition and respawn ----------------------------------------------------------------------

    @Test
    @DisplayName("setComposition replaces changed weapons with full ones and keeps the rest")
    void compositionChanges() {
        loadout.primary.magazine = 3;
        loadout.setComposition(WeaponId.CATHEDRAL, null, null);

        assertEquals(WeaponId.CATHEDRAL, loadout.primary.weaponId());
        assertEquals(5, loadout.primary.magazine, "a swapped weapon comes full");
        assertEquals(WeaponId.DEFAULT_SIDEARM, loadout.handgun.weaponId(), "untouched slots keep their ammo");

        loadout.handgun.magazine = 1;
        loadout.setComposition(null, WeaponId.DEFAULT_SIDEARM, null);
        assertEquals(1, loadout.handgun.magazine, "an unchanged weapon keeps its magazine state");

        loadout.setComposition(null, null, MeleeId.WINTER_KATANA);
        assertEquals(MeleeId.WINTER_KATANA, loadout.meleeId());
    }

    @Test
    @DisplayName("respawn resets magazines, reload state and the active slot, keeping composition")
    void respawnReset() {
        loadout.setComposition(WeaponId.CATHEDRAL, null, MeleeId.YARD_WRENCH);
        loadout.primary.magazine = 1;
        loadout.primary.reserve = 2;
        loadout.handgun.magazine = 0;
        loadout.selectSlot(PlayerLoadout.SLOT_HANDGUN);
        assertTrue(loadout.startReload());

        loadout.resetForRespawn();

        assertEquals(WeaponId.CATHEDRAL, loadout.primary.weaponId(), "composition survives death");
        assertEquals(5, loadout.primary.magazine);
        assertEquals(25, loadout.primary.reserve);
        assertEquals(7, loadout.handgun.magazine);
        assertFalse(loadout.reloading);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot);
        assertEquals(PlayerLoadout.NO_QUICK_SWAP, loadout.quickSwapOrigin);
        assertEquals(MeleeId.YARD_WRENCH, loadout.meleeId());
    }

    @Test
    @DisplayName("copies are deep: ammo and slot state do not alias")
    void copiesAreDeep() {
        loadout.primary.magazine = 9;
        PlayerLoadout twin = loadout.copy();
        assertNotSame(loadout.primary, twin.primary);

        twin.primary.magazine = 1;
        twin.activeSlot = PlayerLoadout.SLOT_MELEE;
        assertEquals(9, loadout.primary.magazine);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, loadout.activeSlot);

        PlayerLoadout third = new PlayerLoadout();
        third.set(loadout);
        third.primary.reserve = 0;
        assertEquals(120, loadout.primary.reserve);
    }

    @Test
    @DisplayName("held weapon wire id follows the hands")
    void heldWireIdMirrorsHands() {
        assertEquals(WeaponId.DEFAULT.ordinal(), loadout.heldWeaponWireId());
        loadout.selectSlot(2);
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(), loadout.heldWeaponWireId());
        loadout.tapSlot(2); // to melee
        assertEquals(MeleeId.DEFAULT.wireId(), loadout.heldWeaponWireId(),
            "melee in hand is the melee wire id, not some gun");
    }
}
