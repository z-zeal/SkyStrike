package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates the shared ballistics table against the ranges the mechanics plan quotes. A typo in
 * one row of the weapon table is otherwise invisible until someone notices a pistol outranging
 * a sniper rifle.
 */
class WeaponBallisticsTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("every weapon has a flight profile")
    void tableIsComplete() {
        assertEquals(WeaponId.values().length, WeaponBallistics.all().size());
        for (WeaponId id : WeaponId.values()) {
            assertNotNull(WeaponBallistics.of(id), id + " has no ballistics");
        }
    }

    @Test
    @DisplayName("every row sits inside the quoted tuning ranges")
    void everyRowIsInRange() {
        for (WeaponId id : WeaponId.values()) {
            WeaponBallistics b = WeaponBallistics.of(id);

            assertTrue(b.muzzleSpeed() >= WeaponConfig.MUZZLE_SPEED_MIN
                    && b.muzzleSpeed() <= WeaponConfig.MUZZLE_SPEED_MAX,
                id + " muzzle speed out of range: " + b.muzzleSpeed());

            assertTrue(b.dragPerTick() >= WeaponConfig.DRAG_MIN
                    && b.dragPerTick() <= WeaponConfig.DRAG_MAX,
                id + " drag out of range: " + b.dragPerTick());

            assertTrue(b.gravityRampSeconds() >= WeaponConfig.GRAVITY_RAMP_MIN_SECONDS
                    && b.gravityRampSeconds() <= WeaponConfig.GRAVITY_RAMP_MAX_SECONDS,
                id + " gravity ramp out of range: " + b.gravityRampSeconds());

            assertTrue(b.minDamageRatio() >= WeaponConfig.DAMAGE_FLOOR_MIN_RATIO
                    && b.minDamageRatio() <= WeaponConfig.DAMAGE_FLOOR_MAX_RATIO,
                id + " damage floor out of range: " + b.minDamageRatio());

            assertTrue(b.maxRange() > 0f, id + " has no range");
            assertTrue(b.terminalGravity() < 0f, id + " must drop, not rise");
        }
    }

    @Test
    @DisplayName("the extremes of the table are the weapons the plan names")
    void extremesMatchThePlan() {
        assertEquals(1950f, WeaponBallistics.of(WeaponId.AWP).muzzleSpeed(), EPSILON);
        assertEquals(900f, WeaponBallistics.of(WeaponId.SAWED_OFF).muzzleSpeed(), EPSILON);
        assertEquals(0.998f, WeaponBallistics.of(WeaponId.AWP).dragPerTick(), EPSILON);
        assertEquals(0.980f, WeaponBallistics.of(WeaponId.SAWED_OFF).dragPerTick(), EPSILON);

        // The falloff floors quoted in the roadmap, weapon by weapon.
        assertEquals(0.35f, WeaponBallistics.of(WeaponId.SAWED_OFF).minDamageRatio(), EPSILON);
        assertEquals(0.42f, WeaponBallistics.of(WeaponId.P90).minDamageRatio(), EPSILON);
        assertEquals(0.55f, WeaponBallistics.of(WeaponId.DESERT_EAGLE).minDamageRatio(), EPSILON);
        assertEquals(0.72f, WeaponBallistics.of(WeaponId.HK417).minDamageRatio(), EPSILON);
        assertEquals(0.75f, WeaponBallistics.of(WeaponId.KAR98K).minDamageRatio(), EPSILON);
        assertEquals(0.85f, WeaponBallistics.of(WeaponId.AWP).minDamageRatio(), EPSILON);
    }

    @Test
    @DisplayName("ranges match the weapon table, and snipers outrange everything")
    void rangesMatchTheWeaponTable() {
        assertEquals(620f, WeaponBallistics.of(WeaponId.DESERT_EAGLE).maxRange(), EPSILON);
        assertEquals(740f, WeaponBallistics.of(WeaponId.FAMAS).maxRange(), EPSILON);
        assertEquals(780f, WeaponBallistics.of(WeaponId.SCAR_L).maxRange(), EPSILON);
        assertEquals(420f, WeaponBallistics.of(WeaponId.P90).maxRange(), EPSILON);
        assertEquals(1200f, WeaponBallistics.of(WeaponId.KAR98K).maxRange(), EPSILON);
        assertEquals(1500f, WeaponBallistics.of(WeaponId.AWP).maxRange(), EPSILON);
        assertEquals(950f, WeaponBallistics.of(WeaponId.HK417).maxRange(), EPSILON);
        assertEquals(180f, WeaponBallistics.of(WeaponId.SAWED_OFF).maxRange(), EPSILON);
        assertEquals(260f, WeaponBallistics.of(WeaponId.SHOTGUN).maxRange(), EPSILON);
        assertEquals(1500f, WeaponBallistics.of(WeaponId.SNIPER_RIFLE).maxRange(), EPSILON);
        assertEquals(450f, WeaponBallistics.of(WeaponId.SMG).maxRange(), EPSILON);

        float shortest = WeaponBallistics.of(WeaponId.SAWED_OFF).maxRange();
        for (WeaponId id : WeaponId.values()) {
            assertTrue(WeaponBallistics.of(id).maxRange() >= shortest);
        }
    }

    @Test
    @DisplayName("drop weight comes from the weapon family, snipers flattest")
    void dropWeightFollowsTheFamily() {
        assertEquals(1.0f, WeaponBallistics.of(WeaponId.AWP).gravityWeight(), EPSILON);
        assertEquals(2.5f, WeaponBallistics.of(WeaponId.SCAR_L).gravityWeight(), EPSILON);
        assertEquals(3.5f, WeaponBallistics.of(WeaponId.P90).gravityWeight(), EPSILON);
        assertEquals(4.0f, WeaponBallistics.of(WeaponId.DESERT_EAGLE).gravityWeight(), EPSILON);
        assertEquals(5.0f, WeaponBallistics.of(WeaponId.SAWED_OFF).gravityWeight(), EPSILON);

        assertEquals(
            -CombatConfig.BULLET_GRAVITY_BASE * 2.5f,
            WeaponBallistics.of(WeaponId.SCAR_L).terminalGravity(),
            EPSILON);
    }

    @Test
    @DisplayName("nonsense rows are rejected at construction")
    void invalidRowsThrow() {
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.RIFLE, 0f, 0.99f, 1f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.RIFLE, 1000f, 1.5f, 1f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.RIFLE, 1000f, 0.99f, 0f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.RIFLE, 1000f, 0.99f, 1f, 0f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.RIFLE, 1000f, 0.99f, 1f, 500f, 0f));
    }

    @Test
    @DisplayName("weapon ids are stable on the wire")
    void weaponIdsAreStable() {
        // Ordinals cross the wire in the input packet; reordering this enum silently rebinds
        // every client's weapon selection.
        assertEquals(0, WeaponId.DESERT_EAGLE.ordinal());
        assertEquals(5, WeaponId.AWP.ordinal());
        assertEquals(12, WeaponId.SMG.ordinal());
        assertEquals(13, WeaponId.values().length);

        assertEquals(WeaponId.AWP, WeaponId.fromOrdinal(5));
        assertEquals(WeaponId.DEFAULT, WeaponId.fromOrdinal(-1));
        assertEquals(WeaponId.DEFAULT, WeaponId.fromOrdinal(99));
        assertTrue(WeaponId.isValidOrdinal(0));
        assertTrue(WeaponId.isValidOrdinal(12));
        assertEquals(false, WeaponId.isValidOrdinal(13));
    }
}
