package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The live state of one Q/E slot: equip, durability, breakage, cooldown and — the part a later
 * bug would be subtlest in — the respawn reset semantics of mechanics §10.
 */
class GadgetSlotTest {

    @Test
    @DisplayName("a fresh slot is empty, unusable and harmlessly NONE")
    void emptyByDefault() {
        GadgetSlot slot = new GadgetSlot();
        assertTrue(slot.isEmpty());
        assertFalse(slot.isUsable());
        assertSame(GadgetId.NONE, slot.gadgetId());
        assertEquals(0f, slot.durability);
        assertFalse(slot.holds(GadgetId.NONE), "holds() answers for real gadgets only");
    }

    @Test
    @DisplayName("equipping pulls the registry durability and clears every flag")
    void equipIsFresh() {
        GadgetSlot slot = new GadgetSlot();
        slot.active = true;
        slot.broken = true;
        slot.cooldownRemaining = 2f;

        slot.equip(GadgetId.SHIELD);
        assertTrue(slot.holds(GadgetId.SHIELD));
        assertTrue(slot.isUsable());
        assertEquals(GadgetRegistry.of(GadgetId.SHIELD).maxDurability(), slot.durability);
        assertFalse(slot.active);
        assertFalse(slot.broken);
        assertEquals(0f, slot.cooldownRemaining);

        // The fuel tank has no pool; it is usable while worn and intact all the same.
        slot.equip(GadgetId.FUEL_TANK);
        assertEquals(0f, slot.durability);
        assertTrue(slot.isUsable());

        slot.equip(null);
        assertTrue(slot.isEmpty());
    }

    @Test
    @DisplayName("durability damage breaks the gadget at zero, permanently and inactively")
    void breakageAtZero() {
        GadgetSlot slot = new GadgetSlot(GadgetId.DRONE); // 30 HP
        slot.active = true;

        assertFalse(slot.applyDurabilityDamage(12f));
        assertEquals(18f, slot.durability, 1e-4f);
        assertTrue(slot.isUsable());

        assertTrue(slot.applyDurabilityDamage(50f), "the breaking hit reports true");
        assertEquals(0f, slot.durability);
        assertTrue(slot.broken);
        assertFalse(slot.active, "a destroyed gadget cannot stay engaged");
        assertFalse(slot.isUsable());

        assertFalse(slot.applyDurabilityDamage(10f), "a broken gadget takes no further damage");
        assertSame(ShieldState.BROKEN, slot.shieldState());
    }

    @Test
    @DisplayName("damage is ignored on empty slots and non-positive amounts")
    void damageGuards() {
        GadgetSlot empty = new GadgetSlot();
        assertFalse(empty.applyDurabilityDamage(10f));

        GadgetSlot shield = new GadgetSlot(GadgetId.SHIELD);
        assertFalse(shield.applyDurabilityDamage(0f));
        assertFalse(shield.applyDurabilityDamage(-5f));
        assertEquals(150f, shield.durability);
    }

    @Test
    @DisplayName("the shield vocabulary follows the flags: stowed, equipped, broken")
    void shieldStateView() {
        GadgetSlot slot = new GadgetSlot(GadgetId.SHIELD);
        assertSame(ShieldState.STOWED, slot.shieldState());
        slot.active = true;
        assertSame(ShieldState.EQUIPPED, slot.shieldState());
        slot.broken = true;
        assertSame(ShieldState.BROKEN, slot.shieldState(), "broken outranks active");
    }

    @Test
    @DisplayName("respawn keeps the chosen gadget and clears its state (mechanics §10)")
    void respawnReset() {
        GadgetSlot slot = new GadgetSlot(GadgetId.SHIELD);
        slot.applyDurabilityDamage(150f);
        slot.cooldownRemaining = 3f;
        assertTrue(slot.broken);

        slot.resetForRespawn();
        assertTrue(slot.holds(GadgetId.SHIELD), "a death does not steal a chosen gadget");
        assertEquals(150f, slot.durability);
        assertFalse(slot.broken, "a new life un-breaks the shield");
        assertFalse(slot.active);
        assertEquals(0f, slot.cooldownRemaining);

        GadgetSlot empty = new GadgetSlot();
        empty.resetForRespawn();
        assertTrue(empty.isEmpty(), "an empty slot stays empty");
    }

    @Test
    @DisplayName("the cooldown ticks down to zero and never below")
    void cooldownTicks() {
        GadgetSlot slot = new GadgetSlot(GadgetId.DRONE);
        slot.cooldownRemaining = 1.0f;
        slot.tickCooldown(0.4f);
        assertEquals(0.6f, slot.cooldownRemaining, 1e-4f);
        slot.tickCooldown(-1f);
        assertEquals(0.6f, slot.cooldownRemaining, 1e-4f, "negative dt is ignored");
        slot.tickCooldown(5f);
        assertEquals(0f, slot.cooldownRemaining);
    }

    @Test
    @DisplayName("copies are deep and defensive decoding falls back to NONE")
    void copyAndDefensiveDecode() {
        GadgetSlot original = new GadgetSlot(GadgetId.CAMERA);
        original.active = true;
        original.durability = 7f;
        original.cooldownRemaining = 0.5f;

        GadgetSlot copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original.gadget, copy.gadget);
        assertEquals(original.durability, copy.durability);
        assertEquals(original.active, copy.active);
        assertEquals(original.cooldownRemaining, copy.cooldownRemaining);

        copy.applyDurabilityDamage(7f);
        assertEquals(7f, original.durability, "state must not alias between copies");

        // A hostile wire value reads as an empty slot rather than throwing.
        GadgetSlot hostile = new GadgetSlot();
        hostile.gadget = 999;
        assertSame(GadgetId.NONE, hostile.gadgetId());
        assertTrue(hostile.isEmpty());
    }
}
