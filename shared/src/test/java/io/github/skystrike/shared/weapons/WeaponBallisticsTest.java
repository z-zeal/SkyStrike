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
 * The flight table of mechanics §4.2, generated from the sprite catalog: table-wide bounds for
 * all 89 guns, flagship rows verbatim, the record's own validation, and the wire-order freeze —
 * ordinals cross the wire, so the catalog order of the first 89 is part of the protocol.
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
    @DisplayName("every profile sits inside the documented bounds")
    void profilesAreInRange() {
        for (WeaponId id : WeaponId.values()) {
            WeaponBallistics ballistics = WeaponBallistics.of(id);

            assertTrue(ballistics.muzzleSpeed() >= WeaponConfig.MUZZLE_SPEED_MIN
                    && ballistics.muzzleSpeed() <= WeaponConfig.MUZZLE_SPEED_MAX,
                id + " muzzle speed out of range: " + ballistics.muzzleSpeed());
            assertTrue(ballistics.dragPerTick() >= WeaponConfig.DRAG_MIN
                    && ballistics.dragPerTick() <= WeaponConfig.DRAG_MAX,
                id + " drag out of range: " + ballistics.dragPerTick());
            assertTrue(ballistics.gravityRampSeconds() >= WeaponConfig.GRAVITY_RAMP_MIN_SECONDS
                    && ballistics.gravityRampSeconds() <= WeaponConfig.GRAVITY_RAMP_MAX_SECONDS,
                id + " gravity ramp out of range: " + ballistics.gravityRampSeconds());
            assertTrue(ballistics.minDamageRatio() >= WeaponConfig.DAMAGE_FLOOR_MIN_RATIO
                    && ballistics.minDamageRatio() <= WeaponConfig.DAMAGE_FLOOR_MAX_RATIO,
                id + " damage floor out of range: " + ballistics.minDamageRatio());
            assertTrue(ballistics.maxRange() > 0f, id + " has no range");
        }
    }

    @Test
    @DisplayName("flagship rows match the generated conversion")
    void flagshipRows() {
        assertEquals(1820f, WeaponBallistics.of(WeaponId.CATHEDRAL).muzzleSpeed(), EPSILON);
        assertEquals(0.9978f, WeaponBallistics.of(WeaponId.CATHEDRAL).dragPerTick(), EPSILON);
        assertEquals(1890f, WeaponBallistics.of(WeaponId.CATHEDRAL).maxRange(), EPSILON);
        assertEquals(0.80f, WeaponBallistics.of(WeaponId.CATHEDRAL).minDamageRatio(), EPSILON);

        assertEquals(1640f, WeaponBallistics.of(WeaponId.IRON_CARBINE).muzzleSpeed(), EPSILON);
        assertEquals(770f, WeaponBallistics.of(WeaponId.IRON_CARBINE).maxRange(), EPSILON);
        assertEquals(0.70f, WeaponBallistics.of(WeaponId.IRON_CARBINE).minDamageRatio(), EPSILON);

        assertEquals(720f, WeaponBallistics.of(WeaponId.IRON_SIDEARM).muzzleSpeed(), EPSILON);
        assertEquals(392f, WeaponBallistics.of(WeaponId.IRON_SIDEARM).maxRange(), EPSILON);
        assertEquals(0.55f, WeaponBallistics.of(WeaponId.IRON_SIDEARM).minDamageRatio(), EPSILON);

        // The slowest pistol hits the conversion floor; the shortest shotgun nearly does.
        assertEquals(WeaponConfig.MUZZLE_SPEED_MIN,
            WeaponBallistics.of(WeaponId.REDLINE_45).muzzleSpeed(), EPSILON);
        assertEquals(0.35f, WeaponBallistics.of(WeaponId.SHORT_GOSPEL).minDamageRatio(), EPSILON);
        assertEquals(112f, WeaponBallistics.of(WeaponId.SHORT_GOSPEL).maxRange(), EPSILON);
    }

    @Test
    @DisplayName("the shortest-range weapon is a shotgun and the longest is a sniper")
    void rangeOrdering() {
        WeaponId shortest = WeaponId.values()[0];
        WeaponId longest = WeaponId.values()[0];
        for (WeaponId id : WeaponId.values()) {
            if (WeaponBallistics.of(id).maxRange() < WeaponBallistics.of(shortest).maxRange()) {
                shortest = id;
            }
            if (WeaponBallistics.of(id).maxRange() > WeaponBallistics.of(longest).maxRange()) {
                longest = id;
            }
        }
        assertEquals(WeaponClass.SHOTGUN, WeaponBallistics.of(shortest).weaponClass());
        assertEquals(WeaponClass.SNIPER, WeaponBallistics.of(longest).weaponClass());
    }

    @Test
    @DisplayName("gravity weights follow the class ladder of §4.2")
    void gravityWeights() {
        assertEquals(1.0f, WeaponBallistics.of(WeaponId.CATHEDRAL).gravityWeight(), EPSILON);
        assertEquals(1.8f, WeaponBallistics.of(WeaponId.THORN_DMR).gravityWeight(), EPSILON);
        assertEquals(2.5f, WeaponBallistics.of(WeaponId.IRON_CARBINE).gravityWeight(), EPSILON);
        assertEquals(3.5f, WeaponBallistics.of(WeaponId.WASP_NEST).gravityWeight(), EPSILON);
        assertEquals(2.0f, WeaponBallistics.of(WeaponId.IRON_SIDEARM).gravityWeight(), EPSILON);
        assertEquals(5.0f, WeaponBallistics.of(WeaponId.SCATTER_BENCH).gravityWeight(), EPSILON);

        assertEquals(
            -CombatConfig.BULLET_GRAVITY_BASE * 2.5f,
            WeaponBallistics.of(WeaponId.IRON_CARBINE).terminalGravity(),
            EPSILON);
    }

    @Test
    @DisplayName("the record rejects impossible numbers")
    void validation() {
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.ASSAULT_RIFLE, 0f, 0.99f, 1f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.ASSAULT_RIFLE, 1000f, 1.5f, 1f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.ASSAULT_RIFLE, 1000f, 0.99f, 0f, 500f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.ASSAULT_RIFLE, 1000f, 0.99f, 1f, 0f, 0.7f));
        assertThrows(IllegalArgumentException.class,
            () -> new WeaponBallistics(WeaponClass.ASSAULT_RIFLE, 1000f, 0.99f, 1f, 500f, 0f));
    }

    @Test
    @DisplayName("wire ordinals are frozen: the catalog order of the first 89 is protocol")
    void wireOrdinalsAreFrozen() {
        assertEquals(0, WeaponId.IRON_SIDEARM.ordinal());
        assertEquals(12, WeaponId.BRASS_JUDGE.ordinal());
        assertEquals(36, WeaponId.IRON_CARBINE.ordinal());
        assertEquals(66, WeaponId.CATHEDRAL.ordinal());
        assertEquals(88, WeaponId.YARD_SAW.ordinal());
        assertEquals(89, WeaponId.values().length);

        assertEquals(WeaponId.CATHEDRAL, WeaponId.fromOrdinal(66));
        assertEquals(WeaponId.DEFAULT, WeaponId.fromOrdinal(-1));
        assertEquals(WeaponId.DEFAULT, WeaponId.fromOrdinal(1234));
        assertTrue(WeaponId.isValidOrdinal(0));
        assertTrue(WeaponId.isValidOrdinal(88));
        assertEquals(WeaponId.DEFAULT, WeaponId.IRON_CARBINE);
        assertEquals(WeaponId.DEFAULT_SIDEARM, WeaponId.IRON_SIDEARM);
    }

    @Test
    @DisplayName("sprite asset ids are the catalog ids")
    void assetIds() {
        assertEquals("iron-carbine", WeaponId.IRON_CARBINE.assetId());
        assertEquals("ash-hopper-12", WeaponId.ASH_HOPPER_12.assetId());
        for (WeaponId id : WeaponId.values()) {
            assertTrue(id.assetId().matches("[a-z0-9-]+"), id + " has a non-catalog asset id");
        }
    }
}
