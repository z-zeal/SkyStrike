package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The melee table of mechanics §5.2, asserted against the plan rather than itself.
 */
class MeleeRegistryTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("all four melee weapons are defined")
    void tableIsComplete() {
        assertEquals(4, MeleeId.values().length);
        assertEquals(MeleeId.values().length, MeleeRegistry.all().size());
        for (MeleeId id : MeleeId.values()) {
            MeleeDefinition definition = MeleeRegistry.of(id);
            assertEquals(id, definition.id());
            assertTrue(definition.damage() > 0f, id + " does no damage");
            assertTrue(definition.swingsPerSecond() > 0f, id + " cannot swing");
            assertTrue(definition.range() > 0f, id + " cannot reach");
            assertTrue(definition.knockback() > 0f, id + " cannot launch anything");
        }
    }

    @Test
    @DisplayName("damage, cadence, reach and knockback match the mechanics table")
    void matchesTheMechanicsTable() {
        assertMelee(MeleeId.COMBAT_KNIFE, 50f, 2.0f, 60f, 200f);
        assertMelee(MeleeId.SHOVEL, 65f, 1.667f, 70f, 250f);
        assertMelee(MeleeId.BASEBALL_BAT, 65f, 1.5f, 80f, 350f);
        assertMelee(MeleeId.KATANA, 75f, 2.5f, 90f, 150f);
    }

    private static void assertMelee(MeleeId id, float damage, float rate, float range, float knockback) {
        MeleeDefinition definition = MeleeRegistry.of(id);
        assertEquals(damage, definition.damage(), EPSILON, id + " damage");
        assertEquals(rate, definition.swingsPerSecond(), EPSILON, id + " swings per second");
        assertEquals(range, definition.range(), EPSILON, id + " range");
        assertEquals(knockback, definition.knockback(), EPSILON, id + " knockback");
    }

    @Test
    @DisplayName("the bat launches hardest and the knife is fastest")
    void balanceIntent() {
        assertTrue(MeleeRegistry.of(MeleeId.BASEBALL_BAT).knockback()
            > MeleeRegistry.of(MeleeId.SHOVEL).knockback());
        assertTrue(MeleeRegistry.of(MeleeId.COMBAT_KNIFE).swingsPerSecond()
            >= MeleeRegistry.of(MeleeId.SHOVEL).swingsPerSecond());
        assertTrue(MeleeRegistry.of(MeleeId.KATANA).range()
            >= MeleeRegistry.of(MeleeId.COMBAT_KNIFE).range());
    }

    @Test
    @DisplayName("swing cooldown is the inverse of the quoted cadence")
    void swingCooldownIsTheInverseOfRate() {
        for (MeleeId id : MeleeId.values()) {
            MeleeDefinition definition = MeleeRegistry.of(id);
            assertEquals(1f / definition.swingsPerSecond(), definition.swingCooldownSeconds(), EPSILON);
        }
        assertEquals(0.5f, MeleeRegistry.of(MeleeId.COMBAT_KNIFE).swingCooldownSeconds(), EPSILON);
    }

    @Test
    @DisplayName("lookups return copies, never the shared instance")
    void lookupsReturnCopies() {
        MeleeDefinition first = MeleeRegistry.of(MeleeId.KATANA);
        MeleeDefinition second = MeleeRegistry.of(MeleeId.KATANA);
        assertNotSame(first, second);
        assertEquals(first, second);
    }
}
