package io.github.skystrike.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The mechanics-§7 numbers as transcribed, and the one reference-unit conversion they share.
 * These tests pin the documented values so a retune is a visible, deliberate diff.
 */
class GadgetConfigTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("the §7 reference scale converts through the player: 50 units / 2 = 25")
    void conversionIsDerivedFromThePlayer() {
        assertEquals(PlayerConfig.STAND_HEIGHT / 2f,
            GadgetConfig.WORLD_UNITS_PER_REFERENCE_UNIT, EPSILON);
    }

    @Test
    @DisplayName("drone: 30 HP, speed 8 → 200 u/s, cone 10 units → 250 at 70°, deploys 1 unit → 25 up")
    void droneNumbers() {
        assertEquals(30f, GadgetConfig.DRONE_HEALTH, EPSILON);
        assertEquals(200f, GadgetConfig.DRONE_SPEED, EPSILON);
        assertEquals(25f, GadgetConfig.DRONE_SPAWN_OFFSET_Y, EPSILON);
        assertEquals(250f, GadgetConfig.DRONE_VISION_RANGE, EPSILON);
        assertEquals(70f, GadgetConfig.DRONE_VISION_ANGLE_DEGREES, EPSILON);

        // "Narrower and dimmer than a player's" — both must genuinely be smaller.
        assertTrue(GadgetConfig.DRONE_VISION_RANGE < VisionConfig.REACH_HIP);
        assertTrue(GadgetConfig.DRONE_VISION_ANGLE_DEGREES < VisionConfig.CONE_ANGLE_DEGREES);
        assertTrue(GadgetConfig.DRONE_VISION_BRIGHTNESS > 0f
            && GadgetConfig.DRONE_VISION_BRIGHTNESS < 1f);

        // A drone flies exactly at player walk speed — the conversion's sanity anchor.
        assertEquals(PlayerConfig.WALK_SPEED, GadgetConfig.DRONE_SPEED, EPSILON);
        assertTrue(GadgetConfig.DRONE_VELOCITY_LERP_RATE > 0f);
        assertTrue(GadgetConfig.DRONE_DAMPING > 0f);
    }

    @Test
    @DisplayName("camera: 20 HP, speed 12 → 300 u/s, throwable gravity, 1.2× zoom, permanent")
    void cameraNumbers() {
        assertEquals(20f, GadgetConfig.CAMERA_HEALTH, EPSILON);
        assertEquals(300f, GadgetConfig.CAMERA_THROW_SPEED, EPSILON);
        assertEquals(UtilityConfig.GRAVITY, GadgetConfig.CAMERA_GRAVITY, EPSILON);
        assertEquals(1.2f, GadgetConfig.CAMERA_ZOOM, EPSILON);
        assertTrue(GadgetConfig.CAMERA_PERMANENT);
        assertTrue(GadgetConfig.CAMERA_VISION_RANGE > 0f);
        assertTrue(GadgetConfig.CAMERA_VISION_ANGLE_DEGREES > 0f
            && GadgetConfig.CAMERA_VISION_ANGLE_DEGREES <= 360f);
    }

    @Test
    @DisplayName("shield: 150 durability, 90° frontal arc; the rear arc is a positive sub-half-circle")
    void shieldNumbers() {
        assertEquals(150f, GadgetConfig.SHIELD_DURABILITY, EPSILON);
        assertEquals(90f, GadgetConfig.SHIELD_FRONT_ARC_DEGREES, EPSILON);
        assertTrue(GadgetConfig.SHIELD_REAR_ARC_DEGREES > 0f
            && GadgetConfig.SHIELD_REAR_ARC_DEGREES <= 180f);

        // The two arcs must never overlap, or one hit could be claimed by both states.
        assertTrue(GadgetConfig.SHIELD_FRONT_ARC_DEGREES / 2f
            + GadgetConfig.SHIELD_REAR_ARC_DEGREES / 2f <= 180f);
    }

    @Test
    @DisplayName("fuel tank: ×1.75 capacity, ×1.40 thrust, 120 damage in a 4-unit → 100 radius")
    void fuelTankNumbers() {
        assertEquals(1.75f, GadgetConfig.FUEL_TANK_CAPACITY_MULTIPLIER, EPSILON);
        assertEquals(1.40f, GadgetConfig.FUEL_TANK_THRUST_MULTIPLIER, EPSILON);
        assertEquals(120f, GadgetConfig.FUEL_TANK_EXPLOSION_DAMAGE, EPSILON);
        assertEquals(100f, GadgetConfig.FUEL_TANK_EXPLOSION_RADIUS, EPSILON);
        assertTrue(GadgetConfig.FUEL_TANK_EXPLOSION_IMPULSE > 0f);

        // The blast is a tight pocket, not an area weapon: well under the frag's 350.
        assertTrue(GadgetConfig.FUEL_TANK_EXPLOSION_RADIUS < 350f);
    }

    @Test
    @DisplayName("two gadget slots, as mechanics §8 lays out the loadout")
    void slotCount() {
        assertEquals(2, GadgetConfig.GADGET_SLOTS);
    }
}
