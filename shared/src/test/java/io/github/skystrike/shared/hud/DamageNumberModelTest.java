package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the floating damage number contract: which events become numbers, what they print, and
 * how they age. The widget only projects and draws; everything testable lives here.
 */
class DamageNumberModelTest {

    private static final int LOCAL = 7;
    private static final int OTHER = 3;

    private DamageNumberModel model;

    @BeforeEach
    void setUp() {
        model = new DamageNumberModel();
        model.setLocalPlayerId(LOCAL);
    }

    private static PacketDamageEvent bullet(
            int attacker, int target, float amount, HitZone zone, boolean killed) {
        return new PacketDamageEvent(
            attacker, target, amount, 100f, zone,
            WeaponId.IRON_SIDEARM.ordinal(), 1500f, 900f, 120f, killed);
    }

    @Test
    @DisplayName("only damage the local player deals to somebody else becomes a number")
    void gating() {
        model.add(bullet(LOCAL, OTHER, 20f, HitZone.BODY, false), 0L);
        assertEquals(1, model.size());

        model.add(bullet(OTHER, LOCAL, 20f, HitZone.BODY, false), 0L);
        assertEquals(1, model.size(), "damage taken is the vignette's business");

        model.add(bullet(LOCAL, LOCAL, 20f, HitZone.BODY, false), 0L);
        assertEquals(1, model.size(), "self-inflicted damage is the vignette's business");

        model.add(bullet(OTHER, OTHER, 20f, HitZone.BODY, false), 0L);
        assertEquals(1, model.size(), "other people's fights stay out of the model");

        model.add(bullet(LOCAL, OTHER, 0f, HitZone.BODY, false), 0L);
        assertEquals(1, model.size(), "zero damage prints nothing");

        model.add(null, 0L);
        assertEquals(1, model.size());
    }

    @Test
    @DisplayName("an unknown local id files nothing: no id, no numbers")
    void unknownLocalId() {
        DamageNumberModel fresh = new DamageNumberModel();
        fresh.add(bullet(LOCAL, OTHER, 20f, HitZone.BODY, false), 0L);
        assertEquals(0, fresh.size());
    }

    @Test
    @DisplayName("a filed event keeps its anchor, amount and flags")
    void entryContents() {
        model.add(bullet(LOCAL, OTHER, 23.6f, HitZone.HEAD, true), 1000L);
        List<DamageNumberModel.Entry> visible = model.visible(1000L);
        assertEquals(1, visible.size());
        DamageNumberModel.Entry entry = visible.get(0);
        assertEquals(1500f, entry.worldX());
        assertEquals(900f, entry.worldY());
        assertEquals(24, entry.amount(), "23.6 rounds to the whole number the HUD prints");
        assertTrue(entry.headshot());
        assertTrue(entry.killed());
        assertEquals(0f, entry.ageSeconds(1000L));
        assertEquals(1f, entry.alpha(1000L));
    }

    @Test
    @DisplayName("numbers hold, fade and rise on the wall clock")
    void lifecycle() {
        model.add(bullet(LOCAL, OTHER, 20f, HitZone.BODY, false), 0L);
        DamageNumberModel.Entry entry = model.visible(0L).get(0);

        assertEquals(1f, entry.alpha((long) (DamageNumberModel.HOLD_SECONDS * 1000f)),
            "held at full opacity");
        assertEquals(0f, entry.riseUnits(0L), "starts at the impact point");
        assertTrue(entry.riseUnits((long) (DamageNumberModel.LIFETIME_SECONDS * 500f))
            > DamageNumberModel.RISE_UNITS / 2f, "the ease-out rise is front-loaded");

        long end = (long) (DamageNumberModel.LIFETIME_SECONDS * 1000f);
        assertEquals(0f, entry.alpha(end));
        assertEquals(DamageNumberModel.RISE_UNITS, entry.riseUnits(end), 1e-3f);
        assertTrue(model.visible(end).isEmpty(), "reading the model retires expired numbers");
        assertEquals(0, model.size());
    }

    @Test
    @DisplayName("a burst cannot hoard the model: oldest entries drop at capacity")
    void capacity() {
        for (int i = 0; i < DamageNumberModel.CAPACITY + 6; i++) {
            model.add(bullet(LOCAL, OTHER, 10f + i, HitZone.BODY, false), i);
        }
        assertEquals(DamageNumberModel.CAPACITY, model.size());
        List<DamageNumberModel.Entry> visible = model.visible(0L);
        assertEquals(DamageNumberModel.CAPACITY, visible.size());
        assertEquals(16, visible.get(0).amount(),
            "the oldest survivors are the newest CAPACITY entries");
    }

    @Test
    @DisplayName("the falloff ratio measures a gun hit against its muzzle damage")
    void falloffRatioGuns() {
        float muzzle = WeaponRegistry.of(WeaponId.IRON_SIDEARM).damage();

        PacketDamageEvent full = bullet(LOCAL, OTHER, muzzle, HitZone.BODY, false);
        assertEquals(1f, DamageNumberModel.falloffRatio(full), 1e-4f);

        PacketDamageEvent half = bullet(LOCAL, OTHER, muzzle / 2f, HitZone.BODY, false);
        assertEquals(0.5f, DamageNumberModel.falloffRatio(half), 1e-4f);

        // A headshot is doubled after falloff, so the reference doubles with it: a full-range
        // headshot still reads 1.0 rather than 2.0.
        PacketDamageEvent headshot = new PacketDamageEvent(
            LOCAL, OTHER, muzzle * 2f, 100f, HitZone.HEAD,
            WeaponId.IRON_SIDEARM.ordinal(), 0f, 0f, 10f, false);
        assertEquals(1f, DamageNumberModel.falloffRatio(headshot), 1e-4f);

        PacketDamageEvent clamped = bullet(LOCAL, OTHER, muzzle * 5f, HitZone.BODY, false);
        assertEquals(1f, DamageNumberModel.falloffRatio(clamped), "ratios clamp at 1");
    }

    @Test
    @DisplayName("melee, utility and gadget hits have no falloff to show and read 1.0")
    void falloffRatioNonGuns() {
        PacketDamageEvent melee = new PacketDamageEvent(
            LOCAL, OTHER, 30f, 100f, HitZone.BODY,
            MeleeId.WIRE_ID_BASE + MeleeId.DEFAULT.ordinal(), 0f, 0f, 0f, false);
        assertEquals(1f, DamageNumberModel.falloffRatio(melee));

        PacketDamageEvent utility = new PacketDamageEvent(
            LOCAL, OTHER, 90f, 100f, HitZone.BODY,
            UtilityId.WIRE_ID_BASE + UtilityId.FRAG.ordinal(), 0f, 0f, 0f, false);
        assertEquals(1f, DamageNumberModel.falloffRatio(utility));

        PacketDamageEvent gadget = new PacketDamageEvent(
            LOCAL, OTHER, 120f, 100f, HitZone.BODY,
            GadgetId.WIRE_ID_BASE + GadgetId.FUEL_TANK.ordinal(), 0f, 0f, 0f, true);
        assertEquals(1f, DamageNumberModel.falloffRatio(gadget));

        assertEquals(1f, DamageNumberModel.falloffRatio(null));
    }
}
