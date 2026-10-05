package io.github.skystrike.server.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The trigger: what a held button and a fresh press each mean, per fire mode.
 *
 * <p>These run on a fixed seed so the random deviation is reproducible, and assert on structure
 * (counts, patterns, bounds) rather than on exact angles.
 */
class FireControllerTest {

    private FireController controller;
    private Player player;
    private FireController.Volley volley;

    @BeforeEach
    void setUp() {
        controller = new FireController(new Random(20260305L), new RecoilService());
        player = new Player(1, "Shooter", 0, 500f, 100f);
        player.aimAngle = 0f;
        player.grounded = true;
        volley = new FireController.Volley();
    }

    private static GunInstance gun(WeaponId id) {
        return new GunInstance(id);
    }

    @Test
    @DisplayName("an automatic keeps firing while the trigger is held")
    void automaticFiresWhileHeld() {
        GunInstance scar = gun(WeaponId.IRON_CARBINE);

        assertEquals(1, controller.fire(player, scar, true, true, volley));
        assertEquals(0, controller.fire(player, scar, true, false, volley), "still cycling");

        scar.update(scar.definition().cooldownSeconds(), false, false);
        assertEquals(1, controller.fire(player, scar, true, false, volley),
            "an automatic needs no new press");

        scar.update(scar.definition().cooldownSeconds(), false, false);
        assertEquals(0, controller.fire(player, scar, false, false, volley),
            "releasing the trigger stops it");
    }

    @Test
    @DisplayName("semi-automatic and bolt weapons need a fresh press each time")
    void semiAndBoltRequireTriggerRelease() {
        GunInstance sidearm = gun(WeaponId.IRON_SIDEARM);

        assertEquals(1, controller.fire(player, sidearm, true, true, volley));
        sidearm.update(sidearm.definition().cooldownSeconds(), false, false);

        assertEquals(0, controller.fire(player, sidearm, true, false, volley),
            "holding a sidearm down does not empty the magazine");
        assertEquals(1, controller.fire(player, sidearm, true, true, volley),
            "releasing and pressing again fires");

        GunInstance awp = gun(WeaponId.CATHEDRAL);
        assertEquals(1, controller.fire(player, awp, true, true, volley));
        awp.update(awp.definition().cooldownSeconds(), false, false);
        assertEquals(0, controller.fire(player, awp, true, false, volley));
    }

    @Test
    @DisplayName("the cooldown is the weapon's cycle, to the tick")
    void cooldownGatesEverything() {
        GunInstance corridor = gun(WeaponId.BLACK_CORRIDOR);
        float cooldown = corridor.definition().cooldownSeconds();

        assertEquals(1, controller.fire(player, corridor, true, true, volley));

        corridor.update(cooldown * 0.9f, false, false);
        assertEquals(0, controller.fire(player, corridor, true, true, volley), "90% of the cycle is not enough");

        corridor.update(cooldown * 0.2f, false, false);
        assertEquals(1, controller.fire(player, corridor, true, true, volley));
    }

    @Test
    @DisplayName("a burst is three rounds in one instant, centred on the aim")
    void burstFiresThreeRounds() {
        GunInstance burstRifle = gun(WeaponId.HALCYON_16);
        player.aimAngle = 30f;

        int rounds = controller.fire(player, burstRifle, true, true, volley);
        assertEquals(WeaponConfig.BURST_ROUNDS, rounds);
        assertEquals(WeaponConfig.BURST_ROUNDS, volley.count());
        assertTrue(volley.isBurst());

        float spacing = WeaponConfig.BURST_SPACING_DEGREES;
        float maxJitter = burstRifle.definition().spread().ceilingDegrees() * WeaponConfig.BURST_JITTER_FRACTION;
        float mean = 0f;
        for (int i = 0; i < rounds; i++) {
            float offset = volley.angle(i) - 30f;
            assertTrue(Math.abs(offset) <= spacing + maxJitter + 1e-3f,
                "burst round " + i + " strayed to " + offset);
            mean += offset;
        }
        mean /= rounds;
        assertTrue(Math.abs(mean) < spacing + maxJitter, "the group must sit on the aim: " + mean);

        // A burst applies its recoil once, at 1.20x.
        assertEquals(WeaponConfig.BURST_RECOIL_MULTIPLIER, volley.recoilMultiplier(), 1e-4f);
    }

    @Test
    @DisplayName("a shell is eight pellets spread across the whole cone, one kick")
    void shotgunFiresEightPellets() {
        GunInstance shotgun = gun(WeaponId.SCATTER_BENCH);
        float cone = shotgun.currentSpread();
        player.aimAngle = 0f;

        int pellets = controller.fire(player, shotgun, true, true, volley);
        assertEquals(shotgun.definition().pelletCount(), pellets);
        assertFalse(volley.isBurst());

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < pellets; i++) {
            float angle = volley.angle(i);
            min = Math.min(min, angle);
            max = Math.max(max, angle);
            assertTrue(Math.abs(angle) <= cone / 2f + WeaponConfig.PELLET_JITTER_HIP_DEGREES + 1e-3f,
                "pellet " + i + " left the cone at " + angle);
        }
        assertTrue(max - min > cone / 2f, "the pellets must actually spread: " + (max - min));
        assertTrue(min < 0f && max > 0f, "the shell must straddle the aim");
    }

    @Test
    @DisplayName("holding a trigger walks the cone up to the ceiling but no further")
    void sustainedFireSaturatesTheCone() {
        GunInstance scar = gun(WeaponId.IRON_CARBINE);
        float base = scar.currentSpread();
        float ceiling = scar.definition().spread().ceilingDegrees();
        float cooldown = scar.definition().cooldownSeconds();

        for (int shot = 0; shot < 20; shot++) {
            controller.fire(player, scar, true, true, volley);
            scar.update(cooldown, false, false);
            assertTrue(scar.currentSpread() <= ceiling + 1e-3f,
                "spread exceeded the ceiling: " + scar.currentSpread());
        }

        assertTrue(scar.currentSpread() > base * 2f,
            "twenty rounds should open the cone well past base: " + scar.currentSpread());

        // And it closes again when the trigger is released.
        for (int tick = 0; tick < 300; tick++) {
            scar.update(1f / 60f, false, false);
        }
        assertEquals(base, scar.currentSpread(), 1e-3f);
    }

    @Test
    @DisplayName("aiming tightens the cone a shot is drawn from")
    void aimingTightensTheCone() {
        GunInstance hip = gun(WeaponId.IRON_CARBINE);
        GunInstance ads = gun(WeaponId.IRON_CARBINE);

        for (int tick = 0; tick < 120; tick++) {
            hip.update(1f / 60f, false, false);
            ads.update(1f / 60f, false, true);
        }

        assertTrue(ads.currentSpread() < hip.currentSpread() * 0.5f,
            "ADS should be far tighter: hip " + hip.currentSpread() + " ads " + ads.currentSpread());
        assertEquals(
            hip.currentSpread() * hip.definition().spread().adsRatio(),
            ads.currentSpread(),
            1e-3f);
    }

    @Test
    @DisplayName("moving opens the cone, moving while aiming opens it less")
    void movingOpensTheCone() {
        GunInstance weapon = gun(WeaponId.IRON_CARBINE);

        float still = weapon.stanceTargetSpread(false, false);
        float moving = weapon.stanceTargetSpread(true, false);
        float movingAds = weapon.stanceTargetSpread(true, true);
        float stillAds = weapon.stanceTargetSpread(false, true);

        assertTrue(moving > still);
        assertTrue(movingAds < moving);
        assertTrue(movingAds > stillAds);
    }

    @Test
    @DisplayName("the dead do not shoot")
    void deadPlayersCannotFire() {
        GunInstance scar = gun(WeaponId.IRON_CARBINE);
        player.alive = false;
        assertEquals(0, controller.fire(player, scar, true, true, volley));
        assertTrue(volley.isEmpty());
    }

    @Test
    @DisplayName("switching weapons resets the cone and the cycle")
    void switchingWeaponsResetsState() {
        GunInstance weapon = gun(WeaponId.IRON_CARBINE);
        controller.fire(player, weapon, true, true, volley);
        assertTrue(weapon.currentSpread() > weapon.definition().spread().baseDegrees());
        assertFalse(weapon.isReady());

        assertTrue(weapon.switchTo(WeaponId.CATHEDRAL));
        assertEquals(WeaponId.CATHEDRAL, weapon.weaponId());
        assertEquals(WeaponRegistry.of(WeaponId.CATHEDRAL).spread().baseDegrees(), weapon.currentSpread(), 1e-4f);
        assertTrue(weapon.isReady(), "a fresh weapon is ready");
        assertEquals(0f, weapon.visualKick(), 1e-4f);

        assertFalse(weapon.switchTo(WeaponId.CATHEDRAL), "switching to the weapon already held is a no-op");
    }

    @Test
    @DisplayName("the volley buffer is reused, not reallocated, and always cleared first")
    void volleyIsReusedAndCleared() {
        GunInstance shotgun = gun(WeaponId.SCATTER_BENCH);
        controller.fire(player, shotgun, true, true, volley);
        assertEquals(shotgun.definition().pelletCount(), volley.count());

        // A trigger event that produces nothing must leave an empty volley behind.
        assertEquals(0, controller.fire(player, shotgun, true, true, volley), "still cycling");
        assertTrue(volley.isEmpty());
    }
}
