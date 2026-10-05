package io.github.skystrike.server.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The weapon table is the balance of the whole game in one place, so it is asserted against the
 * numbers in the mechanics plan rather than against itself.
 */
class WeaponStatsTest {

    private static final float EPSILON = 1e-4f;

    /** Rounds of {@code weapon} needed to kill a full-health player hitting {@code zone}. */
    private static int shotsToKill(WeaponId id, boolean headshots) {
        WeaponStats stats = WeaponStats.of(id);
        float perShot = stats.volleyDamage() * (headshots ? CombatConfig.HEAD_DAMAGE_MULTIPLIER : 1f);
        return (int) Math.ceil(CombatConfig.MAX_HEALTH / perShot);
    }

    @Test
    @DisplayName("all thirteen weapons are defined")
    void tableIsComplete() {
        assertEquals(WeaponId.values().length, WeaponStats.all().size());
        for (WeaponId id : WeaponId.values()) {
            WeaponStats stats = WeaponStats.of(id);
            assertNotNull(stats);
            assertEquals(id, stats.id());
            assertNotNull(stats.ballistics(), id + " has no ballistics");
            assertTrue(stats.damage() > 0f, id + " does no damage");
            assertTrue(stats.fireRateHz() > 0f, id + " has no fire rate");
            assertTrue(stats.magazineSize() > 0, id + " has no magazine");
            assertTrue(stats.reloadSeconds() > 0f, id + " reloads instantly");
        }
    }

    @Test
    @DisplayName("damage, rate, magazine and mode match the mechanics table")
    void matchesTheMechanicsTable() {
        assertWeapon(WeaponId.DESERT_EAGLE, 60f, 1.5f, 7, FireMode.SEMI);
        assertWeapon(WeaponId.FAMAS, 25f, 10f, 25, FireMode.AUTO);
        assertWeapon(WeaponId.SCAR_L, 32f, 7.5f, 20, FireMode.AUTO);
        assertWeapon(WeaponId.P90, 18f, 14f, 50, FireMode.AUTO);
        assertWeapon(WeaponId.KAR98K, 90f, 0.75f, 5, FireMode.BOLT);
        assertWeapon(WeaponId.AWP, 145f, 0.5f, 5, FireMode.BOLT);
        assertWeapon(WeaponId.HK417, 48f, 4f, 10, FireMode.SEMI);
        assertWeapon(WeaponId.SAWED_OFF, 100f, 1.2f, 2, FireMode.SHOTGUN);
        assertWeapon(WeaponId.BURST_RIFLE, 24f, 3.2f, 24, FireMode.BURST);
        assertWeapon(WeaponId.ASSAULT_RIFLE, 30f, 6f, 30, FireMode.AUTO);
        assertWeapon(WeaponId.SHOTGUN, 80f, 1.2f, 8, FireMode.SHOTGUN);
        assertWeapon(WeaponId.SNIPER_RIFLE, 100f, 0.8f, 5, FireMode.SEMI);
        assertWeapon(WeaponId.SMG, 20f, 8f, 25, FireMode.AUTO);
    }

    private static void assertWeapon(WeaponId id, float damage, float rate, int magazine, FireMode mode) {
        WeaponStats stats = WeaponStats.of(id);
        assertEquals(damage, stats.damage(), EPSILON, id + " damage");
        assertEquals(rate, stats.fireRateHz(), EPSILON, id + " fire rate");
        assertEquals(magazine, stats.magazineSize(), id + " magazine");
        assertEquals(mode, stats.fireMode(), id + " fire mode");
    }

    @Test
    @DisplayName("the time-to-kill targets from the roadmap hold at 150 HP")
    void timeToKillTargets() {
        // The two the roadmap names explicitly.
        assertEquals(1, shotsToKill(WeaponId.AWP, true), "an AWP headshot must be one shot");
        assertEquals(3, shotsToKill(WeaponId.DESERT_EAGLE, false), "a Deagle must be three body shots");

        assertEquals(2, shotsToKill(WeaponId.AWP, false));
        assertEquals(1, shotsToKill(WeaponId.KAR98K, true));
        assertEquals(2, shotsToKill(WeaponId.KAR98K, false));
        assertEquals(1, shotsToKill(WeaponId.SNIPER_RIFLE, true));
        assertEquals(2, shotsToKill(WeaponId.SNIPER_RIFLE, false));
        assertEquals(2, shotsToKill(WeaponId.DESERT_EAGLE, true));
        assertEquals(4, shotsToKill(WeaponId.HK417, false));
        assertEquals(5, shotsToKill(WeaponId.SCAR_L, false));
        assertEquals(5, shotsToKill(WeaponId.ASSAULT_RIFLE, false));
        assertEquals(6, shotsToKill(WeaponId.FAMAS, false));
        assertEquals(8, shotsToKill(WeaponId.SMG, false));
        assertEquals(9, shotsToKill(WeaponId.P90, false));

        // Both shotguns kill with one shell at contact range, before falloff.
        assertTrue(WeaponStats.of(WeaponId.SAWED_OFF).volleyDamage() >= CombatConfig.MAX_HEALTH);
        assertTrue(WeaponStats.of(WeaponId.SHOTGUN).volleyDamage() >= CombatConfig.MAX_HEALTH);
    }

    @Test
    @DisplayName("a P90 kills in well under a second and an AWP cannot")
    void sustainedFireTimings() {
        WeaponStats p90 = WeaponStats.of(WeaponId.P90);
        float p90Seconds = (shotsToKill(WeaponId.P90, false) - 1) * p90.cooldownSeconds();
        assertTrue(p90Seconds < 0.7f, "P90 TTK was " + p90Seconds + "s");

        WeaponStats awp = WeaponStats.of(WeaponId.AWP);
        assertEquals(2f, awp.cooldownSeconds(), EPSILON, "half a round per second is a 2s cycle");

        // A miss with the AWP costs more than a whole P90 kill.
        assertTrue(awp.cooldownSeconds() > p90Seconds * 2f);
    }

    @Test
    @DisplayName("one trigger event produces the right number of rounds")
    void roundsPerTriggerEvent() {
        assertEquals(1, WeaponStats.of(WeaponId.SCAR_L).roundsPerTriggerEvent());
        assertEquals(1, WeaponStats.of(WeaponId.AWP).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.BURST_ROUNDS, WeaponStats.of(WeaponId.BURST_RIFLE).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.SHOTGUN_PELLETS, WeaponStats.of(WeaponId.SHOTGUN).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.SHOTGUN_PELLETS, WeaponStats.of(WeaponId.SAWED_OFF).roundsPerTriggerEvent());

        // No volley may exceed the buffer the fire controller reuses.
        for (WeaponId id : WeaponId.values()) {
            assertTrue(
                WeaponStats.of(id).roundsPerTriggerEvent() <= WeaponConfig.MAX_SHOTS_PER_VOLLEY,
                id + " produces more rounds than the volley buffer holds");
        }
    }

    @Test
    @DisplayName("base spread matches the weapon table and every profile is in range")
    void spreadProfilesAreInRange() {
        assertEquals(5.20f, WeaponStats.of(WeaponId.DESERT_EAGLE).spread().baseDegrees(), EPSILON);
        assertEquals(1.20f, WeaponStats.of(WeaponId.AWP).spread().baseDegrees(), EPSILON);
        assertEquals(0.75f, WeaponStats.of(WeaponId.SNIPER_RIFLE).spread().baseDegrees(), EPSILON);
        assertEquals(18.50f, WeaponStats.of(WeaponId.SHOTGUN).spread().baseDegrees(), EPSILON);
        assertEquals(15.00f, WeaponStats.of(WeaponId.SAWED_OFF).spread().baseDegrees(), EPSILON);

        for (WeaponId id : WeaponId.values()) {
            WeaponStats.SpreadProfile spread = WeaponStats.of(id).spread();

            assertTrue(spread.baseDegrees() > 0f, id + " has no spread");
            assertTrue(spread.adsRatio() > 0f && spread.adsRatio() < 1f,
                id + " ADS must tighten the cone: " + spread.adsRatio());
            assertTrue(spread.movingMultiplier() >= WeaponConfig.MOVING_SPREAD_MIN_MULTIPLIER
                    && spread.movingMultiplier() <= WeaponConfig.MOVING_SPREAD_MAX_MULTIPLIER,
                id + " moving multiplier out of range: " + spread.movingMultiplier());
            assertTrue(spread.kickDegrees() >= WeaponConfig.SPREAD_KICK_MIN_DEGREES
                    && spread.kickDegrees() <= WeaponConfig.SPREAD_KICK_MAX_DEGREES,
                id + " spread kick out of range: " + spread.kickDegrees());
            assertTrue(spread.ceilingMultiplier() >= WeaponConfig.SPREAD_CEILING_MIN_MULTIPLIER
                    && spread.ceilingMultiplier() <= WeaponConfig.SPREAD_CEILING_MAX_MULTIPLIER,
                id + " spread ceiling out of range: " + spread.ceilingMultiplier());
            assertTrue(spread.recoveryDegreesPerSecond() >= WeaponConfig.SPREAD_RECOVERY_MIN
                    && spread.recoveryDegreesPerSecond() <= WeaponConfig.SPREAD_RECOVERY_MAX,
                id + " recovery out of range: " + spread.recoveryDegreesPerSecond());
            assertEquals(spread.baseDegrees() * spread.ceilingMultiplier(), spread.ceilingDegrees(), EPSILON);
        }
    }

    @Test
    @DisplayName("recoil profiles are in range and scale with damage")
    void recoilProfilesAreInRange() {
        for (WeaponId id : WeaponId.values()) {
            WeaponStats.RecoilProfile recoil = WeaponStats.of(id).recoil();

            assertTrue(recoil.adsMultiplier() >= WeaponConfig.ADS_RECOIL_MIN_MULTIPLIER
                    && recoil.adsMultiplier() <= WeaponConfig.ADS_RECOIL_MAX_MULTIPLIER,
                id + " ADS recoil out of range: " + recoil.adsMultiplier());
            assertTrue(recoil.movingMultiplier() >= WeaponConfig.MOVING_RECOIL_MIN_MULTIPLIER
                    && recoil.movingMultiplier() <= WeaponConfig.MOVING_RECOIL_MAX_MULTIPLIER,
                id + " moving recoil out of range: " + recoil.movingMultiplier());
            assertTrue(recoil.linearImpulse() > 0f, id + " has no push");
            assertTrue(recoil.angularDegreesPerSecond() > 0f, id + " has no torque");
            assertTrue(recoil.visualKickDegrees() > 0f
                    && recoil.visualKickDegrees() <= WeaponConfig.VISUAL_KICK_MAX_DEGREES,
                id + " visual kick out of range: " + recoil.visualKickDegrees());
        }

        // The heavy hitters shove hardest; the P90 barely moves you.
        assertTrue(WeaponStats.of(WeaponId.AWP).recoil().linearImpulse()
            > WeaponStats.of(WeaponId.P90).recoil().linearImpulse() * 5f);
        assertTrue(WeaponStats.of(WeaponId.AWP).recoil().adsMultiplier()
            < WeaponStats.of(WeaponId.P90).recoil().adsMultiplier(),
            "scoping a sniper rifle should pay more than aiming an SMG");
    }

    @Test
    @DisplayName("cooldown is the inverse of the quoted fire rate")
    void cooldownIsTheInverseOfRate() {
        for (WeaponId id : WeaponId.values()) {
            WeaponStats stats = WeaponStats.of(id);
            assertEquals(1f / stats.fireRateHz(), stats.cooldownSeconds(), EPSILON, id.toString());
        }
        assertEquals(0.1f, WeaponStats.of(WeaponId.FAMAS).cooldownSeconds(), EPSILON);
    }

    @Test
    @DisplayName("lookup by wire ordinal falls back instead of throwing")
    void lookupByOrdinal() {
        assertEquals(WeaponId.AWP, WeaponStats.ofOrdinal(WeaponId.AWP.ordinal()).id());
        assertEquals(WeaponId.DEFAULT, WeaponStats.ofOrdinal(-1).id());
        assertEquals(WeaponId.DEFAULT, WeaponStats.ofOrdinal(999).id());
    }
}
