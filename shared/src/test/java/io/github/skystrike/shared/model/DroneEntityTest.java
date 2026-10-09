package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The drone entity's wire shape: copy/set, hitbox and damage bookkeeping. */
class DroneEntityTest {

    @Test
    @DisplayName("copy and set carry every field, so snapshots cannot alias live state")
    void copyIsCompleteAndIndependent() {
        DroneEntity drone = new DroneEntity(7, 3, 1, 400f, 900f, 45f, 30f);
        drone.vx = 12f;
        drone.vy = -4f;
        drone.prevX = 399f;
        drone.prevY = 901f;

        DroneEntity copy = drone.copy();
        assertEquals(7, copy.id);
        assertEquals(3, copy.ownerId);
        assertEquals(1, copy.teamIndex);
        assertEquals(400f, copy.x, 1e-6f);
        assertEquals(900f, copy.y, 1e-6f);
        assertEquals(399f, copy.prevX, 1e-6f);
        assertEquals(901f, copy.prevY, 1e-6f);
        assertEquals(12f, copy.vx, 1e-6f);
        assertEquals(-4f, copy.vy, 1e-6f);
        assertEquals(45f, copy.aimAngle, 1e-6f);
        assertEquals(30f, copy.health, 1e-6f);

        copy.x = 1f;
        copy.health = 1f;
        assertEquals(400f, drone.x, "mutating the copy leaves the original alone");
        assertEquals(30f, drone.health, 1e-6f);

        DroneEntity into = new DroneEntity();
        into.set(drone);
        assertEquals(7, into.id);
        assertEquals(30f, into.health, 1e-6f);
    }

    @Test
    @DisplayName("the hitbox is a square centred on the drone, sized by the one config value")
    void hitboxIsCentredAndConfigSized() {
        DroneEntity drone = new DroneEntity(1, 1, 0, 500f, 600f, 0f, 30f);
        var box = drone.hitbox();
        float r = GadgetConfig.DRONE_RADIUS;
        assertEquals(500f - r, box.left(), 1e-6f);
        assertEquals(500f + r, box.right(), 1e-6f);
        assertEquals(600f - r, box.bottom(), 1e-6f);
        assertEquals(600f + r, box.top(), 1e-6f);
        assertEquals(2f * r, box.width(), 1e-6f);
    }

    @Test
    @DisplayName("damage drains health to zero and no further; destroyed reads at zero")
    void damageAndDestruction() {
        DroneEntity drone = new DroneEntity(1, 1, 0, 0f, 0f, 0f, GadgetConfig.DRONE_HEALTH);
        assertFalse(drone.isDestroyed());

        drone.applyDamage(10f);
        assertEquals(GadgetConfig.DRONE_HEALTH - 10f, drone.health, 1e-6f);
        assertFalse(drone.isDestroyed());

        drone.applyDamage(1000f);
        assertEquals(0f, drone.health, 1e-6f, "health clamps at zero");
        assertTrue(drone.isDestroyed());

        drone.applyDamage(5f);
        assertEquals(0f, drone.health, 1e-6f, "a destroyed drone stays at zero");
        drone.applyDamage(-5f);
        assertEquals(0f, drone.health, 1e-6f, "negative damage is refused");
    }

    @Test
    @DisplayName("the entity knows which gadget it is the live form of")
    void gadgetIdIsDrone() {
        assertEquals(GadgetId.DRONE, new DroneEntity().gadgetId());
    }
}
