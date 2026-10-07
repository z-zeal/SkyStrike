package io.github.skystrike.server.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The M7 §8.1 effect channel's server half: batching, seed assignment, the pending cap, and the
 * per-recipient {@code VisionMath} culling that decides who is told about a detonation at all.
 */
class EffectBroadcasterTest {

    private ArenaMap arena;
    private EffectBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        arena = ArenaMap.standard();
        broadcaster = new EffectBroadcaster();
    }

    @Test
    @DisplayName("emitted spawns accumulate until the snapshot drains them, with unique seeds")
    void emitAccumulatesAndDrainClears() {
        broadcaster.emit(new EffectSpawn(EffectType.FRAG_EXPLOSION, 100f, 200f, 0f, 1f));
        broadcaster.emit(new EffectSpawn(EffectType.MUZZLE_FLASH, 300f, 400f, 90f, 1f));
        assertEquals(2, broadcaster.pendingCount());

        List<EffectSpawn> drained = broadcaster.drain();
        assertEquals(2, drained.size());
        assertEquals(1, drained.get(0).seed);
        assertEquals(2, drained.get(1).seed);
        assertEquals(0, broadcaster.pendingCount());
        assertTrue(broadcaster.drain().isEmpty(), "the window restarts empty");
    }

    @Test
    @DisplayName("the pending window is capped so effect spam cannot exhaust the heap")
    void pendingWindowIsCapped() {
        for (int i = 0; i < EffectBroadcaster.MAX_PENDING_SPAWNS + 25; i++) {
            broadcaster.emit(new EffectSpawn(EffectType.MUZZLE_FLASH, i, 0f, 0f, 1f));
        }
        assertEquals(EffectBroadcaster.MAX_PENDING_SPAWNS, broadcaster.pendingCount());
    }

    @Test
    @DisplayName("a null or empty batch is a no-op, and draining never returns null")
    void degenerateInputIsSafe() {
        broadcaster.emit(null);
        assertEquals(0, broadcaster.pendingCount());
        assertTrue(broadcaster.drain().isEmpty());
        assertTrue(broadcaster.cullFor(List.of(), player(1300f, 400f, 0f), arena, null).isEmpty());
        assertTrue(broadcaster.cullFor(null, player(1300f, 400f, 0f), arena, null).isEmpty());
        assertTrue(broadcaster.cullFor(
            List.of(new EffectSpawn(EffectType.FRAG_EXPLOSION, 1400f, 400f, 0f, 1f)),
            null,
            arena,
            null).isEmpty());
    }

    @Test
    @DisplayName("an observer who can see the effect's disc is told about it")
    void visibleEffectIsDelivered() {
        // Mid-room, clear of the walls and pillars; the observer faces it down the room.
        Player observer = player(1300f, 400f, 0f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f));

        List<EffectSpawn> visible = broadcaster.cullFor(spawns, observer, arena, null);
        assertEquals(1, visible.size());
        assertEquals(EffectType.BULLET_IMPACT_CONCRETE, visible.get(0).type);
    }

    @Test
    @DisplayName("an observer with their back to the effect is not told about it")
    void facingAwayIsCulled() {
        // Same room, but the impact is behind the observer: outside the cone, the peripheral
        // floor is not enough (the same bar M6's player lights clear).
        Player observer = player(1300f, 400f, 180f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, observer, arena, null).isEmpty());
    }

    @Test
    @DisplayName("an observer behind the room wall is not told about an effect inside the room")
    void occludedEffectIsCulled() {
        Player observer = player(1050f, 400f, 0f); // outside the room, facing it
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, observer, arena, null).isEmpty());
    }

    @Test
    @DisplayName("an effect inside the cone but beyond vision reach is culled")
    void outOfReachEffectIsCulled() {
        Player observer = player(300f, 1000f, 0f); // far west, facing east down the arena
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.FRAG_EXPLOSION, 2400f, 1000f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, observer, arena, null).isEmpty());
    }

    @Test
    @DisplayName("scale widens the culling disc: a gap impact reaches observers only when scaled up")
    void scaleWidensTheCullingDisc() {
        // In the gap between the room's inner pillar (x 1800..1816, y 300..430) and its right
        // wall (x 1856..1880): the observer inside the room cannot see the impact itself, the
        // pillar blocks every sample of the small disc, but a scaled-up disc samples points
        // west of the pillar and above it, which are clearly visible.
        Player observer = player(1300f, 400f, 0f);
        EffectSpawn unscaled = new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1830f, 400f, 0f, 1f);
        EffectSpawn scaled = new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1830f, 400f, 0f, 3f);

        assertTrue(broadcaster.cullFor(List.of(unscaled), observer, arena, null).isEmpty());
        assertEquals(1, broadcaster.cullFor(List.of(scaled), observer, arena, null).size());
    }

    @Test
    @DisplayName("mixed batches keep exactly the visible effects, in order")
    void mixedBatchKeepsOnlyVisibleEffects() {
        Player observer = player(1300f, 400f, 0f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f), // visible
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f), // visible
            new EffectSpawn(EffectType.MUZZLE_FLASH, 300f, 1000f, 0f, 1f)); // out of reach

        List<EffectSpawn> visible = broadcaster.cullFor(spawns, observer, arena, null);
        assertEquals(2, visible.size());
    }

    private static Player player(float x, float y, float aimDegrees) {
        Player player = new Player(1, "Observer", 0, x, y);
        player.aimAngle = aimDegrees;
        return player;
    }
}
