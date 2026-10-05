package io.github.skystrike.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Damage is falloff first, then the hit zone. That order is what keeps "a headshot is worth
 * double" true at every range, and it is the thing these tests mostly pin down.
 */
class DamageServiceTest {

    private static final float EPSILON = 1e-2f;

    private KillFeedService killFeed;
    private DamageService damage;
    private Player attacker;
    private Player target;

    @BeforeEach
    void setUp() {
        killFeed = new KillFeedService();
        damage = new DamageService(killFeed);
        attacker = new Player(1, "Nova", 0, 400f, 100f);
        target = new Player(2, "Rook", 1, 700f, 100f);
    }

    /** Impact height inside the target's body, below the head zone. */
    private static float bodyY(Player p) {
        return p.y + p.currentHeight() * 0.4f;
    }

    /** Impact height inside the target's head zone. */
    private static float headY(Player p) {
        return p.y + p.currentHeight() * 0.9f;
    }

    @Test
    @DisplayName("a body shot at the muzzle does exactly the weapon's damage")
    void pointBlankBodyShot() {
        WeaponDefinition carbine = WeaponRegistry.of(WeaponId.IRON_CARBINE);
        DamageService.DamageResult result =
            damage.applyBulletDamage(attacker, attacker.id, target, carbine, 700f, bodyY(target), 0f);

        assertNotNull(result);
        assertEquals(HitZone.BODY, result.zone());
        assertEquals(32.2f, result.amount(), EPSILON);
        assertEquals(PlayerConfig.MAX_HEALTH - 32.2f, target.health, EPSILON);
        assertFalse(result.killed());
        assertTrue(target.alive);
    }

    @Test
    @DisplayName("a headshot is worth double at every range")
    void headshotsDoubleAtEveryRange() {
        WeaponDefinition carbine = WeaponRegistry.of(WeaponId.IRON_CARBINE);
        float range = carbine.ballistics().maxRange();

        Player a = new Player(10, "A", 1, 0f, 0f);
        Player b = new Player(11, "B", 1, 0f, 0f);

        float closeBody = damage.applyBulletDamage(attacker, 1, a, carbine, 0f, bodyY(a), 0f).amount();
        float closeHead = damage.applyBulletDamage(attacker, 1, b, carbine, 0f, headY(b), 0f).amount();
        assertEquals(2f, closeHead / closeBody, EPSILON);

        Player c = new Player(12, "C", 1, 0f, 0f);
        Player d = new Player(13, "D", 1, 0f, 0f);
        float farBody = damage.applyBulletDamage(attacker, 1, c, carbine, 0f, bodyY(c), range).amount();
        float farHead = damage.applyBulletDamage(attacker, 1, d, carbine, 0f, headY(d), range).amount();
        assertEquals(2f, farHead / farBody, EPSILON);
    }

    @Test
    @DisplayName("a full-arena shot deals clearly less damage than a point-blank one")
    void falloffIsVisibleAcrossTheArena() {
        WeaponDefinition magnum = WeaponRegistry.of(WeaponId.LONGSPUR_44);
        float range = magnum.ballistics().maxRange();

        Player near = new Player(20, "Near", 1, 0f, 0f);
        Player far = new Player(21, "Far", 1, 0f, 0f);

        float close = damage.applyBulletDamage(attacker, 1, near, magnum, 0f, bodyY(near), 0f).amount();
        float distant = damage.applyBulletDamage(attacker, 1, far, magnum, 0f, bodyY(far), range).amount();

        assertEquals(82.8f, close, EPSILON);
        assertEquals(82.8f * 0.55f, distant, EPSILON);
        assertTrue(distant < close * 0.6f, "the difference must be obvious, not subtle");

        // And two body shots kill up close while three do not at maximum range.
        assertTrue(close * 2f >= CombatConfig.MAX_HEALTH);
        assertTrue(distant * 3f < CombatConfig.MAX_HEALTH);
    }

    @Test
    @DisplayName("a Cathedral headshot is one shot and a body shot is two")
    void sniperTimeToKill() {
        WeaponDefinition cathedral = WeaponRegistry.of(WeaponId.CATHEDRAL);

        DamageService.DamageResult head =
            damage.applyBulletDamage(attacker, attacker.id, target, cathedral, 700f, headY(target), 100f);
        assertTrue(head.killed(), "a Cathedral headshot must kill outright");
        assertEquals(0f, target.health, EPSILON);

        Player second = new Player(3, "Vex", 1, 900f, 100f);
        DamageService.DamageResult first =
            damage.applyBulletDamage(attacker, attacker.id, second, cathedral, 900f, bodyY(second), 100f);
        assertFalse(first.killed(), "one body shot must not kill");
        DamageService.DamageResult killing =
            damage.applyBulletDamage(attacker, attacker.id, second, cathedral, 900f, bodyY(second), 100f);
        assertTrue(killing.killed());
    }

    @Test
    @DisplayName("friendly fire and self-damage are on, and Neutral fights everyone")
    void damagePermissions() {
        Player teammate = new Player(4, "Mate", 0, 500f, 100f);
        Player neutral = new Player(5, "Lone", CombatConfig.NEUTRAL_TEAM_INDEX, 600f, 100f);
        Player otherNeutral = new Player(6, "Wolf", CombatConfig.NEUTRAL_TEAM_INDEX, 650f, 100f);

        assertTrue(DamageService.canDamage(attacker, target), "enemies, obviously");
        assertEquals(CombatConfig.FRIENDLY_FIRE, DamageService.canDamage(attacker, teammate));
        assertEquals(CombatConfig.SELF_DAMAGE, DamageService.canDamage(attacker, attacker));
        assertTrue(DamageService.canDamage(neutral, attacker), "Neutral fights everyone");
        assertTrue(DamageService.canDamage(attacker, neutral));
        assertTrue(DamageService.canDamage(neutral, otherNeutral), "including other Neutrals");

        target.alive = false;
        assertFalse(DamageService.canDamage(attacker, target), "corpses take no damage");
    }

    @Test
    @DisplayName("death sets the respawn timer, counts the death, and credits the killer once")
    void deathBookkeeping() {
        WeaponDefinition cathedral = WeaponRegistry.of(WeaponId.CATHEDRAL);
        target.vx = 150f;
        target.vy = -40f;
        target.jetpacking = true;

        DamageService.DamageResult result =
            damage.applyBulletDamage(attacker, attacker.id, target, cathedral, 700f, headY(target), 0f);

        assertTrue(result.killed());
        assertFalse(target.alive);
        assertEquals(0f, target.health, EPSILON);
        assertEquals(1, target.deaths);
        assertEquals(CombatConfig.RESPAWN_DELAY_SECONDS, target.respawnTimer, EPSILON);
        assertEquals(0f, target.vx, EPSILON, "a corpse stops moving");
        assertEquals(0f, target.vy, EPSILON);
        assertFalse(target.jetpacking);
        assertEquals(1, attacker.kills);

        // Shooting the corpse again does nothing at all.
        assertNull(damage.applyBulletDamage(attacker, attacker.id, target, cathedral, 700f, headY(target), 0f));
        assertEquals(1, attacker.kills);
        assertEquals(1, target.deaths);
    }

    @Test
    @DisplayName("a team kill and a self kill credit nobody")
    void friendlyAndSelfKillsAreNotCredited() {
        WeaponDefinition cathedral = WeaponRegistry.of(WeaponId.CATHEDRAL);

        Player teammate = new Player(4, "Mate", 0, 500f, 100f);
        damage.applyBulletDamage(attacker, attacker.id, teammate, cathedral, 500f, headY(teammate), 0f);
        assertFalse(teammate.alive);
        assertEquals(1, teammate.deaths);
        assertEquals(0, attacker.kills, "team killing is not scoring");

        damage.applyBulletDamage(attacker, attacker.id, attacker, cathedral, 400f, headY(attacker), 0f);
        assertFalse(attacker.alive);
        assertEquals(1, attacker.deaths);
        assertEquals(0, attacker.kills, "nor is shooting yourself");
    }

    @Test
    @DisplayName("every applied hit is queued once and drains once")
    void resultsDrainOnce() {
        WeaponDefinition scar = WeaponRegistry.of(WeaponId.IRON_CARBINE);
        damage.applyBulletDamage(attacker, attacker.id, target, scar, 700f, bodyY(target), 0f);
        damage.applyBulletDamage(attacker, attacker.id, target, scar, 700f, bodyY(target), 0f);

        assertEquals(2, damage.pendingCount());
        List<DamageService.DamageResult> drained = damage.drain();
        assertEquals(2, drained.size());
        assertEquals(0, damage.pendingCount());
        assertTrue(damage.drain().isEmpty());

        DamageService.DamageResult first = drained.get(0);
        assertEquals(attacker.id, first.attackerId());
        assertEquals(target.id, first.targetId());
        assertEquals(WeaponId.IRON_CARBINE.ordinal(), first.weaponId());
        assertFalse(first.isSelfInflicted());
    }

    @Test
    @DisplayName("a kill reaches the feed with the headshot and friendly-fire flags set")
    void killsReachTheFeed() {
        WeaponDefinition cathedral = WeaponRegistry.of(WeaponId.CATHEDRAL);
        damage.applyBulletDamage(attacker, attacker.id, target, cathedral, 700f, headY(target), 0f);

        List<KillFeedService.KillEvent> events = killFeed.drain();
        assertEquals(1, events.size());

        KillFeedService.KillEvent event = events.get(0);
        assertEquals(attacker.id, event.killerId());
        assertEquals("Nova", event.killerName());
        assertEquals(target.id, event.victimId());
        assertEquals("Rook", event.victimName());
        assertEquals(WeaponId.CATHEDRAL, event.weapon());
        assertTrue(event.headshot());
        assertFalse(event.selfInflicted());
        assertFalse(event.friendlyFire());

        assertTrue(killFeed.drain().isEmpty(), "the feed drains once");
        assertEquals(1, killFeed.recent().size(), "but history is kept");
    }

    @Test
    @DisplayName("damage with no attacker still works, for Phase 5 hazards")
    void worldDamageNeedsNoAttacker() {
        DamageService.DamageResult result =
            damage.apply(null, -1, target, 40f, HitZone.BODY, -1, 700f, 120f, 0f);

        assertNotNull(result);
        assertEquals(PlayerConfig.MAX_HEALTH - 40f, target.health, EPSILON);
        assertEquals(-1, result.attackerId());
    }

    @Test
    @DisplayName("nothing is applied for a missing target, a dead one, or zero damage")
    void degenerateInputs() {
        assertNull(damage.apply(attacker, 1, null, 10f, HitZone.BODY, 0, 0f, 0f, 0f));
        assertNull(damage.apply(attacker, 1, target, 0f, HitZone.BODY, 0, 0f, 0f, 0f));
        assertNull(damage.applyBulletDamage(attacker, 1, target, null, 0f, 0f, 0f));

        target.alive = false;
        assertNull(damage.apply(attacker, 1, target, 10f, HitZone.BODY, 0, 0f, 0f, 0f));
    }
}
