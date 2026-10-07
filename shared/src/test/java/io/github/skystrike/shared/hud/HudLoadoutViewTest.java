package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The M4 gate, as a test: ammo, reload, quick-swap and utility counts on the HUD never disagree
 * with the {@link PlayerLoadout} they are drawn from. Every assertion here pins the exact text
 * the bar prints, because "roughly the right number" is how a HUD lies.
 */
class HudLoadoutViewTest {

    private PlayerLoadout loadout;

    @BeforeEach
    void setUp() {
        loadout = new PlayerLoadout();
    }

    @Test
    @DisplayName("the five default slots read as the loadout holds them")
    void defaultSlots() {
        List<HudLoadoutView.SlotView> slots = HudLoadoutView.slots(loadout);
        assertEquals(5, slots.size());

        HudLoadoutView.SlotView primary = slots.get(0);
        assertEquals(1, primary.slot());
        assertEquals(WeaponId.DEFAULT.displayName(), primary.label());
        assertEquals("30/120", primary.countText());
        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT).magazineSize(), primary.magazine());
        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT).reserveAmmo(), primary.reserve());
        assertTrue(primary.active(), "the default loadout starts on the primary");
        assertFalse(primary.utility());
        assertFalse(primary.melee());

        HudLoadoutView.SlotView handgun = slots.get(1);
        assertEquals(WeaponId.DEFAULT_SIDEARM.displayName(), handgun.label());
        assertEquals("15/75", handgun.countText());
        assertFalse(handgun.active());

        HudLoadoutView.SlotView melee = slots.get(2);
        assertEquals(MeleeId.DEFAULT.displayName(), melee.label());
        assertEquals(HudLoadoutView.NO_COUNT, melee.countText());
        assertTrue(melee.melee());
        assertTrue(melee.filled(), "slot 3 can never be empty");

        HudLoadoutView.SlotView utilityA = slots.get(3);
        assertEquals(UtilityId.DEFAULT_PRIMARY.displayName(), utilityA.label());
        assertEquals("x" + UtilityRegistry.of(UtilityId.DEFAULT_PRIMARY).carriedCount(),
            utilityA.countText());
        assertEquals("x2", utilityA.countText());
        assertTrue(utilityA.utility());

        HudLoadoutView.SlotView utilityB = slots.get(4);
        assertEquals(UtilityId.DEFAULT_SECONDARY.displayName(), utilityB.label());
        assertEquals("x2", utilityB.countText());
    }

    @Test
    @DisplayName("spending rounds and throwables moves the HUD text, not a copy of it")
    void countsFollowTheLoadout() {
        loadout.primary.consume(7);
        assertEquals("23/120", HudLoadoutView.slot(loadout, 1).countText());

        loadout.primary.magazine = 0;
        loadout.primary.reserve = 0;
        HudLoadoutView.SlotView dry = HudLoadoutView.slot(loadout, 1);
        assertEquals("0/0", dry.countText());
        assertTrue(dry.dry());
        assertEquals(0f, dry.magazineFraction());

        loadout.selectSlot(PlayerLoadout.SLOT_UTILITY_A);
        assertTrue(loadout.consumeActiveUtility());
        assertEquals("x1", HudLoadoutView.slot(loadout, 4).countText());

        loadout.utilityACount = 0;
        HudLoadoutView.SlotView spent = HudLoadoutView.slot(loadout, 4);
        assertEquals("x0", spent.countText());
        assertFalse(spent.filled(), "a depleted throwable is empty for selection");
        assertEquals(UtilityId.DEFAULT_PRIMARY.displayName(), spent.label(),
            "its type survives depletion, so the bar keeps naming it");
    }

    @Test
    @DisplayName("an empty gun slot says so instead of inventing a magazine")
    void emptyGunSlot() {
        loadout.primary = null;
        HudLoadoutView.SlotView empty = HudLoadoutView.slot(loadout, 1);
        assertEquals("Empty", empty.label());
        assertEquals(HudLoadoutView.NO_COUNT, empty.countText());
        assertFalse(empty.filled());
        assertEquals(0, empty.magazineSize());
    }

    @Test
    @DisplayName("the reload sweep runs from 0 to 1 over the weapon's own reload time")
    void reloadSweep() {
        assertEquals(0f, HudLoadoutView.reloadProgress(loadout));
        assertEquals("", HudLoadoutView.reloadText(loadout));

        loadout.primary.consume(10);
        assertTrue(loadout.startReload());
        float duration = WeaponRegistry.of(WeaponId.DEFAULT).reloadSeconds();
        assertEquals(duration, loadout.reloadDuration(), 1e-4f);
        assertEquals(0f, HudLoadoutView.reloadProgress(loadout), 1e-4f);

        assertEquals("RELOADING 2.1s", HudLoadoutView.reloadText(loadout),
            "the text counts the weapon's own reload time down, not a HUD-local clock");

        loadout.reloadTimer = duration / 2f;
        assertEquals(0.5f, HudLoadoutView.reloadProgress(loadout), 1e-4f);

        loadout.reloadTimer = 1.25f;
        assertEquals("RELOADING 1.3s", HudLoadoutView.reloadText(loadout));

        loadout.reloadTimer = 0f;
        assertEquals(1f, HudLoadoutView.reloadProgress(loadout), 1e-4f);

        loadout.cancelReload();
        assertEquals(0f, HudLoadoutView.reloadProgress(loadout));
        assertEquals("", HudLoadoutView.reloadText(loadout));
    }

    @Test
    @DisplayName("a quick swap marks the slot it came from, and clears when it returns")
    void quickSwapOrigin() {
        assertEquals("", HudLoadoutView.quickSwapText(loadout));
        assertFalse(HudLoadoutView.slot(loadout, 1).quickSwapOrigin());

        assertTrue(loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY), "tapping the live slot drops to melee");
        assertEquals(PlayerLoadout.SLOT_MELEE, loadout.activeSlot);
        assertEquals("SWAP 1", HudLoadoutView.quickSwapText(loadout));
        assertTrue(HudLoadoutView.slot(loadout, 1).quickSwapOrigin());
        assertTrue(HudLoadoutView.slot(loadout, 3).active());

        assertTrue(loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY));
        assertEquals("", HudLoadoutView.quickSwapText(loadout));
        assertFalse(HudLoadoutView.slot(loadout, 1).quickSwapOrigin());
    }

    @Test
    @DisplayName("gadget slots report carry state, not a guess at one")
    void gadgetViews() {
        List<HudLoadoutView.GadgetView> empty = HudLoadoutView.gadgets(loadout);
        assertEquals(2, empty.size());
        assertEquals("Q", empty.get(0).key());
        assertEquals("E", empty.get(1).key());
        assertEquals("Empty", empty.get(0).label());
        assertEquals(HudLoadoutView.NO_COUNT, empty.get(0).statusText());
        assertFalse(empty.get(0).equipped());

        loadout.setGadgets(GadgetId.SHIELD, GadgetId.FUEL_TANK);
        HudLoadoutView.GadgetView shield = HudLoadoutView.gadget(loadout, 0);
        assertEquals(GadgetId.SHIELD, shield.gadget());
        assertEquals("OFF", shield.statusText(), "a stowed shield is off, not absent");
        assertEquals(150f, shield.maxDurability(), 1e-4f);
        assertEquals(1f, shield.durabilityFraction(), 1e-4f);

        loadout.gadgetQ.active = true;
        assertEquals("ON", HudLoadoutView.gadget(loadout, 0).statusText());

        loadout.gadgetQ.applyDurabilityDamage(75f);
        assertEquals(0.5f, HudLoadoutView.gadget(loadout, 0).durabilityFraction(), 1e-4f);
        loadout.gadgetQ.applyDurabilityDamage(1000f);
        assertEquals("BROKEN", HudLoadoutView.gadget(loadout, 0).statusText());

        HudLoadoutView.GadgetView tank = HudLoadoutView.gadget(loadout, 1);
        assertEquals("WORN", tank.statusText(), "a passive gadget is worn, never toggled");
        assertEquals(1f, tank.durabilityFraction(), 1e-4f, "no pool draws a full bar");
    }

    @Test
    @DisplayName("no loadout yields no view at all, never a default one")
    void nullLoadout() {
        assertEquals(List.of(), HudLoadoutView.slots(null));
        assertEquals(List.of(), HudLoadoutView.gadgets(null));
        assertNull(HudLoadoutView.slot(null, 1));
        assertNull(HudLoadoutView.slot(loadout, 0));
        assertNull(HudLoadoutView.slot(loadout, 6));
        assertNull(HudLoadoutView.gadget(loadout, 2));
        assertEquals(0f, HudLoadoutView.reloadProgress(null));
        assertEquals("", HudLoadoutView.reloadText(null));
        assertEquals("", HudLoadoutView.quickSwapText(null));
        assertEquals("", HudLoadoutView.heldLabel(null));
    }

    @Test
    @DisplayName("the held label names whatever is in the hands, across all three families")
    void heldLabel() {
        assertEquals(WeaponId.DEFAULT.displayName(), HudLoadoutView.heldLabel(loadout));
        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT).magazineSize(),
            HudLoadoutView.activeMagazineSize(loadout));

        loadout.selectSlot(PlayerLoadout.SLOT_MELEE);
        assertEquals(MeleeId.DEFAULT.displayName(), HudLoadoutView.heldLabel(loadout));
        assertEquals(0, HudLoadoutView.activeMagazineSize(loadout));

        loadout.selectSlot(PlayerLoadout.SLOT_UTILITY_B);
        assertEquals(UtilityId.DEFAULT_SECONDARY.displayName(), HudLoadoutView.heldLabel(loadout));
    }
}
