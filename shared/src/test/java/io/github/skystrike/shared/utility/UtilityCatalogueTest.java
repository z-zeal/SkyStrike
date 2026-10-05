package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The utility identity encoding and the mechanics §6 table. */
class UtilityCatalogueTest {

    @Test
    @DisplayName("utility wire ids occupy their own range, clear of guns and melee")
    void wireIdsDoNotCollideWithOtherWeaponRanges() {
        assertEquals(2000, UtilityId.WIRE_ID_BASE);
        assertTrue(UtilityId.WIRE_ID_BASE > MeleeId.WIRE_ID_BASE + MeleeId.values().length,
            "utility range must start above every melee wire id");
        assertTrue(MeleeId.WIRE_ID_BASE > WeaponId.values().length,
            "melee range must start above every gun ordinal");

        for (UtilityId id : UtilityId.values()) {
            assertTrue(UtilityId.isUtilityWireId(id.wireId()));
            assertFalse(MeleeId.isMeleeWireId(id.wireId()), id + " must not read as melee");
        }
    }

    @Test
    @DisplayName("wire ids round-trip, and foreign ids decode to null rather than guessing")
    void wireIdRoundTrip() {
        for (UtilityId id : UtilityId.values()) {
            assertEquals(id, UtilityId.fromWireId(id.wireId()));
            assertEquals(id, UtilityId.fromOrdinal(id.ordinal()));
        }
        assertNull(UtilityId.fromWireId(0), "a gun ordinal is not a utility");
        assertNull(UtilityId.fromWireId(MeleeId.WIRE_ID_BASE), "a melee id is not a utility");
        assertNull(UtilityId.fromWireId(UtilityId.WIRE_ID_BASE - 1));
        assertNull(UtilityId.fromWireId(UtilityId.WIRE_ID_BASE + UtilityId.values().length));
        assertThrows(IllegalArgumentException.class, () -> UtilityId.fromOrdinal(-1));
        assertThrows(IllegalArgumentException.class,
            () -> UtilityId.fromOrdinal(UtilityId.values().length));
    }

    @Test
    @DisplayName("the wire encoding is frozen: appending must never shift an existing id")
    void wireEncodingIsPinned() {
        assertEquals(2000, UtilityId.FRAG.wireId());
        assertEquals(2001, UtilityId.IMPACT.wireId());
        assertEquals(2002, UtilityId.SMOKE.wireId());
        assertEquals(2003, UtilityId.STUN.wireId());
        assertEquals(2004, UtilityId.MOLOTOV.wireId());
        assertEquals(2005, UtilityId.POISON_SMOKE.wireId());
        assertEquals(2006, UtilityId.FLASHBANG.wireId());
        assertEquals(2007, UtilityId.CLAYMORE.wireId());
        assertEquals(8, UtilityId.values().length, "appending a utility? pin its id here too");
    }

    @Test
    @DisplayName("every utility has a definition, and the registry hands out the same instance")
    void registryCoversEveryUtility() {
        List<UtilityDefinition> all = UtilityRegistry.all();
        assertEquals(UtilityId.values().length, all.size());

        for (int i = 0; i < UtilityId.values().length; i++) {
            UtilityId id = UtilityId.values()[i];
            assertEquals(id, all.get(i).id(), "all() must follow enum order");
            assertSame(UtilityRegistry.of(id), UtilityRegistry.of(id),
                "definitions are immutable records, so no copy is needed or wanted");
            assertSame(UtilityRegistry.of(id), UtilityRegistry.ofWireId(id.wireId()));
            assertSame(UtilityRegistry.of(id), UtilityRegistry.ofOrdinal(id.ordinal()));
            assertEquals(id.displayName(), UtilityRegistry.displayNameForWireId(id.wireId()));
        }
        assertNull(UtilityRegistry.ofWireId(7), "a gun id has no utility definition");
        assertEquals("", UtilityRegistry.displayNameForWireId(7));
    }

    @Test
    @DisplayName("the catalogue matches the mechanics §6 table exactly")
    void catalogueMatchesThePlan() {
        assertRow(UtilityId.FRAG, 800f, DetonationMode.FUSE, UtilityEffect.BLAST, 2.5f, 350f, 100f, 1.0f);
        assertRow(UtilityId.IMPACT, 850f, DetonationMode.CONTACT, UtilityEffect.BLAST, 0f, 280f, 85f, 1.2f);
        assertRow(UtilityId.SMOKE, 700f, DetonationMode.FUSE, UtilityEffect.SMOKE_CLOUD, 1.0f, 250f, 0f, 0.8f);
        assertRow(UtilityId.STUN, 720f, DetonationMode.FUSE, UtilityEffect.STUN, 1.8f, 600f, 0f, 1.0f);
        assertRow(UtilityId.MOLOTOV, 650f, DetonationMode.CONTACT, UtilityEffect.FIRE, 0f, 180f, 21f, 1.2f);
        assertRow(UtilityId.POISON_SMOKE, 680f, DetonationMode.FUSE, UtilityEffect.TOXIC_CLOUD,
            1.2f, 220f, 12f, 1.0f);
        assertRow(UtilityId.FLASHBANG, 750f, DetonationMode.FUSE, UtilityEffect.FLASH, 1.5f, 300f, 0f, 0.8f);

        assertEquals(950f, UtilityRegistry.of(UtilityId.FRAG).impulse());
        assertEquals(750f, UtilityRegistry.of(UtilityId.IMPACT).impulse());
        assertEquals(8.0f, UtilityRegistry.of(UtilityId.SMOKE).durationSeconds());
        assertEquals(6.0f, UtilityRegistry.of(UtilityId.MOLOTOV).durationSeconds());
        assertEquals(7.0f, UtilityRegistry.of(UtilityId.POISON_SMOKE).durationSeconds());
        assertEquals(3.0f, UtilityRegistry.of(UtilityId.FLASHBANG).durationSeconds());
    }

    @Test
    @DisplayName("only the claymore is provisional; the plan specifies everything else")
    void onlyTheClaymoreIsGuessed() {
        for (UtilityDefinition definition : UtilityRegistry.all()) {
            if (definition.id() == UtilityId.CLAYMORE) {
                assertTrue(definition.provisional(), "the plan's claymore row is all dashes");
            } else {
                assertFalse(definition.provisional(),
                    definition.id() + " has planned numbers and must not be marked provisional");
            }
        }
    }

    @Test
    @DisplayName("the claymore is a placed proximity device, not something you throw")
    void claymoreIsPlaced() {
        UtilityDefinition claymore = UtilityRegistry.of(UtilityId.CLAYMORE);
        assertEquals(DetonationMode.PROXIMITY, claymore.detonation());
        assertTrue(claymore.detonation().isPlaced());
        assertEquals(0f, claymore.throwForce(), "a placed device has no throw arc");
        assertEquals(UtilityEffect.DIRECTIONAL_BLAST, claymore.effect());
    }

    @Test
    @DisplayName("damage-over-time utilities report a damage rate, instant ones report none")
    void damageOverTimeRates() {
        UtilityDefinition molotov = UtilityRegistry.of(UtilityId.MOLOTOV);
        assertTrue(molotov.effect().isDamageOverTime());
        // 21 per 0.5 s tick is 42 dps: three and a half seconds to burn down 150 health.
        assertEquals(42f, molotov.damagePerSecond(UtilityConfig.DOT_TICK_SECONDS), 0.001f);
        assertEquals(24f, UtilityRegistry.of(UtilityId.POISON_SMOKE)
            .damagePerSecond(UtilityConfig.DOT_TICK_SECONDS), 0.001f);
        assertEquals(0f, UtilityRegistry.of(UtilityId.FRAG)
            .damagePerSecond(UtilityConfig.DOT_TICK_SECONDS),
            "a frag is instant; its damage is not a rate");
    }

    @Test
    @DisplayName("vision-blocking and persistence are properties of the effect, not of the item")
    void effectFlags() {
        assertTrue(UtilityEffect.SMOKE_CLOUD.blocksVision());
        assertTrue(UtilityEffect.TOXIC_CLOUD.blocksVision());
        assertFalse(UtilityEffect.BLAST.blocksVision());
        assertTrue(UtilityRegistry.of(UtilityId.SMOKE).isPersistent());
        assertFalse(UtilityRegistry.of(UtilityId.FRAG).isPersistent());
    }

    @Test
    @DisplayName("definitions reject impossible numbers rather than storing them")
    void definitionsValidate() {
        assertThrows(IllegalArgumentException.class, () -> new UtilityDefinition(
            UtilityId.FRAG, 800f, DetonationMode.FUSE, UtilityEffect.BLAST,
            2.5f, -1f, 100f, 950f, 0f, 1f, 2, false));
        assertThrows(IllegalArgumentException.class, () -> new UtilityDefinition(
            null, 800f, DetonationMode.FUSE, UtilityEffect.BLAST,
            2.5f, 350f, 100f, 950f, 0f, 1f, 2, false));
    }

    private static void assertRow(
        UtilityId id,
        float throwForce,
        DetonationMode detonation,
        UtilityEffect effect,
        float fuse,
        float radius,
        float damage,
        float cooldown) {
        UtilityDefinition definition = UtilityRegistry.of(id);
        assertNotNull(definition);
        assertEquals(throwForce, definition.throwForce(), id + " throw force");
        assertEquals(detonation, definition.detonation(), id + " detonation");
        assertEquals(effect, definition.effect(), id + " effect");
        assertEquals(fuse, definition.fuseSeconds(), id + " fuse");
        assertEquals(radius, definition.radius(), id + " radius");
        assertEquals(damage, definition.damage(), id + " damage");
        assertEquals(cooldown, definition.cooldownSeconds(), id + " cooldown");
        assertEquals(UtilityConfig.DEFAULT_CARRIED_COUNT, definition.carriedCount(), id + " count");
    }
}
