package io.github.skystrike.shared.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerMotionTest {

    private final ArenaMap map = ArenaMap.standard();

    @Test
    @DisplayName("ground movement accelerates smoothly toward walk speed with ground damping")
    void groundMovementAcceleratesCrisply() {
        Player player = new Player(1, "Test", 0, 500f, WorldConfig.GROUND_HEIGHT);
        PlayerInput input = new PlayerInput(1L, 1.0f, false, false, false, false, false, 0f);

        // Step 0.25 seconds on ground
        for (int i = 0; i < 15; i++) {
            player = PlayerMotion.step(player, input, 1f / 60f, map);
        }

        assertTrue(player.vx > 190f, "expected brisk acceleration on ground, got vx=" + player.vx);
        assertTrue(player.vx <= PlayerConfig.WALK_SPEED);
        assertTrue(player.grounded);
    }

    @Test
    @DisplayName("ground damping 16/s stops player much faster than air damping 1.0/s")
    void groundDampingVsAirDampingContrast() {
        Player groundPlayer = new Player(1, "Ground", 0, 500f, WorldConfig.GROUND_HEIGHT);
        groundPlayer.vx = 200f;
        groundPlayer.grounded = true;

        Player airPlayer = new Player(2, "Air", 0, 500f, 500f);
        airPlayer.vx = 200f;
        airPlayer.grounded = false;

        PlayerInput idleInput = new PlayerInput(1L, 0f, false, false, false, false, false, 0f);

        // Simulate 0.2s of deceleration
        for (int i = 0; i < 12; i++) {
            groundPlayer = PlayerMotion.step(groundPlayer, idleInput, 1f / 60f, map);
            airPlayer = PlayerMotion.step(airPlayer, idleInput, 1f / 60f, map);
        }

        // On ground with damping 16/s, exp(-16 * 0.2) = 0.04 -> vx < 15
        assertTrue(groundPlayer.vx < 15f, "ground player should stop rapidly, got vx=" + groundPlayer.vx);
        // In air with damping 1.0/s, exp(-1.0 * 0.2) = 0.818 -> vx > 150
        assertTrue(airPlayer.vx > 150f, "air player should carry momentum, got vx=" + airPlayer.vx);
    }

    @Test
    @DisplayName("crouching halves speed to 100 u/s and shrinks hitbox height from 50 to 30")
    void crouchHalvesSpeedAndShrinksHeight() {
        Player player = new Player(1, "Crouch", 0, 500f, WorldConfig.GROUND_HEIGHT);
        PlayerInput input = new PlayerInput(1L, 1.0f, false, true, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, input, 1f / 60f, map);
        }

        assertTrue(player.crouched);
        assertEquals(PlayerConfig.CROUCH_HEIGHT, player.currentHeight(), 1e-4f);
        assertEquals(30f, player.hitbox().height(), 1e-4f);
        assertTrue(player.vx <= PlayerConfig.CROUCH_SPEED + 0.1f);
        assertTrue(player.vx > 90f);
    }

    @Test
    @DisplayName("a stunned player targets 30 percent movement speed while the slow lasts")
    void stunSlowLimitsInputSpeed() {
        Player player = new Player(1, "Stunned", 0, 500f, WorldConfig.GROUND_HEIGHT);
        player.applyStatus(0f, 2f);
        PlayerInput input = new PlayerInput(1L, 1f, false, false, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, input, 1f / 60f, map);
        }

        assertTrue(player.isSlowed());
        assertTrue(player.vx <= PlayerConfig.WALK_SPEED * 0.30f + 0.1f);
        assertTrue(player.vx > PlayerConfig.WALK_SPEED * 0.25f);
    }

    @Test
    @DisplayName("jumping applies 420 u/s upward velocity and breaks grounded state")
    void jumpAppliesVelocity() {
        Player player = new Player(1, "Jumper", 0, 500f, WorldConfig.GROUND_HEIGHT);
        PlayerInput jumpInput = new PlayerInput(1L, 0f, true, false, false, false, false, 0f);

        player = PlayerMotion.step(player, jumpInput, 1f / 60f, map);

        assertFalse(player.grounded);
        assertTrue(player.vy > 350f, "expected initial jump vy ~ 406 after 1 tick gravity, got: " + player.vy);
    }

    @Test
    @DisplayName("jetpack burns 20 fuel/s for exactly 5 seconds of continuous flight")
    void jetpackFuelConsumption() {
        Player player = new Player(1, "Flyer", 0, 500f, 300f);
        player.grounded = false;
        player.fuel = 100f;

        PlayerInput flyInput = new PlayerInput(1L, 0f, false, false, true, false, false, 90f);

        // Step for 1 second (60 ticks)
        for (int i = 0; i < 60; i++) {
            player = PlayerMotion.step(player, flyInput, 1f / 60f, map);
        }

        assertEquals(80f, player.fuel, 0.5f);
        assertTrue(player.jetpacking);
    }

    @Test
    @DisplayName("an intact tank raises jetpack capacity and thrust, while a broken tank does neither")
    void fuelTankMultipliersAreSharedWithPrediction() {
        Player ordinary = new Player(1, "Ordinary", 0, 500f, 300f);
        Player tanked = new Player(2, "Tanked", 0, 500f, 300f);
        tanked.loadout.setGadgets(GadgetId.FUEL_TANK, GadgetId.NONE);
        ordinary.grounded = false;
        tanked.grounded = false;
        ordinary.fuel = 150f;
        tanked.fuel = PlayerConfig.MAX_FUEL * GadgetConfig.FUEL_TANK_CAPACITY_MULTIPLIER;

        PlayerInput idle = new PlayerInput(1L, 0f, false, false, false, false, false, 90f);
        ordinary = PlayerMotion.step(ordinary, idle, 1f / 60f, map);
        tanked = PlayerMotion.step(tanked, idle, 1f / 60f, map);
        assertEquals(PlayerConfig.MAX_FUEL, ordinary.fuel, 0.001f);
        assertEquals(PlayerConfig.MAX_FUEL * GadgetConfig.FUEL_TANK_CAPACITY_MULTIPLIER,
            tanked.fuel, 0.001f);

        PlayerInput jetpack = new PlayerInput(2L, 0f, false, false, true, false, false, 90f);
        ordinary = PlayerMotion.step(ordinary, jetpack, 1f / 60f, map);
        tanked = PlayerMotion.step(tanked, jetpack, 1f / 60f, map);
        assertTrue(tanked.vy > ordinary.vy, "the intact tank must increase shared thrust");

        tanked.loadout.gadgetQ.broken = true;
        tanked.loadout.gadgetQ.active = false;
        tanked.fuel = 150f;
        tanked = PlayerMotion.step(tanked, idle, 1f / 60f, map);
        assertEquals(PlayerConfig.MAX_FUEL, tanked.fuel, 0.001f);
    }

    @Test
    @DisplayName("jetpack fuel recharges at 20/s only while grounded")
    void jetpackFuelRechargesGroundedOnly() {
        // High enough to still be falling a second later: gravity is 800 u/s^2, so the drop over
        // the measured second is about 400 units. Starting at y = 500 landed this player on a
        // ramp step half way through the test, and it recharged on the way.
        Player airPlayer = new Player(1, "Air", 0, 500f, 1500f);
        airPlayer.grounded = false;
        airPlayer.fuel = 50f;

        Player groundPlayer = new Player(2, "Ground", 0, 500f, WorldConfig.GROUND_HEIGHT);
        groundPlayer.grounded = true;
        groundPlayer.fuel = 50f;

        PlayerInput idle = new PlayerInput(1L, 0f, false, false, false, false, false, 0f);

        // Step 1 second
        for (int i = 0; i < 60; i++) {
            airPlayer = PlayerMotion.step(airPlayer, idle, 1f / 60f, map);
            groundPlayer = PlayerMotion.step(groundPlayer, idle, 1f / 60f, map);
        }

        assertEquals(50f, airPlayer.fuel, 1e-3f, "air player fuel must not recharge");
        assertEquals(70f, groundPlayer.fuel, 0.5f, "grounded player fuel must recharge by 20");
    }

    @Test
    @DisplayName("swept collision prevents moving through solid walls")
    void sweptCollisionStopsAtWall() {
        // Lane pillar is at x=1010, width=24, height=260. Player width=30 (half-width 15).
        // Approaching pillar from x=980 moving right at 200 u/s
        Player player = new Player(1, "Runner", 0, 980f, WorldConfig.GROUND_HEIGHT);
        player.vx = 200f;
        PlayerInput input = new PlayerInput(1L, 1.0f, false, false, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, input, 1f / 60f, map);
        }

        // Right edge of player (x + 15) must not exceed pillar left (1010)
        assertEquals(1010f - 15f, player.x, 1e-3f);
        assertEquals(0f, player.vx, 1e-3f);
    }

    @Test
    @DisplayName("sv_noclip skips the swept collision and gravity, passing straight through a wall")
    void noclipPassesThroughWallsAndIgnoresGravity() {
        // Same lane pillar and approach as sweptCollisionStopsAtWall, but with noclip on: the
        // player must sail through instead of stopping, and never pick up downward gravity speed.
        Player player = new Player(1, "Ghost", 0, 980f, WorldConfig.GROUND_HEIGHT);
        player.vx = 200f;
        player.grounded = false;
        PlayerInput input = new PlayerInput(1L, 1.0f, false, false, false, false, false, 0f);

        for (int i = 0; i < 30; i++) {
            player = PlayerMotion.step(player, input, 1f / 60f, map, true);
        }

        // 30 ticks at 1/60s and 200 u/s cover 100 units: 980 + 100 = 1080, well past the pillar
        // spanning x=[1010, 1034].
        assertEquals(1080f, player.x, 1e-2f, "noclip must not be stopped by the pillar");
        assertEquals(200f, player.vx, 1e-3f, "noclip never zeroes velocity on contact");
        assertEquals(0f, player.vy, 1e-3f, "noclip never accumulates gravity");
        assertFalse(player.grounded, "noclip never reports grounded");
    }

    @Test
    @DisplayName("coyote time keeps body upright for 0.10s after leaving ground")
    void coyoteTimeMaintainsUprightLock() {
        Player player = new Player(1, "Coyote", 0, 500f, 300f);
        player.grounded = false;
        player.coyoteTimer = 0.08f; // Within 0.10s coyote window
        player.vx = 200f; // High velocity that would otherwise tilt

        PlayerInput input = new PlayerInput(1L, 1.0f, false, false, false, false, false, 0f);
        player = PlayerMotion.step(player, input, 1f / 60f, map);

        assertEquals(0f, player.rotation, 0.5f, "coyote window must keep body upright");
    }

    @Test
    @DisplayName("backpedal case cuts aim torque to zero and multiplies velocity bank by 2.40")
    void backpedalLeanReadsDramatically() {
        // Player aiming right (aim = 0°) while moving left (vx = -200) in air
        Player backpedalPlayer = new Player(1, "Backpedal", 0, 1500f, 500f);
        backpedalPlayer.grounded = false;
        backpedalPlayer.coyoteTimer = 0f;
        backpedalPlayer.vx = -200f;

        Player normalFlyer = new Player(2, "Normal", 0, 1500f, 500f);
        normalFlyer.grounded = false;
        normalFlyer.coyoteTimer = 0f;
        normalFlyer.vx = 200f; // Moving forward in facing direction

        PlayerInput aimRight = new PlayerInput(1L, 0f, false, false, false, false, false, 0f);

        for (int i = 0; i < 60; i++) {
            backpedalPlayer = PlayerMotion.step(backpedalPlayer, aimRight, 1f / 60f, map);
            normalFlyer = PlayerMotion.step(normalFlyer, aimRight, 1f / 60f, map);
        }

        // Backpedal lean is significantly larger in magnitude due to 2.40x multiplier
        assertTrue(
            Math.abs(backpedalPlayer.rotation) > Math.abs(normalFlyer.rotation) * 1.8f,
            "backpedal lean (" + backpedalPlayer.rotation + ") should be much larger than forward (" + normalFlyer.rotation + ")");
    }

    @Test
    @DisplayName("simulation is deterministic: same inputs produce identical states")
    void simulationDeterminism() {
        Player p1 = new Player(1, "P1", 0, 300f, WorldConfig.GROUND_HEIGHT);
        Player p2 = new Player(1, "P1", 0, 300f, WorldConfig.GROUND_HEIGHT);

        PlayerInput input = new PlayerInput(1L, 1.0f, true, false, true, true, false, 45f);

        for (int i = 0; i < 100; i++) {
            p1 = PlayerMotion.step(p1, input, 1f / 60f, map);
            p2 = PlayerMotion.step(p2, input, 1f / 60f, map);
        }

        assertEquals(p1.x, p2.x, 0f);
        assertEquals(p1.y, p2.y, 0f);
        assertEquals(p1.vx, p2.vx, 0f);
        assertEquals(p1.vy, p2.vy, 0f);
        assertEquals(p1.rotation, p2.rotation, 0f);
        assertEquals(p1.fuel, p2.fuel, 0f);
        assertEquals(p1.grounded, p2.grounded);
    }
}
