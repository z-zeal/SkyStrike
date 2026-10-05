package io.github.skystrike.shared.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.map.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    @DisplayName("hitbox corresponds to bottom-center x, y with standing vs crouching height")
    void hitboxGeometry() {
        Player player = new Player(1, "Hero", 0, 100f, 200f);

        // Standing hitbox
        player.crouched = false;
        assertEquals(50f, player.currentHeight());
        Rect box = player.hitbox();
        assertEquals(100f - 15f, box.left());
        assertEquals(100f + 15f, box.right());
        assertEquals(200f, box.bottom());
        assertEquals(250f, box.top());

        // Crouched hitbox
        player.crouched = true;
        assertEquals(30f, player.currentHeight());
        Rect crouchBox = player.hitbox();
        assertEquals(85f, crouchBox.left());
        assertEquals(115f, crouchBox.right());
        assertEquals(200f, crouchBox.bottom());
        assertEquals(230f, crouchBox.top());
    }

    @Test
    void facingDirectionDerivesFromAimAngle() {
        Player player = new Player(1, "Hero", 0, 100f, 100f);

        player.aimAngle = 0f; // Right
        assertTrue(player.isFacingRight());

        player.aimAngle = 45f; // Up-Right
        assertTrue(player.isFacingRight());

        player.aimAngle = -45f; // Down-Right
        assertTrue(player.isFacingRight());

        player.aimAngle = 180f; // Left
        assertFalse(player.isFacingRight());

        player.aimAngle = 135f; // Up-Left
        assertFalse(player.isFacingRight());

        player.aimAngle = -135f; // Down-Left
        assertFalse(player.isFacingRight());
    }

    @Test
    void copyIsDeepAndIndependent() {
        Player original = new Player(42, "Original", 1, 500f, 100f);
        original.vx = 120f;
        original.vy = -50f;
        original.fuel = 75f;
        original.rotation = -12f;

        Player copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(original.id, copy.id);
        assertEquals(original.name, copy.name);
        assertEquals(original.x, copy.x);
        assertEquals(original.y, copy.y);
        assertEquals(original.fuel, copy.fuel);
        assertEquals(original.rotation, copy.rotation);

        copy.x = 999f;
        assertEquals(500f, original.x);
    }
}
