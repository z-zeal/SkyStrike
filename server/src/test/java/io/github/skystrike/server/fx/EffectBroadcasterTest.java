package io.github.skystrike.server.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.WeaponId;
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
    @DisplayName("sound cues copy only attributed muzzle flashes, independently of visual culling")
    void gunfireCuesCopyAttributedMuzzleFlashes() {
        EffectSpawn ordinaryFlash = new EffectSpawn(EffectType.MUZZLE_FLASH, 10f, 20f, 0f, 1f);
        EffectSpawn attributedFlash = new EffectSpawn(EffectType.MUZZLE_FLASH, 1400f, 500f, 90f, 1f);
        attributedFlash.sourcePlayerId = 7;
        attributedFlash.weaponId = WeaponId.IRON_CARBINE.ordinal();
        EffectSpawn attributedExplosion = new EffectSpawn(EffectType.FRAG_EXPLOSION, 50f, 60f, 0f, 1f);
        attributedExplosion.sourcePlayerId = 8;
        attributedExplosion.weaponId = WeaponId.IRON_CARBINE.ordinal();

        broadcaster.emit(ordinaryFlash);
        broadcaster.emit(attributedFlash);
        broadcaster.emit(attributedExplosion);
        List<EffectSpawn> window = broadcaster.drain();
        List<EffectSpawn> gunfire = broadcaster.gunfireFor(window);

        assertEquals(1, gunfire.size());
        assertTrue(broadcaster.cullFor(
            window, Recipient.of(player(1700f, 500f, 0f)), arena, null).isEmpty(),
            "the sound cue is retained even though this recipient cannot see its muzzle flash");
        assertNotSame(attributedFlash, gunfire.get(0));
        assertEquals(attributedFlash.seed, gunfire.get(0).seed);
        assertEquals(7, gunfire.get(0).sourcePlayerId);
        assertEquals(WeaponId.IRON_CARBINE.ordinal(), gunfire.get(0).weaponId);
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
        assertTrue(broadcaster.cullFor(List.of(), Recipient.of(player(1300f, 400f, 0f)), arena, null).isEmpty());
        assertTrue(broadcaster.cullFor(null, Recipient.of(player(1300f, 400f, 0f)), arena, null).isEmpty());
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

        List<EffectSpawn> visible = broadcaster.cullFor(spawns, Recipient.of(observer), arena, null);
        assertEquals(1, visible.size());
        assertEquals(EffectType.BULLET_IMPACT_CONCRETE, visible.get(0).type);
    }

    @Test
    @DisplayName("an observer with their back to a distant effect is not told about it")
    void facingAwayIsCulled() {
        // The impact is 300 units behind the observer: outside the cone (the peripheral floor is
        // not enough, the same bar M6's player lights clear) and outside the 140-unit vision bubble.
        Player observer = player(1700f, 500f, 0f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 500f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, Recipient.of(observer), arena, null).isEmpty());
    }

    @Test
    @DisplayName("an effect just behind the observer is inside their vision bubble, so it is sent")
    void behindInsideBubbleIsDelivered() {
        // 100 units behind, facing away: outside the cone, inside the all-round bubble (M14).
        Player observer = player(1300f, 400f, 180f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f));

        assertEquals(1, broadcaster.cullFor(spawns, Recipient.of(observer), arena, null).size(),
            "the body's bubble is all round, so the back of the body sees this close");
    }

    @Test
    @DisplayName("a drone's bubble delivers an effect behind the drone, outside its own cone")
    void droneBubbleDeliversEffectBehindIt() {
        // The drone (70-unit bubble) faces east, away from an impact 50 units west of it. Note the
        // culler's device branch still judges a device's cone on the looser entity bar, so this
        // passes on the cone too; the bubble's own rule, on the presentation bar, is pinned by
        // ObserverSetTest.deviceBubbleLightsCloseAroundIt. The observer is far enough off to see nothing.
        Player observer = player(1700f, 500f, 0f);
        DroneEntity drone = new DroneEntity(1, 1, 0, 1450f, 500f, 0f, 30f);
        EffectBroadcaster.Recipient recipient = new EffectBroadcaster.Recipient(
            observer, List.of(drone), List.of());
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 500f, 0f, 1f));

        assertEquals(1, broadcaster.cullFor(spawns, recipient, arena, null).size());
    }

    @Test
    @DisplayName("an observer behind the room wall is not told about an effect inside the room")
    void occludedEffectIsCulled() {
        Player observer = player(1050f, 400f, 0f); // outside the room, facing it
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, Recipient.of(observer), arena, null).isEmpty());
    }

    @Test
    @DisplayName("an effect inside the cone but beyond vision reach is culled")
    void outOfReachEffectIsCulled() {
        Player observer = player(300f, 1000f, 0f); // far west, facing east down the arena
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.FRAG_EXPLOSION, 2400f, 1000f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, Recipient.of(observer), arena, null).isEmpty());
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

        assertTrue(broadcaster.cullFor(List.of(unscaled), Recipient.of(observer), arena, null).isEmpty());
        assertEquals(1, broadcaster.cullFor(List.of(scaled), Recipient.of(observer), arena, null).size());
    }

    @Test
    @DisplayName("mixed batches keep exactly the visible effects, in order")
    void mixedBatchKeepsOnlyVisibleEffects() {
        Player observer = player(1300f, 400f, 0f);
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f), // visible
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 400f, 0f, 1f), // visible
            new EffectSpawn(EffectType.MUZZLE_FLASH, 300f, 1000f, 0f, 1f)); // out of reach

        List<EffectSpawn> visible = broadcaster.cullFor(spawns, Recipient.of(observer), arena, null);
        assertEquals(2, visible.size());
    }

    @Test
    @DisplayName("an effect seen only through the recipient's own drone is still delivered")
    void effectVisibleOnlyThroughOwnedDroneIsDelivered() {
        // The observer is 300 units from the impact and faces away: neither their cone nor their
        // bubble reaches it. Their drone sits 50 units east of the impact, facing it: the drone's
        // cone is an extra observer.
        Player observer = player(1700f, 500f, 0f);
        DroneEntity drone = new DroneEntity(1, 1, 0, 1450f, 500f, 180f, 30f);
        EffectBroadcaster.Recipient recipient = new EffectBroadcaster.Recipient(
            observer, List.of(drone), List.of());

        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 500f, 0f, 1f));

        assertEquals(1, broadcaster.cullFor(spawns, recipient, arena, null).size(),
            "the drone sees the impact even with the player's back to it");
    }

    @Test
    @DisplayName("an effect beyond the drone's reach is culled even with the drone deployed")
    void effectBeyondDroneReachIsCulled() {
        Player observer = player(1300f, 400f, 180f);
        DroneEntity drone = new DroneEntity(1, 1, 0, 1390f, 400f, 0f, 30f);
        EffectBroadcaster.Recipient recipient = new EffectBroadcaster.Recipient(
            observer, List.of(drone), List.of());

        // 1400 units east of the drone: far past its 250-unit cone.
        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.FRAG_EXPLOSION, 2790f, 400f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, recipient, arena, null).isEmpty());
    }

    @Test
    @DisplayName("a stuck camera the recipient owns counts as an observer; a flying one does not")
    void stuckCameraObservesButFlyingCameraDoesNot() {
        Player observer = player(1700f, 500f, 0f);
        CameraEntity stuck = new CameraEntity(1, 1, 0, 1450f, 500f, 0f, 0f, 180f, 20f);
        stuck.stuck = true;
        CameraEntity flying = new CameraEntity(2, 1, 0, 1450f, 500f, 300f, 100f, 180f, 20f);

        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 500f, 0f, 1f));

        assertEquals(1, broadcaster.cullFor(
            spawns,
            new EffectBroadcaster.Recipient(observer, List.of(), List.of(stuck)),
            arena,
            null).size(), "the stuck camera's cone sees the impact");
        assertTrue(broadcaster.cullFor(
            spawns,
            new EffectBroadcaster.Recipient(observer, List.of(), List.of(flying)),
            arena,
            null).isEmpty(), "a camera still in flight projects no cone");
    }

    @Test
    @DisplayName("someone else's drone is not this recipient's observer")
    void anotherPlayersDroneDoesNotCount() {
        Player observer = player(1700f, 500f, 0f);
        DroneEntity notMine = new DroneEntity(1, 99, 1, 1450f, 500f, 180f, 30f);
        EffectBroadcaster.Recipient recipient = new EffectBroadcaster.Recipient(
            observer, List.of(notMine), List.of());

        List<EffectSpawn> spawns = List.of(
            new EffectSpawn(EffectType.BULLET_IMPACT_CONCRETE, 1400f, 500f, 0f, 1f));

        assertTrue(broadcaster.cullFor(spawns, recipient, arena, null).isEmpty(),
            "only the recipient's own devices are extra observers");
    }

    private static Player player(float x, float y, float aimDegrees) {
        Player player = new Player(1, "Observer", 0, x, y);
        player.aimAngle = aimDegrees;
        return player;
    }
}
