package io.github.skystrike.shared.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the three properties the rest of combat is built on: rounds travel, they drop, and
 * they hurt less the further they have flown.
 */
class BallisticsMathTest {

    private static final float EPSILON = 1e-3f;

    @Test
    @DisplayName("the gravity ramp starts at zero, ends at one, and is smooth in between")
    void gravityRampSmoothstep() {
        assertEquals(0f, BallisticsMath.gravityRampFactor(0f, 1f), EPSILON);
        assertEquals(0.5f, BallisticsMath.gravityRampFactor(0.5f, 1f), EPSILON);
        assertEquals(1f, BallisticsMath.gravityRampFactor(1f, 1f), EPSILON);
        assertEquals(1f, BallisticsMath.gravityRampFactor(5f, 1f), EPSILON);

        // Smoothstep, not linear: a quarter of the way in, less than a quarter of the drop.
        assertTrue(BallisticsMath.gravityRampFactor(0.25f, 1f) < 0.25f);
        assertTrue(BallisticsMath.gravityRampFactor(0.75f, 1f) > 0.75f);
    }

    @Test
    @DisplayName("drag is quoted per tick and applies the same bleed per second at any step size")
    void dragIsFramerateIndependent() {
        float drag = 0.99f;
        float oneTick = 1f / WorldConfig.TICK_RATE_HZ;

        assertEquals(drag, BallisticsMath.dragDecay(drag, oneTick), EPSILON);

        // Two half-ticks must bleed exactly as much as one whole tick.
        float half = BallisticsMath.dragDecay(drag, oneTick / 2f);
        assertEquals(drag, half * half, EPSILON);

        assertEquals(1f, BallisticsMath.dragDecay(drag, 0f), EPSILON);
        assertEquals(1f, BallisticsMath.dragDecay(1f, oneTick), EPSILON);
    }

    @Test
    @DisplayName("damage falls off linearly to the weapon floor and never below it")
    void damageFalloffIsLinearToTheFloor() {
        assertEquals(1f, BallisticsMath.damageRatio(0f, 1000f, 0.5f), EPSILON);
        assertEquals(0.75f, BallisticsMath.damageRatio(500f, 1000f, 0.5f), EPSILON);
        assertEquals(0.5f, BallisticsMath.damageRatio(1000f, 1000f, 0.5f), EPSILON);
        assertEquals(0.5f, BallisticsMath.damageRatio(9999f, 1000f, 0.5f), EPSILON);
    }

    @Test
    @DisplayName("a Deagle at maximum range does its floor damage, not its muzzle damage")
    void falloffUsesTheWeaponTable() {
        WeaponBallistics deagle = WeaponBallistics.of(WeaponId.DESERT_EAGLE);
        float muzzle = BallisticsMath.damageAfterFalloff(60f, 0f, deagle);
        float far = BallisticsMath.damageAfterFalloff(60f, deagle.maxRange(), deagle);

        assertEquals(60f, muzzle, EPSILON);
        assertEquals(60f * 0.55f, far, EPSILON);
        assertTrue(far < muzzle * 0.6f, "a full-range pistol shot must hurt noticeably less");
    }

    @Test
    @DisplayName("every gun in the table is fast enough to need sweeping")
    void everyWeaponRequiresSweeping() {
        for (WeaponId id : WeaponId.values()) {
            float speed = WeaponBallistics.of(id).muzzleSpeed();
            assertTrue(
                BallisticsMath.requiresSweep(speed),
                id + " at " + speed + " u/s must be swept, not point-tested");
        }
        assertFalse(BallisticsMath.requiresSweep(CombatConfig.SWEEP_SPEED_THRESHOLD));
    }

    @Test
    @DisplayName("a round fired flat drops, slows and records the path it actually flew")
    void stepIntegratesDropDragAndPath() {
        WeaponBallistics scar = WeaponBallistics.of(WeaponId.SCAR_L);
        Projectile round = new Projectile(
            1, 1, 0, WeaponId.SCAR_L.ordinal(), 0f, 500f,
            scar.muzzleSpeed(), 0f);

        float dt = 1f / WorldConfig.TICK_RATE_HZ;
        for (int i = 0; i < 30; i++) {
            BallisticsMath.step(round, scar, dt);
        }

        assertTrue(round.x > 0f, "the round must travel forward");
        assertTrue(round.y < 500f, "the round must drop");
        assertTrue(round.vx < scar.muzzleSpeed(), "drag must bleed speed");
        assertTrue(round.vy < 0f, "gravity must build downward velocity");
        assertEquals(0.5f, round.age, EPSILON);

        // distanceTravelled is path length, so it is at least the horizontal displacement.
        assertTrue(round.distanceTravelled >= round.x - EPSILON);
        assertTrue(round.prevX < round.x, "the previous position must trail the current one");
    }

    @Test
    @DisplayName("drop is invisible up close and obvious across the arena")
    void dropRampsInWithDistance() {
        WeaponBallistics scar = WeaponBallistics.of(WeaponId.SCAR_L);
        float dt = 1f / WorldConfig.TICK_RATE_HZ;

        Projectile round = new Projectile(
            1, 1, 0, WeaponId.SCAR_L.ordinal(), 0f, 1000f, scar.muzzleSpeed(), 0f);

        float dropAtHalfRange = 0f;
        while (round.distanceTravelled < scar.maxRange() && round.age < 2f) {
            BallisticsMath.step(round, scar, dt);
            if (dropAtHalfRange == 0f && round.distanceTravelled >= scar.maxRange() / 2f) {
                dropAtHalfRange = 1000f - round.y;
            }
        }
        float dropAtFullRange = 1000f - round.y;

        assertTrue(dropAtHalfRange < 2f, "half range should look flat, was " + dropAtHalfRange);
        assertTrue(dropAtFullRange > dropAtHalfRange * 3f,
            "drop must accelerate: half " + dropAtHalfRange + " full " + dropAtFullRange);
    }

    @Test
    @DisplayName("rounds expire on time, on energy, or on overshooting their range")
    void expiryRules() {
        WeaponBallistics smg = WeaponBallistics.of(WeaponId.SMG);
        Projectile fresh = new Projectile(1, 1, 0, WeaponId.SMG.ordinal(), 0f, 0f, 1000f, 0f);
        assertFalse(BallisticsMath.isExpired(fresh, smg));

        Projectile old = new Projectile(2, 1, 0, WeaponId.SMG.ordinal(), 0f, 0f, 1000f, 0f);
        old.age = CombatConfig.MAX_PROJECTILE_LIFETIME;
        assertTrue(BallisticsMath.isExpired(old, smg));

        Projectile slow = new Projectile(3, 1, 0, WeaponId.SMG.ordinal(), 0f, 0f, 10f, 0f);
        assertTrue(BallisticsMath.isExpired(slow, smg));

        Projectile farGone = new Projectile(4, 1, 0, WeaponId.SMG.ordinal(), 0f, 0f, 1000f, 0f);
        farGone.distanceTravelled = smg.maxRange() * CombatConfig.MAX_RANGE_OVERSHOOT;
        assertTrue(BallisticsMath.isExpired(farGone, smg));
    }

    @Test
    @DisplayName("muzzle velocity points along the aim")
    void muzzleVelocityComponents() {
        assertEquals(1000f, BallisticsMath.muzzleVelocityX(0f, 1000f), 0.01f);
        assertEquals(0f, BallisticsMath.muzzleVelocityY(0f, 1000f), 0.01f);
        assertEquals(0f, BallisticsMath.muzzleVelocityX(90f, 1000f), 0.01f);
        assertEquals(1000f, BallisticsMath.muzzleVelocityY(90f, 1000f), 0.01f);
        assertEquals(-1000f, BallisticsMath.muzzleVelocityY(-90f, 1000f), 0.01f);
    }
}
