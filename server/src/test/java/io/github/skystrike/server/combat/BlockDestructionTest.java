package io.github.skystrike.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.server.gadget.CameraSystem;
import io.github.skystrike.server.gadget.DroneSystem;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.map.BlockChip;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.MeleeRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Milestone 3's server-side destruction: a round chips a breakable and stops, the hit that opens it
 * lets the round fly on through the hole, a pane shatters under anything and costs the round only
 * the small pass-through loss, and a swing reaches the blocks its arc covers.
 *
 * <p>These maps are authored rather than the shipped arena, whose fixture range would put the
 * pane, the crate and the platform in one line and leave nothing isolated to assert on. The crate
 * is authored with
 * {@code durability=0.4}, which is what makes one hit a chip and the second a break for a carbine,
 * and what proves the authored multiplier reaches the pool the damage is spent from.
 */
class BlockDestructionTest {

    private static final float TICK = 1f / WorldConfig.TICK_RATE_HZ;
    private static final float EPSILON = 1e-2f;
    private static final float PANE_LEFT = 1300f;
    private static final float CRATE_LEFT = 1400f;

    /** The floor every authored room here stands on, and the two props the cases place on it. */
    private static final String GROUND = "centred wall stone 0 0 3000 100";
    private static final String PANE = "mirrored glass glass 1300 100 20 200";
    private static final String CRATE = "mirrored breakable wood 1400 100 40 60 durability=0.4";

    /** Ground, a pane at x 1300..1320 and a crate at x 1400..1440, both in the round's lane. */
    private static final String WORKSHOP = room(PANE, CRATE);

    /** The same room without the pane, for the control round of the speed-loss case. */
    private static final String WORKSHOP_NO_PANE = room(CRATE);

    /** Ground and a pane alone: nothing between the window and whatever stands behind it. */
    private static final String PANE_ONLY = room(PANE);

    private static String room(String... props) {
        StringBuilder text = new StringBuilder("skystrike-map 1\nmirror 1500\n")
            .append(GROUND).append('\n');
        for (String prop : props) {
            text.append(prop).append('\n');
        }
        return text.toString();
    }

    private ArenaMap arena;
    private MapDamageState destruction;
    private BulletSystem bullets;
    private DamageService damage;
    private RecordingSink sink;

    /** The simplest possible sink: a list, so the tests assert on exactly what was emitted. */
    private static final class RecordingSink implements EffectSink {
        final List<EffectSpawn> emitted = new ArrayList<>();

        @Override
        public void emit(EffectSpawn spawn) {
            emitted.add(spawn);
        }
    }

    @BeforeEach
    void setUp() {
        arena = ArenaMapFormat.parse(WORKSHOP);
        destruction = MapDamageState.intact(arena);
        bullets = new BulletSystem(arena);
        bullets.setDestruction(destruction);
        damage = new DamageService(new KillFeedService());
        sink = new RecordingSink();
        bullets.setEffectSink(sink);
    }

    private static Player shooter(float x, float y, float aimDegrees) {
        Player player = new Player(1, "Shooter", 0, x, y);
        player.aimAngle = aimDegrees;
        return player;
    }

    /** The block whose left edge is {@code left}, whichever copy of the mirror it is. */
    private static MapBlock blockAt(ArenaMap map, float left) {
        for (MapBlock block : map.blocks()) {
            if (block.rect().x() == left) {
                return block;
            }
        }
        throw new AssertionError("no block with left edge " + left + " in " + map.blocks());
    }

    private static MapBlock pane(ArenaMap map) {
        return blockAt(map, PANE_LEFT);
    }

    private static MapBlock crate(ArenaMap map) {
        return blockAt(map, CRATE_LEFT);
    }

    @Test
    @DisplayName("a shattering pane costs the round only the pass-through speed loss")
    void thePaneCostsASmallSpeedLoss() {
        // Two identical rounds fired into two rooms that differ only by the pane. They fly the
        // same path for the same ticks, so the whole difference between them is the cost of
        // punching through the window — which must be small enough that shooting through it is
        // still a shot worth taking.
        MapDamageState openPane = MapDamageState.intact(arena);
        BulletSystem withPane = new BulletSystem(arena);
        withPane.setDestruction(openPane);

        ArenaMap noPane = ArenaMapFormat.parse(WORKSHOP_NO_PANE);
        BulletSystem control = new BulletSystem(noPane);

        Player eachSide = shooter(1200f, 100f, 0f);
        Projectile through = withPane.spawn(eachSide, WeaponId.IRON_CARBINE, 0f);
        Projectile straight = control.spawn(eachSide, WeaponId.IRON_CARBINE, 0f);

        // Step until the pane is gone, which is the tick the loss is paid on.
        for (int tick = 0; tick < 30 && openPane.revision() == 0; tick++) {
            withPane.step(TICK, List.of(eachSide), damage);
            control.step(TICK, List.of(eachSide), damage);
        }

        assertEquals(1, openPane.revision(), "the pane must shatter on the round's path");
        assertEquals(
            straight.vx * (1f - CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION),
            through.vx,
            EPSILON,
            "the only speed the pane may cost is the configured pass-through fraction");
        assertTrue(through.vx > 0.8f * straight.vx, "and it may never read as a wall");
    }

    @Test
    @DisplayName("a pane in front of a device is shattered before the round reaches the device")
    void thePaneShattersBeforeTheRoundReachesADevice() {
        // A window is in no blocking view, so the device sweep used to win the whole path and the
        // pane was never touched. The order on the round's own segment decides: pane first, then
        // whatever stands behind it. This room is a pane and the floor, so the drone is the first
        // thing past the window.
        ArenaMap window = ArenaMapFormat.parse(PANE_ONLY);
        MapDamageState shattered = MapDamageState.intact(window);
        BulletSystem rounds = new BulletSystem(window);
        rounds.setDestruction(shattered);
        rounds.setEffectSink(sink);
        DroneSystem drones = new DroneSystem(window);
        CameraSystem cameras = new CameraSystem(window);
        rounds.setGadgetSystems(drones, cameras);

        Player owner = new Player(2, "Owner", 0, 400f, 1500f);
        owner.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.x = 1420f;
        drone.y = 142.5f;

        Player player = shooter(1200f, 100f, 0f);
        assertNotNull(rounds.spawn(player, WeaponId.IRON_CARBINE, 0f));

        for (int tick = 0; tick < 30 && rounds.count() > 0; tick++) {
            rounds.step(TICK, List.of(player, owner), damage);
        }

        assertEquals(0, rounds.count(), "the round is spent somewhere");
        assertEquals(1, rounds.gadgetImpactCount(), "the drone behind the pane is hit");
        assertEquals(1, shattered.revision(), "and the pane is gone");
        assertFalse(shattered.liveMap().blocks().contains(pane(window)));
        assertFalse(sink.emitted.isEmpty());
        EffectSpawn first = sink.emitted.get(0);
        assertEquals(
            EffectType.GLASS_SHATTER,
            first.type,
            "the shatter is what the round met first, so it is the first event");
        assertEquals(PANE_LEFT, first.x, EPSILON);
    }

    @Test
    @DisplayName("a round cracks a crate and stops, and the hit that opens it lets the round through")
    void aRoundChipsThenPassesThrough() {
        Player player = shooter(1200f, 100f, 0f);
        MapBlock crate = crate(arena);
        float pool = 48f; // wood's 120 durability under the authored 0.4 multiplier

        // The first hit: real damage, no break, and the round stops in the wood it chipped.
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));
        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }
        float chipped = destruction.damage(crate);
        assertTrue(chipped > 0f && chipped < pool, "one carbine round chips it: " + chipped);
        assertEquals(0, destruction.revision());
        assertEquals(1, bullets.terrainImpactCount(), "the round stopped in the surface");
        assertEquals(EffectType.BULLET_IMPACT_WOOD, sink.emitted.get(0).type);
        assertEquals(CRATE_LEFT, sink.emitted.get(0).x, EPSILON);
        assertTrue(sink.emitted.get(0).y > 140f,
            "the impact is where the round met the crate, not its centre");

        // The second: the pool is spent, the block leaves the map, and the round flies on through
        // the hole it just made rather than dying in it.
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));
        for (int tick = 0; tick < 30 && destruction.revision() == 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }

        assertEquals(1, destruction.revision(), "the block is out of the map");
        assertFalse(destruction.liveMap().blocks().contains(crate));
        assertTrue(destruction.isDestroyed(crate));
        assertEquals(0f, destruction.remainingDurability(crate));
        assertEquals(1f, destruction.damageFraction(crate), 1e-4f);
        assertTrue(destruction.damage(crate) >= pool, "the pool was spent to zero and then some");
        assertEquals(1, bullets.count(), "the round that opened it kept flying");
        assertEquals(
            1,
            bullets.terrainImpactCount(),
            "the breaking hit is not a stop-hit: only the chip stopped a round");
    }

    @Test
    @DisplayName("a non-lethal hit leaves one chip carrying the running total, and a break supersedes it")
    void chipsAreRecordedPerHitWithTheRunningTotal() {
        Player player = shooter(1200f, 100f, 0f);
        MapBlock crate = crate(arena);
        float pool = 48f; // wood's 120 durability under the authored 0.4 multiplier

        // The first hit leaves the crate standing, so the hit is a chip: one record, carrying the
        // total rather than the hit's own size, and nothing about the map changes.
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));
        for (int tick = 0; tick < 30 && bullets.count() > 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }
        assertEquals(1, destruction.chips().size(), "one record per non-lethal hit");
        BlockChip chip = destruction.chips().get(0);
        assertEquals(crate, chip.block());
        assertEquals(destruction.damage(crate), chip.damage(), 1e-4f,
            "the record carries the authority's running total");
        assertTrue(chip.damage() > 0f && chip.damage() < pool,
            "a chip leaves the block standing: " + chip.damage());
        assertEquals(0, destruction.revision(), "and changes nothing the map is");
        assertTrue(destruction.damagedBlocks().contains(chip),
            "the baseline of what is chipped right now holds it too");

        // The second hit spends the pool. That is a break, and the break is the only record it
        // leaves: a block that left the map is never also stated as chipped.
        float afterChip = destruction.damage(crate);
        float fractionAfterChip = destruction.damageFraction(crate);
        assertNotNull(bullets.spawn(player, WeaponId.IRON_CARBINE, 0f));
        for (int tick = 0; tick < 30 && destruction.revision() == 0; tick++) {
            bullets.step(TICK, List.of(player), damage);
        }
        assertEquals(1, destruction.revision());
        assertEquals(1, destruction.chips().size(), "the breaking hit is a break, not a chip");

        // A mirror fed the chip lands on the authority's number while the block stands — the
        // fraction the crack overlay draws follows from it through the shared material row — and
        // the break then leaves both sides reading a fully spent, destroyed block.
        MapDamageState mirror = MapDamageState.intact(arena);
        assertTrue(mirror.applyRemoteDamage(destruction.authoredIndexOf(crate), chip.damage()));
        assertEquals(afterChip, mirror.damage(crate), 1e-4f, "the chip carried a total, not a delta");
        assertEquals(fractionAfterChip, mirror.damageFraction(crate), 1e-4f);
        assertFalse(mirror.isDestroyed(crate), "a chip leaves the block standing");
        assertNotNull(mirror.applyRemoteBreak(
            destruction.authoredIndexOf(crate), DamageSource.BULLET, CRATE_LEFT, 150f, false));
        assertTrue(mirror.isDestroyed(crate));
        assertEquals(1f, mirror.damageFraction(crate), 1e-4f, "a destroyed block reads fully spent");
        assertEquals(1, mirror.revision(), "and the two sides' revisions agree once both land");
    }

    @Test
    @DisplayName("a swing lands on the blocks its arc covers, and cracks before it breaks")
    void aSwingDamagesBlocksInTheArc() {
        MeleeSystem melee = new MeleeSystem();
        melee.setDestruction(destruction);
        melee.setEffectSink(sink);

        Player attacker = shooter(1380f, 100f, 0f);
        MapBlock crate = crate(arena);

        // The crate's near face is 20 units away, well inside the blade's 64; 34 damage into a
        // 48 pool chips it.
        melee.swing(attacker, MeleeRegistry.of(MeleeId.NIGHT_LETTER), List.of(attacker), damage);
        assertEquals(34f, destruction.damage(crate), 1e-3f);
        assertTrue(sink.emitted.isEmpty(), "a chip is not a break, so it raises nothing of its own");
        assertEquals(0, destruction.revision());

        melee.swing(attacker, MeleeRegistry.of(MeleeId.NIGHT_LETTER), List.of(attacker), damage);

        assertEquals(1, destruction.revision());
        assertFalse(destruction.liveMap().blocks().contains(crate));
        assertEquals(DamageSource.MELEE, destruction.destroyedBlocks().get(0).cause());
        assertEquals(1, sink.emitted.size());
        assertEquals(EffectType.BULLET_IMPACT_WOOD, sink.emitted.get(0).type);
        assertEquals(CRATE_LEFT, sink.emitted.get(0).x, EPSILON);
        assertEquals(125f, sink.emitted.get(0).y, EPSILON,
            "the debris lands on the face the swing reached");
    }

    @Test
    @DisplayName("a stone face refuses rounds and a swing outright, however many arrive")
    void indestructibleSurfacesTakeNothing() {
        ArenaMap walled = ArenaMapFormat.parse(String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 0 0 3000 100",
            "centred breakable stone 1490 100 20 60",
            ""));
        MapDamageState state = MapDamageState.intact(walled);
        BulletSystem rounds = new BulletSystem(walled);
        rounds.setDestruction(state);
        MeleeSystem melee = new MeleeSystem();
        melee.setDestruction(state);

        MapBlock stone = blockAt(walled, 1490f);
        Player player = shooter(1300f, 100f, 0f);

        for (int shot = 0; shot < 3; shot++) {
            assertNotNull(rounds.spawn(player, WeaponId.IRON_CARBINE, 0f));
            for (int tick = 0; tick < 30 && rounds.count() > 0; tick++) {
                rounds.step(TICK, List.of(player), damage);
            }
        }
        melee.swing(shooter(1470f, 100f, 0f), MeleeRegistry.of(MeleeId.NIGHT_LETTER),
            List.of(), damage);

        assertEquals(0f, state.damage(stone), 1e-4f,
            "stone takes no bullet damage and no melee damage, so its pool is untouched");
        assertEquals(0, state.revision());
        assertEquals(
            3,
            rounds.terrainImpactCount(),
            "each round stopped in the face it could not open, as every wall does");
    }
}
