package io.github.skystrike.server.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Server-authority tests for the worn gadgets in increment 2. */
class GadgetSystemTest {

    private Player attacker;
    private Player target;
    private KillFeedService killFeed;
    private DamageService damage;

    @BeforeEach
    void setUp() {
        attacker = new Player(1, "Nova", 0, 760f, 975f);
        target = new Player(2, "Rook", 1, 810f, 975f);
        killFeed = new KillFeedService();
        damage = new DamageService(killFeed);
    }

    @Test
    @DisplayName("Q toggles a shield and equipped state forces the handgun")
    void shieldToggleAndHandgunLock() {
        target.loadout.setGadgets(GadgetId.SHIELD, GadgetId.NONE);
        target.loadout.activeSlot = PlayerLoadout.SLOT_PRIMARY;
        ShieldSystem shields = new ShieldSystem();

        assertTrue(shields.toggle(target, PacketPlayerInput.GADGET_Q_PRESS));
        assertTrue(target.loadout.shieldEquipped());
        assertTrue(shields.enforceHandgunOnly(target));
        assertEquals(PlayerLoadout.SLOT_HANDGUN, target.loadout.activeSlot);

        assertTrue(shields.toggle(target, PacketPlayerInput.GADGET_Q_PRESS));
        assertFalse(target.loadout.shieldEquipped());
    }

    @Test
    @DisplayName("the real damage path absorbs from the stowed rear and breaks at zero")
    void shieldAbsorbsAndBreaks() {
        target.loadout.setGadgets(GadgetId.SHIELD, GadgetId.NONE);
        GadgetSlot shield = target.loadout.gadgetQ;
        target.aimAngle = 0f;
        shield.durability = 20f;
        float bodyY = target.centerY();

        DamageService.DamageResult absorbed = damage.apply(
            attacker, attacker.id, target, 15f, HitZone.BODY, 0, target.x - 40f, bodyY, 0f);
        assertNull(absorbed, "a fully absorbed hit has no health damage event");
        assertEquals(5f, shield.durability, 0.001f);
        assertEquals(150f, target.health, 0.001f);

        DamageService.DamageResult breaking = damage.apply(
            attacker, attacker.id, target, 15f, HitZone.BODY, 0, target.x - 40f, bodyY, 0f);
        assertNotNull(breaking);
        assertEquals(0f, shield.durability, 0.001f);
        assertTrue(shield.broken);
        assertEquals(140f, target.health, 0.001f, "ten damage overflowed after five durability");

        DamageService.DamageResult afterBreak = damage.apply(
            attacker, attacker.id, target, 10f, HitZone.BODY, 0, target.x - 40f, bodyY, 0f);
        assertNotNull(afterBreak);
        assertEquals(130f, target.health, 0.001f);
    }

    @Test
    @DisplayName("respawn reset restores worn gadget state without changing composition")
    void respawnRestoresGadgets() {
        target.loadout.setGadgets(GadgetId.SHIELD, GadgetId.FUEL_TANK);
        target.loadout.gadgetQ.active = true;
        target.loadout.gadgetQ.applyDurabilityDamage(200f);
        target.loadout.gadgetE.broken = true;
        target.loadout.gadgetE.durability = 0f;

        target.loadout.resetForRespawn();

        assertEquals(GadgetId.SHIELD, target.loadout.gadgetQ.gadgetId());
        assertEquals(GadgetId.FUEL_TANK, target.loadout.gadgetE.gadgetId());
        assertFalse(target.loadout.gadgetQ.broken);
        assertFalse(target.loadout.gadgetQ.active);
        assertEquals(150f, target.loadout.gadgetQ.durability, 0.001f);
        assertFalse(target.loadout.gadgetE.broken);
        assertTrue(target.loadout.hasFuelTank());
    }

    @Test
    @DisplayName("a tank detonation kills its wearer and credits the shooter with the gadget id")
    void tankDetonationCreditsShooterAndHitsBystanderOnce() {
        target.loadout.setGadgets(GadgetId.FUEL_TANK, GadgetId.NONE);
        FuelTankSystem tanks = new FuelTankSystem(ArenaMap.standard());
        DamageService tankDamage = new DamageService(killFeed, tanks);
        Player bystander = new Player(3, "Vex", 1, 850f, 975f);

        DamageService.DamageResult wearer = tankDamage.applyBulletDamage(
            attacker,
            attacker.id,
            target,
            WeaponRegistry.of(WeaponId.IRON_CARBINE),
            target.x - 10f,
            target.centerY(),
            0f,
            List.of(attacker, target, bystander));

        assertNotNull(wearer);
        assertFalse(target.alive);
        assertTrue(target.loadout.gadgetQ.broken);
        assertTrue(bystander.health < 150f);
        assertEquals(3, tankDamage.drain().size(), "attacker, wearer and bystander each resolve once");
        assertEquals(GadgetId.FUEL_TANK.wireId(), killFeed.drain().get(0).weaponId());
    }
}
