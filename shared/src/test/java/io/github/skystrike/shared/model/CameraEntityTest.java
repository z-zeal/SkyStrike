package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The camera entity's wire shape: copy/set, hitbox, stick flags and damage bookkeeping. */
class CameraEntityTest {

    @Test
    @DisplayName("copy and set carry every field, including the stick state and contact normal")
    void copyIsCompleteAndIndependent() {
        CameraEntity camera = new CameraEntity(9, 4, 1, 700f, 300f, 120f, -80f, 15f, 20f);
        camera.stuck = true;
        camera.contactNormalX = 0f;
        camera.contactNormalY = 1f;
        camera.prevX = 690f;
        camera.prevY = 310f;

        CameraEntity copy = camera.copy();
        assertEquals(9, copy.id);
        assertEquals(4, copy.ownerId);
        assertEquals(1, copy.teamIndex);
        assertEquals(700f, copy.x, 1e-6f);
        assertEquals(300f, copy.y, 1e-6f);
        assertEquals(690f, copy.prevX, 1e-6f);
        assertEquals(310f, copy.prevY, 1e-6f);
        assertEquals(120f, copy.vx, 1e-6f);
        assertEquals(-80f, copy.vy, 1e-6f);
        assertTrue(copy.stuck);
        assertEquals(0f, copy.contactNormalX, 1e-6f);
        assertEquals(1f, copy.contactNormalY, 1e-6f);
        assertEquals(15f, copy.aimAngle, 1e-6f);
        assertEquals(20f, copy.health, 1e-6f);

        copy.stuck = false;
        copy.health = 1f;
        assertTrue(camera.stuck, "mutating the copy leaves the original alone");
        assertEquals(20f, camera.health, 1e-6f);

        CameraEntity into = new CameraEntity();
        into.set(camera);
        assertEquals(9, into.id);
        assertTrue(into.stuck);
    }

    @Test
    @DisplayName("the hitbox is a square centred on the camera, sized by the one config value")
    void hitboxIsCentredAndConfigSized() {
        CameraEntity camera = new CameraEntity(1, 1, 0, 500f, 600f, 0f, 0f, 0f, 20f);
        var box = camera.hitbox();
        float r = GadgetConfig.CAMERA_RADIUS;
        assertEquals(500f - r, box.left(), 1e-6f);
        assertEquals(500f + r, box.right(), 1e-6f);
        assertEquals(600f - r, box.bottom(), 1e-6f);
        assertEquals(600f + r, box.top(), 1e-6f);
    }

    @Test
    @DisplayName("damage drains health to zero and no further; destroyed reads at zero")
    void damageAndDestruction() {
        CameraEntity camera = new CameraEntity(1, 1, 0, 0f, 0f, 0f, 0f, 0f, GadgetConfig.CAMERA_HEALTH);
        assertFalse(camera.isDestroyed());

        camera.applyDamage(15f);
        assertEquals(GadgetConfig.CAMERA_HEALTH - 15f, camera.health, 1e-6f);

        camera.applyDamage(1000f);
        assertEquals(0f, camera.health, 1e-6f);
        assertTrue(camera.isDestroyed());

        camera.applyDamage(5f);
        assertEquals(0f, camera.health, 1e-6f);
        camera.applyDamage(-5f);
        assertEquals(0f, camera.health, 1e-6f);
    }

    @Test
    @DisplayName("the entity knows which gadget it is the live form of")
    void gadgetIdIsCamera() {
        assertEquals(GadgetId.CAMERA, new CameraEntity().gadgetId());
    }
}
