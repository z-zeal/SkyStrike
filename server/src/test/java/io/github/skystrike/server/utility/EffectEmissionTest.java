package io.github.skystrike.server.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.server.weapons.FireController;
import io.github.skystrike.server.weapons.GunInstance;
import io.github.skystrike.server.weapons.RecoilService;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The M7 §8.1 emission points: every gameplay event that should be seen emits exactly the effect
 * type the client's catalogue expects, at the position and direction the simulation resolved.
 * Systems without a sink must behave exactly as before — these tests pin that too.
 *
 * <p>Lives in the utility package because {@code UtilitySystem.detonate} is deliberately
 * package-visible for deterministic tests; the bullet and fire-controller emission cases only use
 * public APIs and ride along.
 */
class EffectEmissionTest {

    private static final float TICK = 1f / WorldConfig.TICK_RATE_HZ;

    private ArenaMap arena;
    private DamageService damage;
    private RecordingSink sink;

    /** The simplest possible sink: a list, so tests assert on exactly what was emitted. */
    private static final class RecordingSink implements EffectSink {
        final List<EffectSpawn> emitted = new ArrayList<>();

        @Override
        public void emit(EffectSpawn spawn) {
            emitted.add(spawn);
        }
    }

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
        damage = new DamageService(new KillFeedService());
        sink = new RecordingSink();
    }

    // --- UtilitySystem detonations -------------------------------------------------------------

    @Test
    @DisplayName("a frag detonation emits one frag explosion at the detonation point")
    void fragDetonationEmitsFragExplosion() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);
        ThrownUtility frag = thrown(UtilityId.FRAG, owner, 760f, 1000f);

        utilities.detonate(frag, UtilityRegistry.of(UtilityId.FRAG), List.of(owner), damage);

        assertEquals(1, sink.emitted.size());
        EffectSpawn spawn = sink.emitted.get(0);
        assertEquals(EffectType.FRAG_EXPLOSION, spawn.type);
        assertEquals(760f, spawn.x, 0.001f);
        assertEquals(1000f, spawn.y, 0.001f);
    }

    @Test
    @DisplayName("an impact grenade detonation emits the smaller impact explosion")
    void impactDetonationEmitsImpactExplosion() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);
        ThrownUtility impact = thrown(UtilityId.IMPACT, owner, 760f, 1000f);

        utilities.detonate(impact, UtilityRegistry.of(UtilityId.IMPACT), List.of(owner), damage);

        assertEquals(1, sink.emitted.size());
        assertEquals(EffectType.IMPACT_EXPLOSION, sink.emitted.get(0).type);
    }

    @Test
    @DisplayName("a smoke detonation emits a cloud scaled to grow into the zone's own radius")
    void smokeDetonationEmitsCloudSizedToTheZone() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);
        ThrownUtility smoke = thrown(UtilityId.SMOKE, owner, 760f, 1000f);

        utilities.detonate(smoke, UtilityRegistry.of(UtilityId.SMOKE), List.of(owner), damage);

        assertEquals(1, sink.emitted.size());
        EffectSpawn spawn = sink.emitted.get(0);
        assertEquals(EffectType.SMOKE_BURST, spawn.type);
        float expectedScale = UtilityRegistry.of(UtilityId.SMOKE).radius() / VisionConfig.DEFAULT_SMOKE_RADIUS;
        assertEquals(expectedScale, spawn.scale, 0.0001f);
    }

    @Test
    @DisplayName("a poison detonation emits the poison burst, scaled the same way")
    void poisonDetonationEmitsPoisonBurst() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);
        ThrownUtility poison = thrown(UtilityId.POISON_SMOKE, owner, 760f, 1000f);

        utilities.detonate(poison, UtilityRegistry.of(UtilityId.POISON_SMOKE), List.of(owner), damage);

        assertEquals(1, sink.emitted.size());
        EffectSpawn spawn = sink.emitted.get(0);
        assertEquals(EffectType.POISON_BURST, spawn.type);
        float expectedScale =
            UtilityRegistry.of(UtilityId.POISON_SMOKE).radius() / VisionConfig.DEFAULT_SMOKE_RADIUS;
        assertEquals(expectedScale, spawn.scale, 0.0001f);
    }

    @Test
    @DisplayName("a molotov emits a tangent splash plus one fire-zone event per accepted zone")
    void molotovDetonationEmitsSplashAndFireZones() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 475f);
        ThrownUtility molotov = thrown(UtilityId.MOLOTOV, owner, 760f, 500f);
        molotov.contactNormalX = 0f;
        molotov.contactNormalY = 1f; // floor: the tangent runs horizontally

        utilities.detonate(molotov, UtilityRegistry.of(UtilityId.MOLOTOV), List.of(owner), damage);

        int expectedZones = 1 + UtilityConfig.FIRE_SPREAD_ZONES_PER_SIDE * 2;
        assertEquals(1 + expectedZones, sink.emitted.size());

        EffectSpawn splash = sink.emitted.get(0);
        assertEquals(EffectType.MOLOTOV_SPLASH, splash.type);
        assertEquals(760f, splash.x, 0.001f);
        assertEquals(500f, splash.y, 0.001f);
        // Floor normal (0, 1) gives tangent (-1, 0): the splash direction is 180 degrees.
        assertEquals(180f, splash.angle, 0.001f);

        for (int i = 1; i < sink.emitted.size(); i++) {
            EffectSpawn zone = sink.emitted.get(i);
            assertEquals(EffectType.FIRE_ZONE, zone.type, "every accepted fire zone emits one event");
            assertEquals(90f, zone.angle, 0.001f, "fire zones carry 'up' as their direction");
        }
    }

    @Test
    @DisplayName("stun and flashbang detonations both emit the flash burst; blindness stays server state")
    void stunAndFlashDetonationsEmitFlashBursts() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);

        utilities.detonate(
            thrown(UtilityId.STUN, owner, 760f, 1000f),
            UtilityRegistry.of(UtilityId.STUN),
            List.of(owner),
            damage);
        utilities.detonate(
            thrown(UtilityId.FLASHBANG, owner, 780f, 1000f),
            UtilityRegistry.of(UtilityId.FLASHBANG),
            List.of(owner),
            damage);

        assertEquals(2, sink.emitted.size());
        assertEquals(EffectType.FLASH_DETONATION, sink.emitted.get(0).type);
        assertEquals(EffectType.FLASH_DETONATION, sink.emitted.get(1).type);
    }

    @Test
    @DisplayName("a claymore detonation emits a directional blast along its placed aim")
    void claymoreDetonationEmitsDirectionalBlast() {
        UtilitySystem utilities = systemWithSink();
        Player owner = player(1, 760f, 975f);
        owner.aimAngle = 35f;
        ThrownUtility claymore = thrown(UtilityId.CLAYMORE, owner, 760f, 1000f);

        utilities.detonate(claymore, UtilityRegistry.of(UtilityId.CLAYMORE), List.of(owner), damage);

        assertEquals(1, sink.emitted.size());
        EffectSpawn spawn = sink.emitted.get(0);
        assertEquals(EffectType.CLAYMORE_BLAST, spawn.type);
        assertEquals(35f, spawn.angle, 0.001f);
    }

    @Test
    @DisplayName("a system with no sink detonates exactly as before, emitting nothing")
    void noSinkMeansNoEmissionAndNoCrash() {
        UtilitySystem utilities = new UtilitySystem(arena);
        Player owner = player(1, 760f, 975f);
        ThrownUtility frag = thrown(UtilityId.FRAG, owner, 760f, 1000f);

        utilities.detonate(frag, UtilityRegistry.of(UtilityId.FRAG), List.of(owner), damage);

        assertTrue(sink.emitted.isEmpty());
    }

    // --- BulletSystem impacts ------------------------------------------------------------------

    @Test
    @DisplayName("a round absorbed by terrain emits a concrete impact along its travel direction")
    void terrainImpactEmitsConcreteImpact() {
        BulletSystem bullets = new BulletSystem(arena);
        bullets.setEffectSink(sink);
        Player shooter = player(1, 500f, 100f);
        shooter.aimAngle = -90f;
        bullets.spawn(shooter, WeaponId.IRON_CARBINE, -90f);

        for (int tick = 0; tick < 10 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(shooter), damage);
        }

        assertEquals(0, bullets.count(), "the ground must absorb it");
        assertEquals(1, sink.emitted.size());
        EffectSpawn spawn = sink.emitted.get(0);
        assertEquals(EffectType.BULLET_IMPACT_CONCRETE, spawn.type);
        assertEquals(500f, spawn.x, 1f, "fired straight down, the impact keeps the muzzle's x");
        assertEquals(WorldConfig.GROUND_HEIGHT, spawn.y, 2f);
        assertEquals(-90f, spawn.angle, 1f, "the travel direction is straight down");
    }

    @Test
    @DisplayName("player hits emit no surface impact; a bullet system with no sink stays silent")
    void playerHitsAndMissingSinkEmitNothing() {
        BulletSystem bullets = new BulletSystem(arena);
        Player shooter = player(1, 400f, 100f);
        shooter.aimAngle = 0f;
        Player target = player(2, 700f, 100f);
        bullets.spawn(shooter, WeaponId.IRON_CARBINE, 0f);

        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(shooter, target), damage);
        }

        assertEquals(0, bullets.count(), "the round reached the target");
        assertEquals(1, bullets.playerImpactCount());
        assertTrue(sink.emitted.isEmpty(), "a body hit is not a surface impact");
    }

    // --- FireController muzzle events -----------------------------------------------------------

    @Test
    @DisplayName("a produced volley emits a muzzle flash and a shell eject at the barrel")
    void firingEmitsMuzzleFlashAndShellEject() {
        FireController controller = new FireController(new Random(1L), new RecoilService());
        controller.setEffectSink(sink);
        Player shooter = player(1, 500f, 100f);
        shooter.aimAngle = 0f;
        shooter.grounded = true;
        GunInstance gun = new GunInstance(WeaponId.IRON_CARBINE);
        FireController.Volley volley = new FireController.Volley();

        assertEquals(1, controller.fire(shooter, gun, true, true, volley));

        assertEquals(2, sink.emitted.size());
        EffectSpawn flash = sink.emitted.get(0);
        assertEquals(EffectType.MUZZLE_FLASH, flash.type);
        assertEquals(shooter.eyeX() + CombatConfig.MUZZLE_OFFSET, flash.x, 0.001f);
        assertEquals(shooter.eyeY(), flash.y, 0.001f);
        assertEquals(0f, flash.angle, 0.001f);

        EffectSpawn casing = sink.emitted.get(1);
        assertEquals(EffectType.SHELL_EJECT, casing.type);
        assertEquals(flash.x, casing.x, 0.001f);
        assertEquals(flash.y, casing.y, 0.001f);
        assertEquals(flash.angle, casing.angle, 0.001f);
    }

    @Test
    @DisplayName("a trigger click on cooldown produces no rounds and emits nothing")
    void cooldownClickEmitsNothing() {
        FireController controller = new FireController(new Random(1L), new RecoilService());
        controller.setEffectSink(sink);
        Player shooter = player(1, 500f, 100f);
        shooter.aimAngle = 0f;
        shooter.grounded = true;
        GunInstance gun = new GunInstance(WeaponId.IRON_CARBINE);
        FireController.Volley volley = new FireController.Volley();

        assertEquals(1, controller.fire(shooter, gun, true, true, volley));
        assertEquals(0, controller.fire(shooter, gun, true, false, volley), "still cycling");

        assertEquals(2, sink.emitted.size(), "only the produced volley emitted");
    }

    // --- helpers --------------------------------------------------------------------------------

    private UtilitySystem systemWithSink() {
        UtilitySystem utilities = new UtilitySystem(arena);
        utilities.setEffectSink(sink);
        return utilities;
    }

    private static Player player(int id, float x, float y) {
        return new Player(id, "P" + id, id == 1 ? 0 : 1, x, y);
    }

    private static ThrownUtility thrown(UtilityId id, Player owner, float x, float y) {
        ThrownUtility thrown = new ThrownUtility(
            10, owner.id, owner.teamIndex, id.ordinal(), x, y, 0f, 0f, 0f);
        thrown.aimAngle = owner.aimAngle;
        return thrown;
    }
}
