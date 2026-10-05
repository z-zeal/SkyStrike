package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The melee table of mechanics §5.2, generated from the sprite catalog: completeness over all
 * 21 weapons, flagship rows verbatim, the reach/cadence/knockback identities, and the copy
 * contract shared with {@link WeaponRegistry}.
 */
class MeleeRegistryTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("every melee weapon of the catalog is defined and plausible")
    void tableIsComplete() {
        assertEquals(21, MeleeId.values().length);
        assertEquals(MeleeId.values().length, MeleeRegistry.all().size());
        for (MeleeId id : MeleeId.values()) {
            MeleeDefinition definition = MeleeRegistry.of(id);
            assertNotNull(definition);
            assertEquals(id, definition.id());
            assertTrue(definition.damage() > 0f, id + " does no damage");
            assertTrue(definition.swingsPerSecond() > 0f, id + " never swings");
            assertTrue(definition.range() > 0f, id + " has no reach");
            assertTrue(definition.knockback() > 0f, id + " has no knockback");
        }
    }

    @Test
    @DisplayName("flagship rows match the generated conversion of the catalog stats")
    void matchesTheGeneratedTable() {
        assertMelee(MeleeId.TRENCH_KNUCKLE, 45f, 1.5f, 64f, 290f);
        assertMelee(MeleeId.WINTER_KATANA, 76f, 1.0667f, 78f, 190f);
        assertMelee(MeleeId.MILL_ZWEI, 106f, 0.6667f, 89f, 330f);
        assertMelee(MeleeId.FROST_NAGINATA, 81f, 0.8333f, 103f, 210f);
        assertMelee(MeleeId.POCKET_THORN, 34f, 1.8333f, 61f, 140f);
    }

    private void assertMelee(MeleeId id, float damage, float swings, float range, float knockback) {
        MeleeDefinition definition = MeleeRegistry.of(id);
        assertEquals(damage, definition.damage(), EPSILON, id + " damage");
        assertEquals(swings, definition.swingsPerSecond(), EPSILON, id + " cadence");
        assertEquals(range, definition.range(), EPSILON, id + " range");
        assertEquals(knockback, definition.knockback(), EPSILON, id + " knockback");
    }

    @Test
    @DisplayName("the class identities hold: polearms reach, knives cycle, heavies shove")
    void classIdentities() {
        // The naginata out-reaches everything; the stiletto out-cycles everything.
        for (MeleeId id : MeleeId.values()) {
            assertTrue(MeleeRegistry.of(MeleeId.FROST_NAGINATA).range()
                >= MeleeRegistry.of(id).range(), id + " out-reaches the naginata");
            assertTrue(MeleeRegistry.of(MeleeId.NIGHT_LETTER).swingsPerSecond()
                >= MeleeRegistry.of(id).swingsPerSecond(), id + " out-cycles the stiletto");
        }
        // Weight shoves: the zweihander launches, the letter opener does not.
        assertTrue(MeleeRegistry.of(MeleeId.MILL_ZWEI).knockback()
            > MeleeRegistry.of(MeleeId.NIGHT_LETTER).knockback() * 2f);
        // Reach ladder: greatsword > katana > knife.
        assertTrue(MeleeRegistry.of(MeleeId.MILL_ZWEI).range()
            > MeleeRegistry.of(MeleeId.WINTER_KATANA).range());
        assertTrue(MeleeRegistry.of(MeleeId.WINTER_KATANA).range()
            > MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE).range());
    }

    @Test
    @DisplayName("no melee weapon one-shots a full-health player from the body")
    void noBodyOneShot() {
        for (MeleeId id : MeleeId.values()) {
            assertTrue(MeleeRegistry.of(id).damage() < 150f, id + " one-shots the body");
        }
    }

    @Test
    @DisplayName("swing cooldown is the inverse of the cadence")
    void swingCooldown() {
        for (MeleeId id : MeleeId.values()) {
            MeleeDefinition definition = MeleeRegistry.of(id);
            assertEquals(1f / definition.swingsPerSecond(), definition.swingCooldownSeconds(), EPSILON);
        }
        assertEquals(1f / 1.5f, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE).swingCooldownSeconds(), EPSILON);
    }

    @Test
    @DisplayName("lookups return copies, never the shared instance")
    void lookupsReturnCopies() {
        MeleeDefinition first = MeleeRegistry.of(MeleeId.WINTER_KATANA);
        MeleeDefinition second = MeleeRegistry.of(MeleeId.WINTER_KATANA);
        assertNotSame(first, second, "two lookups must not hand out the same object");
        assertEquals(first, second, "but they must agree on every number");

        assertEquals(MeleeId.DEFAULT, MeleeRegistry.ofOrdinal(-5).id(), "garbage falls back");
    }
}
