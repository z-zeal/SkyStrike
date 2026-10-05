package io.github.skystrike.server.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Recoil as the movement system sees it: a real push, a real spin, and a visual kick that is
 * only visual.
 */
class RecoilServiceTest {

    private static final float EPSILON = 1e-3f;

    private RecoilService recoil;
    private Player player;

    @BeforeEach
    void setUp() {
        recoil = new RecoilService();
        player = new Player(1, "Shooter", 0, 500f, 100f);
        player.aimAngle = 0f;
        player.grounded = true;
    }

    @Test
    @DisplayName("the multiplier is 1 standing still at the hip on the ground")
    void baselineMultiplierIsOne() {
        GunInstance gun = new GunInstance(WeaponId.SCAR_L);
        assertEquals(1f, recoil.multiplier(player, gun, false), EPSILON);
    }

    @Test
    @DisplayName("movement, air and burst each bend the multiplier the documented way")
    void multiplierRespondsToStance() {
        GunInstance gun = new GunInstance(WeaponId.SCAR_L);
        WeaponDefinition.RecoilProfile profile = gun.definition().recoil();

        player.vx = 200f;
        assertEquals(profile.movingMultiplier(), recoil.multiplier(player, gun, false), EPSILON);

        player.vx = 0f;
        player.grounded = false;
        assertEquals(WeaponConfig.AIRBORNE_RECOIL_MULTIPLIER,
            recoil.multiplier(player, gun, false), EPSILON);

        player.grounded = true;
        assertEquals(WeaponConfig.BURST_RECOIL_MULTIPLIER,
            recoil.multiplier(player, gun, true), EPSILON);

        // Aiming blends in: the gun has to spend time in ADS before it pays off.
        for (int tick = 0; tick < 120; tick++) {
            gun.update(1f / 60f, false, true);
        }
        assertEquals(profile.adsMultiplier(), recoil.multiplier(player, gun, false), 0.01f);
    }

    @Test
    @DisplayName("firing right pushes you left")
    void linearPushOpposesTheAim() {
        GunInstance gun = new GunInstance(WeaponId.AWP);
        float impulse = gun.definition().recoil().linearImpulse();

        player.aimAngle = 0f;
        recoil.apply(player, gun, player.aimAngle, 1f);

        assertEquals(-impulse, player.vx, EPSILON);
        assertEquals(0f, player.vy, EPSILON);
    }

    @Test
    @DisplayName("firing down while airborne buys height")
    void firingDownAirborneGainsHeight() {
        GunInstance gun = new GunInstance(WeaponId.AWP);
        player.grounded = false;
        player.aimAngle = -90f;
        player.vy = -200f;

        float multiplier = recoil.applyVolley(player, gun, player.aimAngle, false);

        assertEquals(WeaponConfig.AIRBORNE_RECOIL_MULTIPLIER, multiplier, EPSILON);
        float lift = gun.definition().recoil().linearImpulse() * multiplier;
        assertEquals(-200f + lift, player.vy, EPSILON);
        assertTrue(lift > 0f, "firing down must add upward velocity");

        // The lift is a meaningful fraction of a jump, but not a free flight.
        assertTrue(lift < PlayerConfig.JUMP_SPEED, "recoil must not out-jump a jump");
        assertTrue(lift > PlayerConfig.JUMP_SPEED * 0.05f, "recoil must be worth doing");
    }

    @Test
    @DisplayName("body torque follows the aim and the visual kick stays capped")
    void torqueAndVisualKick() {
        GunInstance gun = new GunInstance(WeaponId.AWP);
        WeaponDefinition.RecoilProfile profile = gun.definition().recoil();

        recoil.apply(player, gun, 0f, 1f);
        assertEquals(profile.angularDegreesPerSecond(), player.angularVelocity, EPSILON);
        assertEquals(profile.visualKickDegrees(), gun.visualKick(), EPSILON);

        player.angularVelocity = 0f;
        recoil.apply(player, gun, 180f, 1f);
        assertEquals(-profile.angularDegreesPerSecond(), player.angularVelocity, EPSILON);

        for (int i = 0; i < 50; i++) {
            recoil.apply(player, gun, 0f, 1f);
        }
        assertEquals(WeaponConfig.VISUAL_KICK_MAX_DEGREES, gun.visualKick(), EPSILON);

        // And it decays on its own at 120 deg/s.
        gun.update(WeaponConfig.VISUAL_KICK_MAX_DEGREES
            / WeaponConfig.VISUAL_KICK_DECAY_DEGREES_PER_SECOND, false, false);
        assertEquals(0f, gun.visualKick(), EPSILON);
    }

    @Test
    @DisplayName("aiming visibly reduces the shove")
    void aimingReducesTheShove() {
        GunInstance hipGun = new GunInstance(WeaponId.HK417);
        GunInstance adsGun = new GunInstance(WeaponId.HK417);
        for (int tick = 0; tick < 120; tick++) {
            adsGun.update(1f / 60f, false, true);
        }

        Player hipPlayer = new Player(1, "A", 0, 0f, 0f);
        Player adsPlayer = new Player(2, "B", 0, 0f, 0f);

        recoil.applyVolley(hipPlayer, hipGun, 0f, false);
        recoil.applyVolley(adsPlayer, adsGun, 0f, false);

        assertTrue(Math.abs(adsPlayer.vx) < Math.abs(hipPlayer.vx),
            "ADS " + adsPlayer.vx + " should shove less than hip " + hipPlayer.vx);
        assertEquals(
            hipPlayer.vx * adsGun.definition().recoil().adsMultiplier(),
            adsPlayer.vx,
            0.5f);
    }
}
