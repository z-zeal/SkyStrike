package io.github.skystrike.server.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A detonation damages the breakables it reaches through the shared state, and the block that goes
 * raises its own debris: the blast's own effect covers the moment, the break's effect covers the
 * block that is no longer there.
 *
 * <p>The room is authored because the shipped arena cannot lose anything. The two crates carry a
 * multiplier that puts one inside the frag's numbers at its distance and the other outside them, so
 * a single detonation is shown to break the near crate and only chip the far one — the falloff, not
 * a kind check, is what decides.
 */
class UtilityBlockDestructionTest {

    private static final float NEAR_CRATE_LEFT = 1400f;
    private static final float FAR_CRATE_LEFT = 1560f;

    /** Wood's 120 durability under an authored 0.7 multiplier: 84, between the two distances. */
    private static final String CRATES = "mirrored breakable wood 1400 100 40 60 durability=0.7";

    private ArenaMap arena;
    private DamageService damage;

    @BeforeEach
    void setUp() {
        arena = ArenaMapFormat.parse(String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 0 0 3000 100",
            CRATES,
            ""));
        damage = new DamageService(new KillFeedService());
    }

    private static MapBlock blockAt(ArenaMap map, float left) {
        for (MapBlock block : map.blocks()) {
            if (block.rect().x() == left) {
                return block;
            }
        }
        throw new AssertionError("no block with left edge " + left + " in " + map.blocks());
    }

    /** A frag thrown by a player standing well outside its radius, so only blocks are in play. */
    private static ThrownUtility fragAt(Player owner, float x, float y) {
        return new ThrownUtility(10, owner.id, owner.teamIndex,
            UtilityId.FRAG.ordinal(), x, y, 0f, 0f, 0f);
    }

    @Test
    @DisplayName("a frag breaks the crate at its feet and only chips the one further off")
    void aBlastBreaksWhatItReaches() {
        MapDamageState destruction = MapDamageState.intact(arena);
        UtilitySystem utilities = new UtilitySystem(arena);
        utilities.setDestruction(destruction);
        RecordingSink sink = new RecordingSink();
        utilities.setEffectSink(sink);

        Player owner = new Player(1, "Owner", 0, 900f, 100f);
        ThrownUtility frag = fragAt(owner, 1460f, 130f);

        utilities.detonate(frag, UtilityRegistry.of(UtilityId.FRAG), List.of(owner), damage);

        MapBlock near = blockAt(arena, NEAR_CRATE_LEFT);
        MapBlock far = blockAt(arena, FAR_CRATE_LEFT);
        // 20 units to the near crate's face and 100 to the far one's, under a 100-damage frag with
        // linear falloff over 350: 94.3 against an 84 pool breaks, 71.4 only spends part of it.
        assertEquals(1, destruction.revision(), "one break, however many blocks were hit");
        assertTrue(destruction.isDestroyed(near));
        assertFalse(destruction.liveMap().blocks().contains(near));
        assertFalse(destruction.isDestroyed(far), "the far crate is chipped, not destroyed");
        assertTrue(destruction.damage(far) > 0f, "and it is chipped, not ignored");
        assertTrue(destruction.remainingDurability(far) > 0f, "with pool left to spend");

        BlockBreak broke = destruction.destroyedBlocks().get(0);
        assertEquals(near, broke.block());
        assertEquals(DamageSource.BLAST, broke.cause());
        assertEquals(NEAR_CRATE_LEFT, broke.x(), 1e-3f, "the blast reached the crate's near face");
        assertEquals(130f, broke.y(), 1e-3f);
        assertFalse(broke.shattered(), "a crate is spent, not shattered");

        // Two events: the block that left raising its own debris, then the explosion itself.
        assertEquals(2, sink.emitted.size(), "one break, one explosion: " + sink.emitted);
        EffectSpawn debris = sink.emitted.get(0);
        assertEquals(EffectType.BULLET_IMPACT_WOOD, debris.type);
        assertEquals(NEAR_CRATE_LEFT, debris.x, 1e-2f);
        assertEquals(130f, debris.y, 1e-2f);
        assertEquals(
            180f,
            Math.abs(debris.angle),
            1e-2f,
            "debris travels away from the detonation, which is east of the crate");
        assertEquals(
            EffectType.FRAG_EXPLOSION,
            sink.emitted.get(1).type,
            "the explosion's own effect is emitted once, after the blocks it took");
    }

    @Test
    @DisplayName("a wall between the charge and a crate keeps the crate intact")
    void aWallIsCover() {
        ArenaMap walled = ArenaMapFormat.parse(String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 0 0 3000 100",
            "mirrored wall stone 1300 100 40 200",
            CRATES,
            ""));
        MapDamageState destruction = MapDamageState.intact(walled);
        UtilitySystem utilities = new UtilitySystem(walled);
        utilities.setDestruction(destruction);

        MapBlock crate = blockAt(walled, NEAR_CRATE_LEFT);
        MapBlock wall = blockAt(walled, 1300f);
        Player owner = new Player(1, "Owner", 0, 900f, 100f);

        utilities.detonate(fragAt(owner, 1100f, 130f), UtilityRegistry.of(UtilityId.FRAG),
            List.of(owner), damage);

        assertEquals(0, destruction.revision(), "a wall is permanent, and it shielded the crate");
        assertEquals(0f, destruction.damage(crate), 1e-4f, "occlusion is absolute for blocks too");
        assertEquals(0f, destruction.damage(wall), 1e-4f, "a wall has no pool to spend as cover");
    }

    /** The simplest possible sink: a list, so the tests assert on exactly what was emitted. */
    private static final class RecordingSink implements EffectSink {
        final List<EffectSpawn> emitted = new ArrayList<>();

        @Override
        public void emit(EffectSpawn spawn) {
            emitted.add(spawn);
        }
    }
}
