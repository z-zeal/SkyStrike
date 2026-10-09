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
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authoritative bullet lifecycle against the real arena.
 *
 * <p>The important one is {@link #aRoundCannotTunnelThroughTheTunnelRoof()}: every gun in the
 * table moves further in one tick than the 14-unit tunnel roof is thick, so point-testing the
 * new position would let rounds pass straight through the map.
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
    @DisplayName("a round leaves the muzzle, not the eye, at the weapon's muzzle speed")
    void spawnPlacesTheRoundAtTheMuzzle() {
        Player player = shooter(500f, 100f, 0f);
        WeaponBallistics ballistics = WeaponBallistics.of(WeaponId.IRON_CARBINE);

        Projectile round = bullets.spawn(player, WeaponId.IRON_CARBINE, 0f);

        assertNotNull(round);
        assertEquals(player.eyeX() + CombatConfig.MUZZLE_OFFSET, round.x, EPSILON);
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
        // mirror of the west wall at 1120..1144). The column west of the floor is not open air
        // either — the doorway landings sit above it (x 1000..1120, y 342..360 mirrored) — so the
        // clear shot has to be the one away from the wall the muzzle is buried in.
        Player player = shooter(1850f, 300f, 0f);

        assertNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f),
            "the muzzle is inside the wall, so the round never exists");
        assertEquals(0, bullets.count());

        // Turning around and firing away from the wall works normally.
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 180f));
    }

    @Test
    @DisplayName("a round cannot tunnel through the 14 unit tunnel roof")
    void aRoundCannotTunnelThroughTheTunnelRoof() {
        // The tunnel roof slab is (1120, 190) 180x14, so it spans y 190..204.
        // A player hovering in the tunnel fires a Cathedral straight up: 1820 u/s is 30.3 units per
        // tick, more than twice the slab's thickness, so only a swept test can catch it.
        Player player = shooter(1200f, 115.5f, 90f);
        Projectile round = bullets.spawn(player, WeaponId.CATHEDRAL, 90f);

        assertNotNull(round);
        assertTrue(round.y < 190f, "the round must start below the slab: " + round.y);

        float perTick = WeaponBallistics.of(WeaponId.CATHEDRAL).muzzleSpeed() * TICK;
        assertTrue(round.y + perTick > 204f,
            "the test is only meaningful if one tick clears the slab entirely");

        bullets.step(TICK, List.of(player), damage);

        assertEquals(0, bullets.count(), "the round must stop at the roof");
        assertEquals(1, bullets.terrainImpactCount());
        assertEquals(0, bullets.playerImpactCount());
        assertTrue(damage.drain().isEmpty());
    }

    @Test
    @DisplayName("a round that hits a player damages them once and disappears")
    void aRoundHitsAPlayer() {
        Player player = shooter(400f, 100f, 0f);
        Player victim = new Player(2, "Victim", 1, 700f, 100f);

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
        // Firing straight down puts the muzzle inside your own hitbox.
        Player player = shooter(500f, 100f, -90f);
        Projectile round = bullets.spawn(player, WeaponId.CATHEDRAL, -90f);

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
        Player player = shooter(500f, 100f, -90f);
        bullets.spawn(player, WeaponId.IRON_CARBINE, -90f);

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
