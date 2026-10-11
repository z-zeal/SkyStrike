package io.github.skystrike.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.gadget.CameraSystem;
import io.github.skystrike.server.gadget.DroneSystem;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.HeldWeaponGeometry;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authoritative bullet lifecycle against the real arena.
 *
 * <p>The important one is {@link #aRoundCannotTunnelThroughTheMidLane()}: the Cathedral moves
 * further in one tick than the 18-unit lane slab is thick, so point-testing the new position
 * would let rounds pass straight through the map.
 *
 * <p>Muzzle positions are per weapon now ({@link HeldWeaponGeometry}); the arena is the real
 * one, so the scenarios below place the shooter where the drawn barrel of that weapon fits in
 * the space actually in front of it.
 */
class BulletSystemTest {

    private static final float TICK = 1f / WorldConfig.TICK_RATE_HZ;
    private static final float EPSILON = 1e-2f;

    private ArenaMap arena;
    private BulletSystem bullets;
    private DamageService damage;

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
        bullets = new BulletSystem(arena);
        damage = new DamageService(new KillFeedService());
    }

    private static Player shooter(float x, float y, float aimDegrees) {
        Player p = new Player(1, "Shooter", 0, x, y);
        p.aimAngle = aimDegrees;
        return p;
    }

    @Test
    @DisplayName("a round leaves the drawn muzzle, not the eye, at the weapon's muzzle speed")
    void spawnPlacesTheRoundAtTheMuzzle() {
        Player player = shooter(500f, 100f, 0f);
        WeaponBallistics ballistics = WeaponBallistics.of(WeaponId.IRON_CARBINE);

        Projectile round = bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);

        assertNotNull(round);
        assertEquals(
            player.eyeX() + HeldWeaponGeometry.muzzleDistance(WeaponId.IRON_CARBINE.ordinal()),
            round.x,
            EPSILON);
        assertTrue(round.x - player.eyeX() > 40f,
            "a carbine's barrel is a full drawn gun ahead of the eye, not a pistol's 22 units: "
                + (round.x - player.eyeX()));
        assertEquals(player.eyeY(), round.y, EPSILON);
        assertEquals(ballistics.muzzleSpeed(), round.vx, EPSILON);
        assertEquals(0f, round.vy, EPSILON);
        assertEquals(player.id, round.ownerId);
        assertEquals(player.teamIndex, round.teamIndex);
        assertEquals(WeaponId.IRON_CARBINE, round.weapon());
        assertEquals(1, bullets.count());
        assertEquals(1, bullets.spawnedCount());
    }

    @Test
    @DisplayName("firing into a wall you are touching produces no round at all")
    void muzzleInsideGeometryIsAbsorbed() {
        // Standing on the centre room floor, right up against its east wall (x 1856..1880, the
        // mirror of the west wall at 1120..1144). The carbine's barrel is ~46 units long, so its
        // muzzle ends up *past* the 24-unit wall — the eye-to-muzzle segment is what catches it,
        // which is exactly the trap a per-weapon muzzle has to keep: a long barrel must not let a
        // round appear on the far side of the wall the player is hugging.
        Player player = shooter(1850f, 300f, 0f);

        float muzzleX = HeldWeaponGeometry.muzzleX(
            WeaponId.IRON_CARBINE.ordinal(), player.eyeX(), 0f);
        assertTrue(muzzleX > 1880f,
            "the test is only meaningful while the barrel reaches past the wall: " + muzzleX);
        assertNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f),
            "the wall is between the eye and the muzzle, so the round never exists");
        assertEquals(0, bullets.count());

        // Firing due west is not a normal shot either: the same ~46-unit barrel puts the muzzle
        // at x=1804, inside the mirrored inner pillar (1800..1816, y 300..430). The gap between
        // that pillar and the wall is 40 units — narrower than the barrel — so the guard absorbs
        // that shot too. Straight up is the shot with room: only the ceiling is above this nook.
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 90f));
    }

    @Test
    @DisplayName("a round cannot tunnel through the 18 unit mid lane")
    void aRoundCannotTunnelThroughTheMidLane() {
        // The mid lane slab is (820, 500) 250x18, so it spans y 500..518 across x 820..1070.
        // A player hovering below it at (980, 390) — left of the doorway landing (1000..1120,
        // 342..360), so the barrel line is clear of it — fires a Cathedral straight up: 1820 u/s
        // is 30.3 units per tick, more than the slab is thick, so only a swept test can catch it.
        // The longest barrel in the game (~62 units) ends ~6 units under the slab, and one tick
        // carries the round past its far face (524.4 > 518). The two assertions below guard that
        // window, so a longer muzzle fails loudly here instead of silently testing nothing.
        Player player = shooter(980f, 390f, 90f);
        Projectile round = bullets.spawn(player, WeaponId.CATHEDRAL, 90f);

        assertNotNull(round);
        assertTrue(round.y < 500f, "the round must start below the slab: " + round.y);

        float perTick = WeaponBallistics.of(WeaponId.CATHEDRAL).muzzleSpeed() * TICK;
        assertTrue(round.y + perTick > 518f,
            "the test is only meaningful if one tick clears the slab entirely");

        bullets.step(TICK, List.of(player), damage);

        assertEquals(0, bullets.count(), "the round must stop at the lane");
        assertEquals(1, bullets.terrainImpactCount());
        assertEquals(0, bullets.playerImpactCount());
        assertTrue(damage.drain().isEmpty());
    }

    @Test
    @DisplayName("a round that hits a player damages them once and disappears")
    void aRoundHitsAPlayer() {
        // The carbine's muzzle starts ~46 units ahead of the eye, so the victim has to stand far
        // enough past it for the round to have flown a real gap when it lands. The crates at
        // x=820 are what caps this lane's clear run.
        Player player = shooter(400f, 100f, 0f);
        Player victim = new Player(2, "Victim", 1, 800f, 100f);

        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));

        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player, victim), damage);
        }

        assertEquals(0, bullets.count(), "the round is consumed by the hit");
        assertEquals(1, bullets.playerImpactCount());
        assertTrue(victim.health < PlayerConfig.MAX_HEALTH, "the victim must have taken damage");

        List<DamageService.DamageResult> results = damage.drain();
        assertEquals(1, results.size(), "one round, one damage instance");
        DamageService.DamageResult hit = results.get(0);
        assertEquals(player.id, hit.attackerId());
        assertEquals(victim.id, hit.targetId());
        assertTrue(hit.distanceTravelled() > 250f, "the round flew the gap: " + hit.distanceTravelled());
        assertTrue(hit.amount() > 0f);
    }

    @Test
    @DisplayName("damage falls off with the path actually flown")
    void distantHitsHurtLess() {
        // Fought in open air at y = 1200: the ground lane is full of crates from x = 820 on, and
        // a round that stops in a crate proves nothing about falloff. The shooter sits twelve
        // units below the targets so both rounds arrive in the body zone — comparing a close
        // headshot against a distant body shot would measure the zone multiplier instead.
        Player near = new Player(2, "Near", 1, 500f, 1200f);
        Player far = new Player(3, "Far", 1, 1000f, 1200f);
        Player player = shooter(300f, 1188f, 0f);

        bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);
        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player, near), damage);
        }
        List<DamageService.DamageResult> nearResults = damage.drain();
        assertEquals(1, nearResults.size(), "the near round must connect");
        DamageService.DamageResult nearHit = nearResults.get(0);

        bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);
        for (int tick = 0; tick < 60 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player, far), damage);
        }
        List<DamageService.DamageResult> farResults = damage.drain();
        assertEquals(1, farResults.size(), "the far round must connect too");
        DamageService.DamageResult farHit = farResults.get(0);

        assertEquals(nearHit.zone(), farHit.zone(), "like for like");
        assertTrue(farHit.distanceTravelled() > nearHit.distanceTravelled(),
            "the far round flew further: " + farHit.distanceTravelled());
        assertTrue(farHit.amount() < nearHit.amount(),
            "near " + nearHit.amount() + " should beat far " + farHit.amount());
    }

    @Test
    @DisplayName("a round cannot hit the player who fired it on the way out")
    void selfHitGraceProtectsTheShooter() {
        // Firing straight down puts the muzzle inside your own hitbox only while the barrel is
        // shorter than the eye height above the feet (~42 units). A pistol's ~23 units does it;
        // a rifle's ~46 or a sniper's ~62 would leave the muzzle in the floor, where the
        // eye-to-muzzle guard absorbs the shot before it becomes one.
        Player player = shooter(500f, 100f, -90f);
        Projectile round = bullets.spawn(player, WeaponId.IRON_SIDEARM, -90f);

        assertNotNull(round);
        assertTrue(round.y > player.y && round.y < player.y + player.currentHeight(),
            "the muzzle must start inside the shooter's own hitbox for this test to mean anything");

        bullets.step(TICK, List.of(player), damage);

        assertEquals(PlayerConfig.MAX_HEALTH, player.health, EPSILON, "you cannot shoot yourself in the foot");
        assertEquals(0, bullets.playerImpactCount());
    }

    @Test
    @DisplayName("rounds stop at terrain and leave the world")
    void roundsAreAbsorbedByTerrain() {
        // The sidearm's ~23-unit barrel keeps the muzzle above the floor when fired straight
        // down. A longer barrel would put it inside the floor and the guard would absorb the
        // shot at the muzzle, which is the wall test's territory, not this one.
        Player player = shooter(500f, 100f, -90f);
        assertNotNull(bullets.spawn(player, WeaponId.IRON_SIDEARM, -90f),
            "the round must exist for the ground to absorb it");

        for (int tick = 0; tick < 10 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }

        assertEquals(0, bullets.count(), "the ground must absorb it");
        assertEquals(1, bullets.terrainImpactCount());
    }

    @Test
    @DisplayName("rounds expire instead of raining down somewhere else a second later")
    void roundsExpire() {
        // Fired straight up the middle of the arena, away from the perch and catwalk.
        Player player = shooter(300f, 900f, 90f);
        bullets.spawn(player, WeaponId.WASP_NEST, 90f);
        assertEquals(1, bullets.count());

        for (int tick = 0; tick < 300 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }
        assertEquals(0, bullets.count(), "no round may live forever");
    }

    @Test
    @DisplayName("the live round list is capped and clearable")
    void liveRoundsAreCappedAndClearable() {
        Player player = shooter(500f, 100f, 0f);

        for (int i = 0; i < CombatConfig.MAX_ACTIVE_PROJECTILES + 20; i++) {
            bullets.spawn(player, WeaponId.SMOKE_STITCH, 0f);
        }
        assertEquals(CombatConfig.MAX_ACTIVE_PROJECTILES, bullets.count());

        bullets.clear();
        assertEquals(0, bullets.count());
        assertTrue(bullets.active().isEmpty());
    }

    @Test
    @DisplayName("the exposed round list is a read-only view")
    void activeListIsUnmodifiable() {
        Player player = shooter(500f, 100f, 0f);
        bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);

        List<Projectile> active = bullets.active();
        assertEquals(1, active.size());
        assertThrows(UnsupportedOperationException.class, active::clear,
            "the active list must not be mutable from outside");
    }

    @Test
    @DisplayName("ids are unique so the client can track a round across snapshots")
    void roundIdsAreUnique() {
        Player player = shooter(500f, 100f, 0f);
        Projectile first = bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);
        Projectile second = bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(second.id > first.id);
    }

    @Test
    @DisplayName("nothing spawns without a shooter or a weapon")
    void degenerateSpawns() {
        assertNull(bullets.spawn(null, WeaponId.IRON_CARBINE, 0f));
        assertNull(bullets.spawn(shooter(500f, 100f, 0f), null, 0f));
        assertEquals(0, bullets.count());
    }

    @Test
    @DisplayName("a round lands on a gadget device, and the nearest device beats a player behind it")
    void aRoundHitsAGadgetDevice() {
        DroneSystem drones = new DroneSystem(arena);
        CameraSystem cameras = new CameraSystem(arena);
        bullets.setGadgetSystems(drones, cameras);

        // The owner stands well away from the line of fire; the drone is what's in it.
        Player owner = new Player(2, "Owner", 0, 400f, 1500f);
        owner.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.x = 700f;
        drone.y = 1242.5f;

        Player victim = new Player(3, "Victim", 1, 900f, 1200f);
        Player player = shooter(500f, 1200f, 0f);
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));

        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player, owner, victim), damage);
        }

        assertEquals(0, bullets.count(), "the round is consumed by the device hit");
        assertEquals(1, bullets.gadgetImpactCount());
        assertEquals(0, bullets.playerImpactCount(), "the device shielded the player behind it");
        assertTrue(drone.health < GadgetConfig.DRONE_HEALTH, "the drone took the damage");
        assertEquals(PlayerConfig.MAX_HEALTH, victim.health, 1e-4f, "nobody behind the drone was hit");
    }

    @Test
    @DisplayName("with no device systems wired, rounds ignore devices entirely")
    void noGadgetSystemsMeansNoDeviceHits() {
        Player player = shooter(500f, 1200f, 0f);
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));

        for (int tick = 0; tick < 60 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }

        assertEquals(0, bullets.gadgetImpactCount());
        assertEquals(0, bullets.count(), "the round still resolves against the world and ends");
    }
}
