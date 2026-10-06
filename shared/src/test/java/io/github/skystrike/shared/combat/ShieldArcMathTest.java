package io.github.skystrike.shared.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.combat.ShieldArcMath.Absorption;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.model.ShieldState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shield's front/rear arc resolution (mechanics §7.3). Server authority and client
 * feedback both call exactly this, so the boundaries and the wrap-around behaviour here are
 * the gameplay rules, not an approximation of them.
 */
class ShieldArcMathTest {

    private static final float HALF_FRONT = GadgetConfig.SHIELD_FRONT_ARC_DEGREES / 2f;

    @Test
    @DisplayName("equipped covers the 90° frontal arc inclusive of both edges")
    void equippedFrontalArc() {
        float facing = 0f;
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, 0f));
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, HALF_FRONT));
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, -HALF_FRONT));
        assertFalse(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, HALF_FRONT + 0.1f));
        assertFalse(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, -HALF_FRONT - 0.1f));
        assertFalse(ShieldArcMath.covers(ShieldState.EQUIPPED, facing, 180f), "a back hit passes");
    }

    @Test
    @DisplayName("stowed covers the rear arc and leaves the front open")
    void stowedRearArc() {
        float facing = 0f;
        float halfRear = GadgetConfig.SHIELD_REAR_ARC_DEGREES / 2f;
        assertTrue(ShieldArcMath.covers(ShieldState.STOWED, facing, 180f));
        assertTrue(ShieldArcMath.covers(ShieldState.STOWED, facing, 180f - halfRear));
        assertTrue(ShieldArcMath.covers(ShieldState.STOWED, facing, -(180f - halfRear)));
        assertFalse(ShieldArcMath.covers(ShieldState.STOWED, facing, 180f - halfRear - 0.1f));
        assertFalse(ShieldArcMath.covers(ShieldState.STOWED, facing, 0f), "a front hit passes");
    }

    @Test
    @DisplayName("angle wrapping never flips the answer around ±180 or past 360")
    void wrapping() {
        // Facing +179, hit arriving from -179: only 2° apart across the seam.
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, 179f, -179f));
        // Stowed while facing -179: the rear arc is centred near +1.
        assertTrue(ShieldArcMath.covers(ShieldState.STOWED, -179f, 1f));
        assertFalse(ShieldArcMath.covers(ShieldState.STOWED, -179f, -179f));
        // Inputs beyond 360 reduce to the same answer.
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, 360f + 10f, 10f - HALF_FRONT));
        assertTrue(ShieldArcMath.covers(ShieldState.EQUIPPED, -350f, 10f + HALF_FRONT));
    }

    @Test
    @DisplayName("a broken or absent shield covers nothing from any direction")
    void brokenCoversNothing() {
        for (float incoming = -180f; incoming <= 180f; incoming += 15f) {
            assertFalse(ShieldArcMath.covers(ShieldState.BROKEN, 0f, incoming));
            assertFalse(ShieldArcMath.covers(null, 0f, incoming));
        }
        assertFalse(ShieldState.BROKEN.absorbs());
        assertFalse(ShieldState.BROKEN.handgunOnly(), "a broken shield restricts nothing");
        assertTrue(ShieldState.EQUIPPED.handgunOnly());
        assertFalse(ShieldState.STOWED.handgunOnly());
    }

    @Test
    @DisplayName("absorption drains durability instead of health, overflow passes through")
    void absorptionArithmetic() {
        Absorption clean = ShieldArcMath.absorb(40f, 150f);
        assertEquals(40f, clean.absorbed());
        assertEquals(0f, clean.healthDamage());
        assertEquals(110f, clean.durabilityAfter());
        assertFalse(clean.broke());
        assertTrue(clean.absorbedAnything());

        Absorption breaking = ShieldArcMath.absorb(60f, 50f);
        assertEquals(50f, breaking.absorbed());
        assertEquals(10f, breaking.healthDamage(), "overflow continues to health");
        assertEquals(0f, breaking.durabilityAfter());
        assertTrue(breaking.broke());

        Absorption exact = ShieldArcMath.absorb(50f, 50f);
        assertEquals(50f, exact.absorbed());
        assertEquals(0f, exact.healthDamage());
        assertTrue(exact.broke(), "emptying the pool exactly still breaks it");

        Absorption empty = ShieldArcMath.absorb(60f, 0f);
        assertEquals(0f, empty.absorbed(), "an empty pool absorbs nothing");
        assertEquals(60f, empty.healthDamage());
        assertFalse(empty.broke(), "nothing was left to break");

        Absorption nothing = ShieldArcMath.absorb(0f, 80f);
        assertEquals(0f, nothing.absorbed());
        assertEquals(0f, nothing.healthDamage());
        assertEquals(80f, nothing.durabilityAfter());
    }

    @Test
    @DisplayName("resolve is the arc test and the split in one deterministic call")
    void resolveCombines() {
        // Covered: equipped, hit from dead ahead.
        Absorption covered = ShieldArcMath.resolve(ShieldState.EQUIPPED, 0f, 0f, 70f, 150f);
        assertEquals(70f, covered.absorbed());
        assertEquals(80f, covered.durabilityAfter());

        // Outside the arc: untouched.
        Absorption missed = ShieldArcMath.resolve(ShieldState.EQUIPPED, 0f, 180f, 70f, 150f);
        assertEquals(0f, missed.absorbed());
        assertEquals(70f, missed.healthDamage());
        assertEquals(150f, missed.durabilityAfter());

        // Broken: untouched even from the protected direction.
        Absorption broken = ShieldArcMath.resolve(ShieldState.BROKEN, 0f, 0f, 70f, 0f);
        assertEquals(0f, broken.absorbed());
        assertEquals(70f, broken.healthDamage());
    }
}
