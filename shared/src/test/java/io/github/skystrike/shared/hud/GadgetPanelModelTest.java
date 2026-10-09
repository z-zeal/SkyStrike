package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the gadget panel's vocabulary and numbers to the loadout they are drawn from: the panel
 * is a pure function of {@link PlayerLoadout}, so every word and every count asserted here is a
 * promise the widget cannot break.
 */
class GadgetPanelModelTest {

    private PlayerLoadout loadout;

    @BeforeEach
    void setUp() {
        loadout = new PlayerLoadout();
    }

    @Test
    @DisplayName("a default loadout shows no panel and two EMPTY slots")
    void emptyLoadout() {
        assertFalse(GadgetPanelModel.visible(loadout));
        assertFalse(GadgetPanelModel.visible(null));

        List<GadgetPanelModel.SlotPanel> slots = GadgetPanelModel.slots(loadout);
        assertEquals(2, slots.size());
        for (GadgetPanelModel.SlotPanel slot : slots) {
            assertEquals(GadgetId.NONE, slot.gadget());
            assertEquals("Empty", slot.name());
            assertFalse(slot.equipped());
            assertEquals("EMPTY", slot.stateText());
            assertEquals("", slot.durabilityText());
            assertEquals("", slot.cooldownText(), "an empty slot prints no cooldown word");
            assertFalse(slot.hasDurabilityPool());
            assertFalse(slot.onCooldown());
        }
        assertEquals("Q", slots.get(0).key());
        assertEquals("E", slots.get(1).key());
        assertNull(GadgetPanelModel.slot(null, 0));
        assertNull(GadgetPanelModel.slot(loadout, 2));
    }

    @Test
    @DisplayName("a fresh shield reads 150/150, REAR, READY")
    void freshShield() {
        loadout.setGadgets(GadgetId.SHIELD, null);
        assertTrue(GadgetPanelModel.visible(loadout));

        GadgetPanelModel.SlotPanel shield = GadgetPanelModel.slot(loadout, 0);
        assertEquals(GadgetId.SHIELD, shield.gadget());
        assertEquals(GadgetId.SHIELD.displayName(), shield.name());
        assertTrue(shield.equipped());
        assertTrue(shield.hasDurabilityPool());
        assertEquals(GadgetConfig.SHIELD_DURABILITY, shield.maxDurability());
        assertEquals(1f, shield.durabilityFraction());
        assertEquals("150/150", shield.durabilityText());
        assertEquals("REAR", shield.stateText(), "a stowed shield protects the rear");
        assertEquals("READY", shield.cooldownText());
    }

    @Test
    @DisplayName("toggling the shield flips the panel between FRONT and REAR")
    void shieldToggle() {
        loadout.setGadgets(GadgetId.SHIELD, null);
        loadout.toggleGadget(0);
        assertEquals("FRONT", GadgetPanelModel.slot(loadout, 0).stateText());
        loadout.toggleGadget(0);
        assertEquals("REAR", GadgetPanelModel.slot(loadout, 0).stateText());
    }

    @Test
    @DisplayName("shield wear counts down and breaking says BROKEN with no cooldown word")
    void shieldWearAndBreak() {
        loadout.setGadgets(GadgetId.SHIELD, null);
        loadout.gadgetQ.applyDurabilityDamage(38f);
        GadgetPanelModel.SlotPanel worn = GadgetPanelModel.slot(loadout, 0);
        assertEquals("112/150", worn.durabilityText());
        assertEquals(112f / 150f, worn.durabilityFraction(), 1e-4f);

        loadout.gadgetQ.applyDurabilityDamage(200f);
        GadgetPanelModel.SlotPanel broken = GadgetPanelModel.slot(loadout, 0);
        assertTrue(broken.broken());
        assertEquals("BROKEN", broken.stateText());
        assertEquals("0/150", broken.durabilityText());
        assertEquals(0f, broken.durabilityFraction());
        assertEquals("", broken.cooldownText(), "a broken gadget prints no cooldown word");
        assertFalse(broken.onCooldown());
    }

    @Test
    @DisplayName("the drone mirrors its device health and says DEPLOYED or DESTROYED")
    void droneStates() {
        loadout.setGadgets(null, GadgetId.DRONE);
        GadgetPanelModel.SlotPanel drone = GadgetPanelModel.slot(loadout, 1);
        assertEquals(GadgetConfig.DRONE_HEALTH, drone.maxDurability());
        assertEquals("30/30", drone.durabilityText());
        assertEquals("READY", drone.stateText(), "an undeployed manual gadget waits on its key");
        assertEquals("READY", drone.cooldownText());

        loadout.gadgetE.active = true;
        assertEquals("DEPLOYED", GadgetPanelModel.slot(loadout, 1).stateText());

        // The server mirrors device health into the slot; half health reads half.
        loadout.gadgetE.durability = GadgetConfig.DRONE_HEALTH / 2f;
        assertEquals("15/30", GadgetPanelModel.slot(loadout, 1).durabilityText());

        loadout.gadgetE.broken = true;
        loadout.gadgetE.durability = 0f;
        assertEquals("DESTROYED", GadgetPanelModel.slot(loadout, 1).stateText());
    }

    @Test
    @DisplayName("the camera reports its own 20-point pool")
    void cameraPool() {
        loadout.setGadgets(null, GadgetId.CAMERA);
        GadgetPanelModel.SlotPanel camera = GadgetPanelModel.slot(loadout, 1);
        assertEquals(GadgetConfig.CAMERA_HEALTH, camera.maxDurability());
        assertEquals("20/20", camera.durabilityText());
        assertEquals("READY", camera.stateText());
    }

    @Test
    @DisplayName("the fuel tank has no pool, says WORN, and never says READY")
    void fuelTank() {
        loadout.setGadgets(GadgetId.FUEL_TANK, null);
        GadgetPanelModel.SlotPanel tank = GadgetPanelModel.slot(loadout, 0);
        assertFalse(tank.hasDurabilityPool());
        assertEquals(0f, tank.maxDurability());
        assertEquals("", tank.durabilityText(), "no pool, no numbers");
        assertEquals("WORN", tank.stateText());
        assertEquals("", tank.cooldownText(), "a passive gadget is never 'ready'");

        loadout.gadgetQ.broken = true;
        assertEquals("DETONATED", GadgetPanelModel.slot(loadout, 0).stateText());
    }

    @Test
    @DisplayName("a running cooldown prints its remaining seconds to one decimal")
    void cooldownText() {
        loadout.setGadgets(GadgetId.SHIELD, null);
        loadout.gadgetQ.cooldownRemaining = 1.24f;
        GadgetPanelModel.SlotPanel slot = GadgetPanelModel.slot(loadout, 0);
        assertTrue(slot.onCooldown());
        assertEquals("1.2s", slot.cooldownText());

        loadout.gadgetQ.cooldownRemaining = 0f;
        assertEquals("READY", GadgetPanelModel.slot(loadout, 0).cooldownText());
    }

    @Test
    @DisplayName("durability and cooldown never read negative, whatever the wire says")
    void clampedNegatives() {
        loadout.setGadgets(GadgetId.SHIELD, null);
        loadout.gadgetQ.durability = -5f;
        loadout.gadgetQ.cooldownRemaining = -1f;
        GadgetPanelModel.SlotPanel slot = GadgetPanelModel.slot(loadout, 0);
        assertEquals(0f, slot.durability());
        assertEquals(0f, slot.cooldownRemaining());
        assertEquals(0f, slot.durabilityFraction());
    }
}
