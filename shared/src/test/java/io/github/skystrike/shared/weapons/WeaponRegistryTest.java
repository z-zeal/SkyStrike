package io.github.skystrike.shared.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The weapon table is the balance of the whole game in one place, so it is asserted against the
 * numbers in the mechanics plan rather than against itself. Moved from the server-side
 * {@code WeaponStatsTest}: Phase 4 made the table shared (the client's predicted loadout needs
 * the same magazine and reload numbers), and equality of the copies the registry hands out is
 * what stands between "everyone sees the same balance" and a silent fork.
 */
class WeaponRegistryTest {

    private static final float EPSILON = 1e-4f;

    /** Rounds of {@code weapon} needed to kill a full-health player hitting {@code zone}. */
    private static int shotsToKill(WeaponId id, boolean headshots) {
        WeaponDefinition definition = WeaponRegistry.of(id);
        float perShot = definition.volleyDamage() * (headshots ? CombatConfig.HEAD_DAMAGE_MULTIPLIER : 1f);
        return (int) Math.ceil(CombatConfig.MAX_HEALTH / perShot);
    }

    @Test
    @DisplayName("all thirteen weapons are defined")
    void tableIsComplete() {
        assertEquals(WeaponId.values().length, WeaponRegistry.all().size());
        for (WeaponId id : WeaponId.values()) {
            WeaponDefinition definition = WeaponRegistry.of(id);
            assertNotNull(definition);
            assertEquals(id, definition.id());
            assertNotNull(definition.ballistics(), id + " has no ballistics");
            assertTrue(definition.damage() > 0f, id + " does no damage");
            assertTrue(definition.fireRateHz() > 0f, id + " has no fire rate");
            assertTrue(definition.magazineSize() > 0, id + " has no magazine");
            assertTrue(definition.reserveAmmo() > 0, id + " has no reserve");
            assertTrue(definition.reloadSeconds() > 0f, id + " reloads instantly");
        }
    }

    @Test
    @DisplayName("damage, rate, magazine, reserve and reload match the mechanics table")
    void matchesTheMechanicsTable() {
        assertWeapon(WeaponId.DESERT_EAGLE, 60f, 1.5f, 620f, 7, 42, 2.0f, FireMode.SEMI);
        assertWeapon(WeaponId.FAMAS, 25f, 10f, 740f, 25, 150, 2.0f, FireMode.AUTO);
        assertWeapon(WeaponId.SCAR_L, 32f, 7.5f, 780f, 20, 120, 2.2f, FireMode.AUTO);
        assertWeapon(WeaponId.P90, 18f, 14f, 420f, 50, 200, 2.4f, FireMode.AUTO);
        assertWeapon(WeaponId.KAR98K, 90f, 0.75f, 1200f, 5, 30, 3.2f, FireMode.BOLT);
        assertWeapon(WeaponId.AWP, 145f, 0.5f, 1500f, 5, 25, 3.8f, FireMode.BOLT);
        assertWeapon(WeaponId.HK417, 48f, 4f, 950f, 10, 60, 2.6f, FireMode.SEMI);
        assertWeapon(WeaponId.SAWED_OFF, 100f, 1.2f, 180f, 2, 18, 2.8f, FireMode.SHOTGUN);
        assertWeapon(WeaponId.BURST_RIFLE, 24f, 3.2f, 720f, 24, 144, 2.2f, FireMode.BURST);
        assertWeapon(WeaponId.ASSAULT_RIFLE, 30f, 6f, 750f, 30, 120, 2.2f, FireMode.AUTO);
        assertWeapon(WeaponId.SHOTGUN, 80f, 1.2f, 260f, 8, 40, 2.8f, FireMode.SHOTGUN);
        assertWeapon(WeaponId.SNIPER_RIFLE, 100f, 0.8f, 1500f, 5, 20, 3.0f, FireMode.SEMI);
        assertWeapon(WeaponId.SMG, 20f, 8f, 450f, 25, 150, 1.8f, FireMode.AUTO);
    }

    private static void assertWeapon(
            WeaponId id,
            float damage,
            float rate,
            float range,
            int magazine,
            int reserve,
            float reload,
            FireMode mode) {
        WeaponDefinition definition = WeaponRegistry.of(id);
        assertEquals(damage, definition.damage(), EPSILON, id + " damage");
        assertEquals(rate, definition.fireRateHz(), EPSILON, id + " fire rate");
        assertEquals(range, definition.ballistics().maxRange(), EPSILON, id + " range");
        assertEquals(magazine, definition.magazineSize(), id + " magazine");
        assertEquals(reserve, definition.reserveAmmo(), id + " reserve");
        assertEquals(reload, definition.reloadSeconds(), EPSILON, id + " reload");
        assertEquals(mode, definition.fireMode(), id + " fire mode");
    }

    @Test
    @DisplayName("the ADS spread ratios from §5.1 hold: AWP tightens to 18%, P90 only to 42%")
    void adsSpreadRatios() {
        assertEquals(0.18f, WeaponRegistry.of(WeaponId.AWP).spread().adsRatio(), EPSILON);
        assertEquals(0.42f, WeaponRegistry.of(WeaponId.P90).spread().adsRatio(), EPSILON);
        for (WeaponId id : WeaponId.values()) {
            float ratio = WeaponRegistry.of(id).spread().adsRatio();
            assertTrue(ratio > 0f && ratio < 1f, id + " ADS must tighten the cone: " + ratio);
        }
    }

    @Test
    @DisplayName("lookups return copies, never the shared instance")
    void lookupsReturnCopies() {
        WeaponDefinition first = WeaponRegistry.of(WeaponId.AWP);
        WeaponDefinition second = WeaponRegistry.of(WeaponId.AWP);

        assertNotSame(first, second, "two lookups must not hand out the same object");
        assertEquals(first, second, "but they must agree on every number");

        assertNotSame(WeaponRegistry.all().get(WeaponId.P90), WeaponRegistry.all().get(WeaponId.P90),
            "the bulk lookup copies too");
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
        assertTrue(WeaponRegistry.of(WeaponId.SAWED_OFF).volleyDamage() >= CombatConfig.MAX_HEALTH);
        assertTrue(WeaponRegistry.of(WeaponId.SHOTGUN).volleyDamage() >= CombatConfig.MAX_HEALTH);
    }

    @Test
    @DisplayName("a P90 kills in well under a second and an AWP cannot")
    void sustainedFireTimings() {
        WeaponDefinition p90 = WeaponRegistry.of(WeaponId.P90);
        float p90Seconds = (shotsToKill(WeaponId.P90, false) - 1) * p90.cooldownSeconds();
        assertTrue(p90Seconds < 0.7f, "P90 TTK was " + p90Seconds + "s");

        WeaponDefinition awp = WeaponRegistry.of(WeaponId.AWP);
        assertEquals(2f, awp.cooldownSeconds(), EPSILON, "half a round per second is a 2s cycle");

        // A miss with the AWP costs more than a whole P90 kill.
        assertTrue(awp.cooldownSeconds() > p90Seconds * 2f);
    }

    @Test
    @DisplayName("one trigger event produces the right number of rounds")
    void roundsPerTriggerEvent() {
        assertEquals(1, WeaponRegistry.of(WeaponId.SCAR_L).roundsPerTriggerEvent());
        assertEquals(1, WeaponRegistry.of(WeaponId.AWP).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.BURST_ROUNDS, WeaponRegistry.of(WeaponId.BURST_RIFLE).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.SHOTGUN_PELLETS, WeaponRegistry.of(WeaponId.SHOTGUN).roundsPerTriggerEvent());

        // No volley may exceed the buffer the fire controller reuses.
        for (WeaponId id : WeaponId.values()) {
            assertTrue(
                WeaponRegistry.of(id).roundsPerTriggerEvent() <= WeaponConfig.MAX_SHOTS_PER_VOLLEY,
                id + " produces more rounds than the volley buffer holds");
        }
    }

    @Test
    @DisplayName("a shotgun shell costs one magazine unit, a burst costs three")
    void magazineCostPerTriggerEvent() {
        assertEquals(1, WeaponRegistry.of(WeaponId.SHOTGUN).magazineCostPerTriggerEvent(),
            "the magazine counts shells, not pellets");
        assertEquals(1, WeaponRegistry.of(WeaponId.SAWED_OFF).magazineCostPerTriggerEvent());
        assertEquals(WeaponConfig.BURST_ROUNDS, WeaponRegistry.of(WeaponId.BURST_RIFLE).magazineCostPerTriggerEvent());
        assertEquals(1, WeaponRegistry.of(WeaponId.SCAR_L).magazineCostPerTriggerEvent());
    }

    @Test
    @DisplayName("base spread matches the weapon table and every profile is in range")
    void spreadProfilesAreInRange() {
        assertEquals(5.20f, WeaponRegistry.of(WeaponId.DESERT_EAGLE).spread().baseDegrees(), EPSILON);
        assertEquals(1.20f, WeaponRegistry.of(WeaponId.AWP).spread().baseDegrees(), EPSILON);
        assertEquals(0.75f, WeaponRegistry.of(WeaponId.SNIPER_RIFLE).spread().baseDegrees(), EPSILON);
        assertEquals(18.50f, WeaponRegistry.of(WeaponId.SHOTGUN).spread().baseDegrees(), EPSILON);
        assertEquals(15.00f, WeaponRegistry.of(WeaponId.SAWED_OFF).spread().baseDegrees(), EPSILON);

        for (WeaponId id : WeaponId.values()) {
            WeaponDefinition.SpreadProfile spread = WeaponRegistry.of(id).spread();

            assertTrue(spread.baseDegrees() > 0f, id + " has no spread");
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
            WeaponDefinition.RecoilProfile recoil = WeaponRegistry.of(id).recoil();

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
        assertTrue(WeaponRegistry.of(WeaponId.AWP).recoil().linearImpulse()
            > WeaponRegistry.of(WeaponId.P90).recoil().linearImpulse() * 5f);
        assertTrue(WeaponRegistry.of(WeaponId.AWP).recoil().adsMultiplier()
            < WeaponRegistry.of(WeaponId.P90).recoil().adsMultiplier(),
            "scoping a sniper rifle should pay more than aiming an SMG");
    }

    @Test
    @DisplayName("cooldown is the inverse of the quoted fire rate")
    void cooldownIsTheInverseOfRate() {
        for (WeaponId id : WeaponId.values()) {
            WeaponDefinition definition = WeaponRegistry.of(id);
            assertEquals(1f / definition.fireRateHz(), definition.cooldownSeconds(), EPSILON, id.toString());
        }
        assertEquals(0.1f, WeaponRegistry.of(WeaponId.FAMAS).cooldownSeconds(), EPSILON);
    }

    @Test
    @DisplayName("lookup by wire ordinal falls back instead of throwing")
    void lookupByOrdinal() {
        assertEquals(WeaponId.AWP, WeaponRegistry.ofOrdinal(WeaponId.AWP.ordinal()).id());
        assertEquals(WeaponId.DEFAULT, WeaponRegistry.ofOrdinal(-1).id());
        assertEquals(WeaponId.DEFAULT, WeaponRegistry.ofOrdinal(999).id());
    }

    @Test
    @DisplayName("wire display names resolve for guns and melee alike")
    void wireDisplayNames() {
        assertEquals("AWP", WeaponRegistry.displayNameForWireId(WeaponId.AWP.ordinal()));
        assertEquals("SCAR-L", WeaponRegistry.displayNameForWireId(WeaponId.SCAR_L.ordinal()));
        assertEquals("Combat Knife", WeaponRegistry.displayNameForWireId(MeleeId.COMBAT_KNIFE.wireId()));
        assertEquals("Baseball Bat", WeaponRegistry.displayNameForWireId(MeleeId.BASEBALL_BAT.wireId()));
        assertEquals(WeaponId.DEFAULT.displayName(),
            WeaponRegistry.displayNameForWireId(999999), "garbage falls back, not throws");
    }
}
