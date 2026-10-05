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
 * The weapon table is the balance of the whole game in one place. The rows are generated from
 * the sprite catalog by documented conversions, so the suite asserts two things: a handful of
 * flagship rows verbatim (a silent regeneration with different rules must fail loudly), and the
 * table-wide invariants of mechanics §4.2–§4.5 over all 89 guns — the generated values must
 * stay inside the bounds the rest of the combat code is tuned against.
 */
class WeaponRegistryTest {

    private static final float EPSILON = 1e-4f;

    /** Trigger events of {@code weapon} needed to kill a full-health player. */
    private static int volleysToKill(WeaponId id, boolean headshots) {
        WeaponDefinition definition = WeaponRegistry.of(id);
        float perVolley = definition.volleyDamage() * (headshots ? CombatConfig.HEAD_DAMAGE_MULTIPLIER : 1f);
        return (int) Math.ceil(CombatConfig.MAX_HEALTH / perVolley);
    }

    @Test
    @DisplayName("every weapon of the catalog is defined and plausible")
    void tableIsComplete() {
        assertEquals(WeaponId.values().length, WeaponRegistry.all().size());
        assertEquals(89, WeaponId.values().length, "the sprite-backed catalog is 89 guns");
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
            assertTrue(definition.pelletCount() >= 1, id + " launches nothing");
        }
    }

    @Test
    @DisplayName("flagship rows match the generated conversion of the catalog stats")
    void matchesTheGeneratedTable() {
        // One per archetype; regenerating with different rules must break these.
        assertWeapon(WeaponId.IRON_SIDEARM, 27.6f, 6.3333f, 392f, 15, 75, 1.35f, FireMode.SEMI);
        assertWeapon(WeaponId.IRON_CARBINE, 32.2f, 12f, 770f, 30, 120, 2.1f, FireMode.AUTO);
        assertWeapon(WeaponId.SMOKE_STITCH, 20.7f, 15f, 476f, 50, 250, 2.1f, FireMode.AUTO);
        assertWeapon(WeaponId.CATHEDRAL, 126.5f, 0.5333f, 1890f, 5, 25, 3.5f, FireMode.BOLT);
        assertWeapon(WeaponId.MAGPIE, 149.5f, 0.4667f, 2100f, 5, 25, 3.8f, FireMode.SEMI);
        assertWeapon(WeaponId.SCATTER_BENCH, 20.8f, 1.25f, 224f, 8, 64, 3.4f, FireMode.PUMP);
        assertWeapon(WeaponId.CINDER_TUBE, 23.4f, 1.6667f, 126f, 2, 16, 1.9f, FireMode.BREAK);
        assertWeapon(WeaponId.HALCYON_16, 32.2f, 3.3611f, 672f, 25, 100, 2.15f, FireMode.BURST);
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
    @DisplayName("ADS tightens every cone: snipers to 18%, SMGs only to 42%")
    void adsSpreadRatios() {
        assertEquals(0.18f, WeaponRegistry.of(WeaponId.CATHEDRAL).spread().adsRatio(), EPSILON);
        assertEquals(0.42f, WeaponRegistry.of(WeaponId.WASP_NEST).spread().adsRatio(), EPSILON);
        for (WeaponId id : WeaponId.values()) {
            float ratio = WeaponRegistry.of(id).spread().adsRatio();
            assertTrue(ratio > 0f && ratio < 1f, id + " ADS must tighten the cone: " + ratio);
        }
    }

    @Test
    @DisplayName("lookups return copies, never the shared instance")
    void lookupsReturnCopies() {
        WeaponDefinition first = WeaponRegistry.of(WeaponId.CATHEDRAL);
        WeaponDefinition second = WeaponRegistry.of(WeaponId.CATHEDRAL);

        assertNotSame(first, second, "two lookups must not hand out the same object");
        assertEquals(first, second, "but they must agree on every number");

        assertNotSame(WeaponRegistry.all().get(WeaponId.SMOKE_STITCH),
            WeaponRegistry.all().get(WeaponId.SMOKE_STITCH),
            "the bulk lookup copies too");
    }

    @Test
    @DisplayName("the time-to-kill identities hold at 150 HP")
    void timeToKillTargets() {
        // Snipers: every one of them two-shots the body and one-shots the head.
        for (WeaponId id : WeaponId.values()) {
            if (WeaponRegistry.of(id).ballistics().weaponClass() == WeaponClass.SNIPER) {
                assertEquals(1, volleysToKill(id, true), id + " headshot must be one shot");
                assertEquals(2, volleysToKill(id, false), id + " body kill must be two shots");
            }
        }

        // The default rifle keeps the familiar five-round body kill.
        assertEquals(5, volleysToKill(WeaponId.IRON_CARBINE, false));

        // Pump and break shotguns one-shell at contact range; the fast autoloaders do not.
        assertEquals(1, volleysToKill(WeaponId.SCATTER_BENCH, false));
        assertEquals(1, volleysToKill(WeaponId.CINDER_TUBE, false));
        assertEquals(2, volleysToKill(WeaponId.ROOM_SWEEPER, false));
        assertEquals(2, volleysToKill(WeaponId.ASH_HOPPER_12, false));

        // Magnum revolvers two-shot the body — the hand-cannon identity.
        assertEquals(2, volleysToKill(WeaponId.LONGSPUR_44, false));

        // No gun one-shots the body: the head multiplier must stay worth aiming for.
        for (WeaponId id : WeaponId.values()) {
            if (WeaponRegistry.of(id).pelletCount() == 1) {
                assertTrue(volleysToKill(id, false) >= 2, id + " must not one-shot the body");
            }
        }
    }

    @Test
    @DisplayName("a PDW kills in well under a second and a bolt sniper cannot")
    void sustainedFireTimings() {
        WeaponDefinition pdw = WeaponRegistry.of(WeaponId.SMOKE_STITCH);
        float pdwSeconds = (volleysToKill(WeaponId.SMOKE_STITCH, false) - 1) * pdw.cooldownSeconds();
        assertTrue(pdwSeconds < 0.7f, "PDW TTK was " + pdwSeconds + "s");

        WeaponDefinition cathedral = WeaponRegistry.of(WeaponId.CATHEDRAL);
        assertTrue(cathedral.cooldownSeconds() > 1.5f, "a .338 bolt gun is a commitment");

        // A miss with the bolt gun costs more than a whole PDW kill.
        assertTrue(cathedral.cooldownSeconds() > pdwSeconds * 2f);
    }

    @Test
    @DisplayName("one trigger event produces the right number of rounds")
    void roundsPerTriggerEvent() {
        assertEquals(1, WeaponRegistry.of(WeaponId.IRON_CARBINE).roundsPerTriggerEvent());
        assertEquals(1, WeaponRegistry.of(WeaponId.CATHEDRAL).roundsPerTriggerEvent());
        assertEquals(WeaponConfig.BURST_ROUNDS, WeaponRegistry.of(WeaponId.HALCYON_16).roundsPerTriggerEvent());
        assertEquals(8, WeaponRegistry.of(WeaponId.SCATTER_BENCH).roundsPerTriggerEvent());
        assertEquals(4, WeaponRegistry.of(WeaponId.BRASS_JUDGE).roundsPerTriggerEvent(),
            "the snake-shot revolver is a pellet weapon in a sidearm class");

        // No volley may exceed the buffer the fire controller reuses.
        for (WeaponId id : WeaponId.values()) {
            assertTrue(
                WeaponRegistry.of(id).roundsPerTriggerEvent() <= WeaponConfig.MAX_SHOTS_PER_VOLLEY,
                id + " produces more rounds than the volley buffer holds");
        }
    }

    @Test
    @DisplayName("a shell costs one magazine unit, a burst costs three")
    void magazineCostPerTriggerEvent() {
        assertEquals(1, WeaponRegistry.of(WeaponId.SCATTER_BENCH).magazineCostPerTriggerEvent(),
            "the magazine counts shells, not pellets");
        assertEquals(1, WeaponRegistry.of(WeaponId.CINDER_TUBE).magazineCostPerTriggerEvent());
        assertEquals(WeaponConfig.BURST_ROUNDS, WeaponRegistry.of(WeaponId.HALCYON_16).magazineCostPerTriggerEvent());
        assertEquals(1, WeaponRegistry.of(WeaponId.IRON_CARBINE).magazineCostPerTriggerEvent());
    }

    @Test
    @DisplayName("pellet weapons are exactly the catalog's multi-projectile guns")
    void pelletWeapons() {
        for (WeaponId id : WeaponId.values()) {
            WeaponDefinition definition = WeaponRegistry.of(id);
            if (definition.firesPellets()) {
                WeaponClass weaponClass = definition.ballistics().weaponClass();
                assertTrue(
                    weaponClass == WeaponClass.SHOTGUN || id == WeaponId.BRASS_JUDGE,
                    id + " fires pellets but is a " + weaponClass);
            }
            if (definition.ballistics().weaponClass() == WeaponClass.SHOTGUN) {
                assertEquals(8, definition.pelletCount(), id + " must fire a full shell");
            }
        }
    }

    @Test
    @DisplayName("base spread matches the generated table and every profile is in range")
    void spreadProfilesAreInRange() {
        assertEquals(5.44f, WeaponRegistry.of(WeaponId.IRON_SIDEARM).spread().baseDegrees(), EPSILON);
        assertEquals(0.48f, WeaponRegistry.of(WeaponId.CATHEDRAL).spread().baseDegrees(), EPSILON);
        assertEquals(3.84f, WeaponRegistry.of(WeaponId.IRON_CARBINE).spread().baseDegrees(), EPSILON);
        assertEquals(12.80f, WeaponRegistry.of(WeaponId.SCATTER_BENCH).spread().baseDegrees(), EPSILON);
        assertEquals(16.64f, WeaponRegistry.of(WeaponId.CINDER_TUBE).spread().baseDegrees(), EPSILON);

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
    @DisplayName("recoil profiles are in range and scale with the catalog's kick scalar")
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

        // The heavy hitters shove hardest; a PDW barely moves you.
        assertTrue(WeaponRegistry.of(WeaponId.MAGPIE).recoil().linearImpulse()
            > WeaponRegistry.of(WeaponId.SMOKE_STITCH).recoil().linearImpulse() * 3f);
        assertTrue(WeaponRegistry.of(WeaponId.MAGPIE).recoil().adsMultiplier()
            < WeaponRegistry.of(WeaponId.SMOKE_STITCH).recoil().adsMultiplier(),
            "scoping a sniper rifle should pay more than aiming a PDW");
    }

    @Test
    @DisplayName("cooldown is the inverse of the quoted fire rate")
    void cooldownIsTheInverseOfRate() {
        for (WeaponId id : WeaponId.values()) {
            WeaponDefinition definition = WeaponRegistry.of(id);
            assertEquals(1f / definition.fireRateHz(), definition.cooldownSeconds(), EPSILON, id.toString());
        }
        assertEquals(1f / 12f, WeaponRegistry.of(WeaponId.IRON_CARBINE).cooldownSeconds(), EPSILON);
    }

    @Test
    @DisplayName("lookup by wire ordinal falls back instead of throwing")
    void lookupByOrdinal() {
        assertEquals(WeaponId.CATHEDRAL, WeaponRegistry.ofOrdinal(WeaponId.CATHEDRAL.ordinal()).id());
        assertEquals(WeaponId.DEFAULT, WeaponRegistry.ofOrdinal(-1).id());
        assertEquals(WeaponId.DEFAULT, WeaponRegistry.ofOrdinal(999).id());
    }

    @Test
    @DisplayName("wire display names resolve for guns and melee alike")
    void wireDisplayNames() {
        assertEquals("Cathedral", WeaponRegistry.displayNameForWireId(WeaponId.CATHEDRAL.ordinal()));
        assertEquals("Iron Carbine", WeaponRegistry.displayNameForWireId(WeaponId.IRON_CARBINE.ordinal()));
        assertEquals("Trench Knuckle", WeaponRegistry.displayNameForWireId(MeleeId.TRENCH_KNUCKLE.wireId()));
        assertEquals("Winter Katana", WeaponRegistry.displayNameForWireId(MeleeId.WINTER_KATANA.wireId()));
        assertEquals(WeaponId.DEFAULT.displayName(),
            WeaponRegistry.displayNameForWireId(999999), "garbage falls back, not throws");
    }
}
