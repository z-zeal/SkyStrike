package io.github.skystrike.shared.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockKind;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.Material;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one-way platform rules of map plan §7, exercised on a map that actually carries a platform.
 *
 * <p>The shipped arena is wall-only, so it cannot prove any of this: on it every one-way behaviour
 * is vacuous and the sweeper must behave exactly as it did before the movement column landed. These
 * tests therefore author a single thin slab in open air and drive the shared sweeper through it.
 *
 * <p>The four properties are the contract: a rising body is never stopped, a falling body lands on
 * the top face, a fresh crouch press (S alone, no jump) drops a body through without letting it
 * re-land on the slab it is leaving, and a body already inside a slab is neither ejected nor
 * supported. The drop trigger is a <i>fresh</i> press — the body not already crouched when the
 * sample arrived — so crouch-walking onto a platform is still possible and a held key never
 * re-drops; the tests below pin that gate and the inertness of jump while crouched on a slab.
 */
class PlatformMotionTest {

    private static final float SLAB_TOP = 500f;
    private static final float SLAB_THICKNESS = 18f;
    private static final float SLAB_BOTTOM = SLAB_TOP - SLAB_THICKNESS;

    /**
     * One centred one-way slab in open air, wide enough that nothing in these tests can walk off
     * its edge. There is no floor: everything below the slab is empty down to the arena bound, so a
     * body that falls through it keeps falling.
     */
    private static final ArenaMap PLATFORM_MAP = ArenaMap.builder(1500f)
        .centred(new MapBlock(
            BlockKind.PLATFORM, Material.ENERGY, new Rect(600f, SLAB_BOTTOM, 1800f, SLAB_THICKNESS)))
        .build();

    private static final PlayerInput IDLE =
        new PlayerInput(1L, 0f, false, false, false, false, false, 0f);

    @Test
    @DisplayName("a body rising through a one-way platform is never stopped or landed on")
    void risingThroughAPlatformIsNotBlocked() {
        Player player = new Player(1, "Riser", 0, 1500f, SLAB_BOTTOM - 60f);
        player.grounded = false;
        player.vy = 300f;
        PlayerInput rise = new PlayerInput(1L, 0f, false, false, true, false, false, 90f);

        for (int i = 0; i < 90; i++) {
            player = PlayerMotion.step(player, rise, 1f / 60f, PLATFORM_MAP);
            if (player.y < SLAB_TOP) {
                assertTrue(player.vy > 0f, "a one-way top must never stop a rise: vy=" + player.vy);
                assertFalse(player.grounded, "a one-way top is not standable from below");
            }
        }

        assertTrue(
            player.y > SLAB_TOP,
            "the body must pass straight up through the slab, ended at y=" + player.y);
    }

    @Test
    @DisplayName("a body falling onto a platform lands on its top face and stays there")
    void fallingOntoAPlatformLands() {
        Player player = new Player(1, "Faller", 0, 1500f, SLAB_TOP + 100f);
        player.grounded = false;

        for (int i = 0; i < 60; i++) {
            player = PlayerMotion.step(player, IDLE, 1f / 60f, PLATFORM_MAP);
        }

        assertEquals(SLAB_TOP, player.y, 1e-3f, "the feet must stop on the slab's top face");
        assertEquals(0f, player.vy, 1e-3f);
        assertTrue(player.grounded);
    }

    @Test
    @DisplayName("a body standing on a platform stays standing instead of sinking through it")
    void standingOnAPlatformIsStable() {
        Player player = new Player(1, "Standing", 0, 1500f, SLAB_TOP);
        player.grounded = true;

        for (int i = 0; i < 60; i++) {
            player = PlayerMotion.step(player, IDLE, 1f / 60f, PLATFORM_MAP);
        }

        assertEquals(SLAB_TOP, player.y, 1e-3f, "gravity must not pull a landed body into the slab");
        assertTrue(player.grounded);
        assertEquals(0f, player.dropThroughTimer, 0f);
    }

    @Test
    @DisplayName(
        "a crouch press alone drops the body through the slab, and it does not re-land mid-drop")
    void crouchPressAloneDropsThroughAndDoesNotReland() {
        Player player = new Player(1, "Dropper", 0, 1500f, SLAB_TOP);
        player.grounded = true;
        PlayerInput crouch = new PlayerInput(1L, 0f, false, true, false, false, false, 0f);

        float previous = player.y;
        for (int i = 0; i < 60; i++) {
            player = PlayerMotion.step(player, crouch, 1f / 60f, PLATFORM_MAP);
            assertTrue(
                player.y < previous,
                "the drop must never be caught by the slab it left, froze at y=" + player.y);
            previous = player.y;
        }

        assertTrue(player.y < SLAB_BOTTOM, "the body must end below the slab, y=" + player.y);
        assertFalse(player.grounded);
        assertEquals(
            0f, player.dropThroughTimer, 1e-4f,
            "the window ends as soon as the feet have cleared the slab");
    }

    @Test
    @DisplayName("crouch held from before the landing does not drop; releasing and re-pressing does")
    void heldCrouchLandsAndARepressDrops() {
        Player player = new Player(1, "Lander", 0, 1500f, SLAB_TOP + 80f);
        player.grounded = false;
        PlayerInput crouchHeld = new PlayerInput(1L, 0f, false, true, false, false, false, 0f);

        boolean landed = false;
        for (int i = 0; i < 120 && !landed; i++) {
            player = PlayerMotion.step(player, crouchHeld, 1f / 60f, PLATFORM_MAP);
            landed = player.grounded;
        }
        assertTrue(landed, "the body must land on the slab while crouching");
        assertTrue(player.crouched, "the held key must keep the body crouched");
        assertEquals(SLAB_TOP, player.y, 1e-3f);

        // The same held press is not a fresh one: the body crouch-walks on instead of dropping.
        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, crouchHeld, 1f / 60f, PLATFORM_MAP);
            assertEquals(0f, player.dropThroughTimer, 0f, "a held crouch must not start a drop");
            assertTrue(player.grounded, "the slab must keep supporting the crouched body");
        }

        // Release (stand up in the open sky over the slab), then a genuinely fresh press drops.
        player = PlayerMotion.step(player, IDLE, 1f / 60f, PLATFORM_MAP);
        assertFalse(player.crouched, "open sky above the slab must allow standing up");
        player = PlayerMotion.step(player, crouchHeld, 1f / 60f, PLATFORM_MAP);
        assertTrue(player.dropThroughTimer > 0f, "a fresh crouch press on the slab must drop");
        assertFalse(player.grounded);
    }

    @Test
    @DisplayName("a jump press while crouched on a slab cannot launch the body up off it")
    void jumpWhileCrouchedOnASlabIsInert() {
        Player player = new Player(1, "Crouched", 0, 1500f, SLAB_TOP);
        player.grounded = true;
        player.crouched = true;
        PlayerInput crouchHoldJump = new PlayerInput(1L, 0f, true, true, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, crouchHoldJump, 1f / 60f, PLATFORM_MAP);
            assertTrue(
                player.y <= SLAB_TOP + 1e-3f,
                "a crouched body on a slab must not be launched up: y=" + player.y);
            assertTrue(player.grounded, "the slab must keep supporting the crouched body");
            assertEquals(0f, player.dropThroughTimer, 0f, "a held crouch is not a fresh press");
        }
    }

    @Test
    @DisplayName("a jump press riding the same sample as the drop press is consumed by the drop")
    void jumpRidingTheDropSampleIsConsumed() {
        Player player = new Player(1, "Dropper", 0, 1500f, SLAB_TOP);
        player.grounded = true;
        PlayerInput dropWithJump = new PlayerInput(1L, 0f, true, true, false, false, false, 0f);

        float previous = player.y;
        for (int i = 0; i < 60; i++) {
            player = PlayerMotion.step(player, dropWithJump, 1f / 60f, PLATFORM_MAP);
            assertTrue(
                player.y <= previous + 1e-3f,
                "the drop must win over the jump on the same sample, froze at y=" + player.y);
            previous = player.y;
        }

        assertTrue(player.y < SLAB_BOTTOM, "the body must end below the slab, y=" + player.y);
        assertFalse(player.grounded);
    }

    @Test
    @DisplayName("a body already inside a slab is not ejected onto its top face")
    void aBodyInsideASlabIsNotEjected() {
        Player falling = new Player(1, "Embedded", 0, 1500f, SLAB_TOP - 5f);
        falling.grounded = false;
        falling.vy = -100f;

        falling = PlayerMotion.step(falling, IDLE, 1f / 60f, PLATFORM_MAP);
        assertTrue(falling.y < SLAB_TOP - 5f, "the body must keep falling through, y=" + falling.y);
        assertFalse(falling.grounded, "a body inside a slab is not snapped up onto it");

        Player drifting = new Player(2, "Drifting", 0, 1500f, SLAB_TOP - 5f);
        drifting.grounded = false;

        drifting = PlayerMotion.step(drifting, IDLE, 1f / 60f, PLATFORM_MAP);
        assertTrue(drifting.y < SLAB_TOP - 5f, "gravity must move it down, not up");
        assertFalse(drifting.grounded);
    }

    @Test
    @DisplayName("crouching and jumping on solid ground still behave as they always did")
    void crouchAndJumpOnSolidGroundStillBehaves() {
        // The arena's own one-way top is the sky platform 1200 units overhead, so nothing is under
        // this body's feet: the drop-through branch cannot engage, the fresh crouch press keeps its
        // ordinary crouch meaning on the ground, and jump still jumps.
        Player player = new Player(1, "Jumper", 0, 500f, WorldConfig.GROUND_HEIGHT);
        player.grounded = true;
        PlayerInput crouchJump = new PlayerInput(1L, 0f, true, true, false, false, false, 0f);

        player = PlayerMotion.step(player, crouchJump, 1f / 60f, ArenaMap.standard());

        assertTrue(player.vy > 350f, "expected a jump, got vy=" + player.vy);
        assertFalse(player.grounded);
        assertEquals(0f, player.dropThroughTimer, 0f, "no platform was under the body");
    }

    @Test
    @DisplayName("a platform is never a horizontal collider")
    void aPlatformNeverBlocksHorizontalMovement() {
        // Feet inside the slab's band, so the body genuinely overlaps it while it runs.
        Player player = new Player(1, "Runner", 0, 1500f, SLAB_BOTTOM - 20f);
        player.vx = 200f;
        player.grounded = false;
        PlayerInput run = new PlayerInput(1L, 1f, false, false, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, run, 1f / 60f, PLATFORM_MAP);
        }

        assertTrue(player.x > 1500f, "the body must walk through the slab's x span, x=" + player.x);
        assertTrue(player.vx > 190f, "the slab must not zero horizontal velocity, vx=" + player.vx);
    }
}
