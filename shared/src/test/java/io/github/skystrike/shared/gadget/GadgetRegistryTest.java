package io.github.skystrike.shared.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The gadget identity and the registry behind it: ordinals and wire ids are protocol, every
 * real gadget has exactly one definition, and the documented numbers survived transcription.
 */
class GadgetRegistryTest {

    @Test
    @DisplayName("gadget ordinals are frozen in the structure plan's documented order")
    void ordinalsArePinned() {
        // The wire carries plain ordinals, so this order can never change: append only.
        assertEquals(0, GadgetId.NONE.ordinal());
        assertEquals(1, GadgetId.DRONE.ordinal());
        assertEquals(2, GadgetId.SHIELD.ordinal());
        assertEquals(3, GadgetId.FUEL_TANK.ordinal());
        assertEquals(4, GadgetId.CAMERA.ordinal());
        assertEquals(5, GadgetId.values().length, "append only; never insert, reorder or remove");
    }

    @Test
    @DisplayName("the gadget wire range starts at 3000 and overlaps nothing")
    void wireRangeIsDistinct() {
        assertEquals(3000, GadgetId.WIRE_ID_BASE);
        assertEquals(3001, GadgetId.DRONE.wireId());
        assertEquals(3004, GadgetId.CAMERA.wireId());

        // Guns are < 1000, melee 1000+, utilities 2000+: the ranges never collide.
        assertTrue(GadgetId.WIRE_ID_BASE > UtilityId.WIRE_ID_BASE + UtilityId.values().length);
        assertFalse(GadgetId.isGadgetWireId(MeleeId.DEFAULT.wireId()));
        assertFalse(GadgetId.isGadgetWireId(UtilityId.FRAG.wireId()));
        assertFalse(UtilityId.isUtilityWireId(GadgetId.DRONE.wireId()));

        assertSame(GadgetId.SHIELD, GadgetId.fromWireId(GadgetId.SHIELD.wireId()));
        assertNull(GadgetId.fromWireId(UtilityId.FRAG.wireId()));
        assertNull(GadgetId.fromWireId(-1));
    }

    @Test
    @DisplayName("activation behaviours match mechanics §7: manual, manual, hybrid, passive")
    void behaviorsMatchThePlan() {
        assertSame(GadgetBehavior.MANUAL, GadgetId.DRONE.behavior());
        assertSame(GadgetBehavior.MANUAL, GadgetId.CAMERA.behavior());
        assertSame(GadgetBehavior.HYBRID, GadgetId.SHIELD.behavior());
        assertSame(GadgetBehavior.PASSIVE, GadgetId.FUEL_TANK.behavior());

        assertTrue(GadgetBehavior.MANUAL.respondsToPress());
        assertTrue(GadgetBehavior.HYBRID.respondsToPress());
        assertFalse(GadgetBehavior.PASSIVE.respondsToPress());
        assertTrue(GadgetBehavior.PASSIVE.hasPassiveEffect());
        assertTrue(GadgetBehavior.HYBRID.hasPassiveEffect());
        assertFalse(GadgetBehavior.MANUAL.hasPassiveEffect());

        // NONE is inert: permanently passive, never real.
        assertSame(GadgetBehavior.PASSIVE, GadgetId.NONE.behavior());
        assertFalse(GadgetId.NONE.isReal());
    }

    @Test
    @DisplayName("every real gadget has a definition, in enum order; NONE has none")
    void registryIsComplete() {
        List<GadgetDefinition> all = GadgetRegistry.all();
        assertEquals(GadgetId.values().length - 1, all.size());
        int index = 0;
        for (GadgetId id : GadgetId.values()) {
            if (!id.isReal()) {
                continue;
            }
            assertSame(id, all.get(index).id(), "all() must follow enum order");
            assertSame(all.get(index), GadgetRegistry.of(id), "records are shared, not copied");
            index++;
        }
        assertThrows(IllegalArgumentException.class, () -> GadgetRegistry.of(GadgetId.NONE));
        assertThrows(IllegalArgumentException.class, () -> GadgetRegistry.ofOrdinal(99));
        assertNull(GadgetRegistry.ofOrdinalOrNull(GadgetId.NONE.ordinal()));
        assertNull(GadgetRegistry.ofOrdinalOrNull(-5));
        assertSame(GadgetRegistry.of(GadgetId.DRONE),
            GadgetRegistry.ofOrdinalOrNull(GadgetId.DRONE.ordinal()));
    }

    @Test
    @DisplayName("documented durabilities survived transcription: 30, 150, no pool, 20")
    void durabilitiesMatchThePlan() {
        assertEquals(30f, GadgetRegistry.of(GadgetId.DRONE).maxDurability());
        assertEquals(150f, GadgetRegistry.of(GadgetId.SHIELD).maxDurability());
        assertEquals(20f, GadgetRegistry.of(GadgetId.CAMERA).maxDurability());

        // The fuel tank is not whittled down: one rear hit detonates it, so it has no pool.
        assertEquals(0f, GadgetRegistry.of(GadgetId.FUEL_TANK).maxDurability());
        assertFalse(GadgetRegistry.of(GadgetId.FUEL_TANK).hasDurabilityPool());
        assertTrue(GadgetRegistry.of(GadgetId.SHIELD).hasDurabilityPool());

        assertEquals(GadgetConfig.DRONE_HEALTH, GadgetRegistry.maxDurability(GadgetId.DRONE));
        assertEquals(0f, GadgetRegistry.maxDurability(GadgetId.NONE));
        assertEquals(0f, GadgetRegistry.maxDurability(null));
    }

    @Test
    @DisplayName("drones and cameras spawn world entities; shield and tank are worn")
    void entitySplitMatchesThePlan() {
        assertTrue(GadgetRegistry.of(GadgetId.DRONE).spawnsEntity());
        assertTrue(GadgetRegistry.of(GadgetId.CAMERA).spawnsEntity());
        assertFalse(GadgetRegistry.of(GadgetId.SHIELD).spawnsEntity());
        assertFalse(GadgetRegistry.of(GadgetId.FUEL_TANK).spawnsEntity());
    }

    @Test
    @DisplayName("every mechanics-§7 number is specified: nothing in the registry is provisional")
    void nothingIsGuessed() {
        for (GadgetDefinition definition : GadgetRegistry.all()) {
            assertFalse(definition.provisional(),
                definition.id() + " is marked provisional but §7 specifies its numbers");
        }
    }

    @Test
    @DisplayName("a definition rejects NONE, null and negative durability")
    void definitionValidates() {
        assertThrows(IllegalArgumentException.class,
            () -> new GadgetDefinition(GadgetId.NONE, 10f, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> new GadgetDefinition(null, 10f, false, false));
        assertThrows(IllegalArgumentException.class,
            () -> new GadgetDefinition(GadgetId.DRONE, -1f, true, false));
    }

    @Test
    @DisplayName("ordinal decoding: strict decode throws, defensive decode falls back to NONE")
    void ordinalDecoding() {
        assertTrue(GadgetId.isValidOrdinal(0));
        assertTrue(GadgetId.isValidOrdinal(GadgetId.values().length - 1));
        assertFalse(GadgetId.isValidOrdinal(-1));
        assertFalse(GadgetId.isValidOrdinal(GadgetId.values().length));

        assertSame(GadgetId.FUEL_TANK, GadgetId.fromOrdinal(3));
        assertThrows(IllegalArgumentException.class, () -> GadgetId.fromOrdinal(-1));
        assertSame(GadgetId.NONE, GadgetId.fromOrdinalOrNone(-1));
        assertSame(GadgetId.NONE, GadgetId.fromOrdinalOrNone(99));
        assertSame(GadgetId.CAMERA, GadgetId.fromOrdinalOrNone(4));
    }

    @Test
    @DisplayName("display names reach the kill feed through the wire id")
    void displayNames() {
        assertEquals("Surveillance Drone", GadgetRegistry.displayNameForWireId(GadgetId.DRONE.wireId()));
        assertEquals("Fuel Tank", GadgetRegistry.displayNameForWireId(GadgetId.FUEL_TANK.wireId()));
        assertEquals("", GadgetRegistry.displayNameForWireId(UtilityId.FRAG.wireId()));
    }
}
