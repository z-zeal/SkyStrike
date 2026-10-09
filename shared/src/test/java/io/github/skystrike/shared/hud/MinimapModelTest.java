package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.hud.MinimapModel.Allegiance;
import io.github.skystrike.shared.hud.MinimapModel.Blip;
import io.github.skystrike.shared.hud.MinimapModel.Box;
import io.github.skystrike.shared.hud.MinimapModel.Input;
import io.github.skystrike.shared.hud.MinimapModel.Kind;
import io.github.skystrike.shared.hud.MinimapModel.View;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.vision.ObserverSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The minimap's read model: what is on the map, why, and for how long.
 *
 * <p>The sight lines used here are the ones {@code ObserverSetTest} verifies against today's
 * {@link ArenaMap#standard()} geometry: open air at y≈600 between x=200 and x=700, so a viewer at
 * (400, 600) facing right lights a body at (700, 600) and nothing at (200, 600) behind them.
 *
 * <p>Each test gets its own model instance (JUnit's per-method lifecycle), so no memory carries
 * over. Within a test, order matters: a sighting that has been remembered ghosts on the next
 * frame, so the ungated case is asserted before the gated one wherever both are checked.
 */
class MinimapModelTest {

    private static final long T0 = 100_000L;
    private static final int TEAM_A = Team.TEAM_A.index();
    private static final int TEAM_B = Team.TEAM_B.index();
    private static final int NEUTRAL = Team.NEUTRAL.index();

    private final ArenaMap arena = ArenaMap.standard();
    private final MinimapModel model = new MinimapModel(arena);

    private static Player player(int id, int teamIndex, float x, float y, float aimAngleDeg) {
        Player player = new Player(id, "P" + id, teamIndex, x, y);
        player.aimAngle = aimAngleDeg;
        return player;
    }

    private static DroneEntity drone(int id, int ownerId, int teamIndex, float x, float y) {
        return new DroneEntity(id, ownerId, teamIndex, x, y, 0f, GadgetConfig.DRONE_HEALTH);
    }

    private static CameraEntity camera(
            int id, int ownerId, int teamIndex, float x, float y, boolean stuck) {
        CameraEntity camera =
            new CameraEntity(id, ownerId, teamIndex, x, y, 0f, 0f, 0f, GadgetConfig.CAMERA_HEALTH);
        camera.stuck = stuck;
        return camera;
    }

    /** The viewer's own eyes at hip reach, over the devices handed in — the shader's own set. */
    private static ObserverSet eyes(
            Player viewer, List<DroneEntity> drones, List<CameraEntity> cameras) {
        return ObserverSet.forViewer(viewer, VisionConfig.REACH_HIP, drones, null, cameras);
    }

    private static Input input(
            Player viewer,
            List<Player> others,
            List<DroneEntity> drones,
            List<CameraEntity> cameras,
            boolean alliesAlways) {
        return new Input(
            viewer, others, drones, cameras, eyes(viewer, drones, cameras), null, null, alliesAlways);
    }

    private static Input input(Player viewer, List<Player> others, boolean alliesAlways) {
        return input(viewer, others, List.of(), List.of(), alliesAlways);
    }

    private static Blip only(List<Blip> blips, Allegiance allegiance) {
        List<Blip> found = blips.stream().filter(b -> b.allegiance() == allegiance).toList();
        assertEquals(1, found.size(), "expected exactly one " + allegiance + " marker in " + blips);
        return found.get(0);
    }

    private static Blip onlyKind(List<Blip> blips, Kind kind) {
        List<Blip> found = blips.stream().filter(b -> b.kind() == kind).toList();
        assertEquals(1, found.size(), "expected exactly one " + kind + " marker in " + blips);
        return found.get(0);
    }

    // --- Projection -----------------------------------------------------------------------------

    @Test
    @DisplayName("the arena is normalised bottom-left origin, y up, and the aspect is quoted")
    void terrainIsNormalised() {
        assertEquals(WorldConfig.ARENA_WIDTH / WorldConfig.ARENA_HEIGHT, model.arenaAspect(), 0.0001f);
        assertEquals(arena.solids().size(), model.terrainBoxes().size(), "one box per solid");

        Box ground = model.terrainBoxes().stream()
            .filter(b -> b.width() == 1f && b.y() == 0f)
            .findFirst()
            .orElseThrow();
        assertEquals(WorldConfig.GROUND_HEIGHT / WorldConfig.ARENA_HEIGHT, ground.height(), 0.0001f,
            "the ground slab spans the full width up from the bottom edge");

        Box ceiling = model.terrainBoxes().stream()
            .filter(b -> b.width() == 1f && b.y() > 0.9f)
            .findFirst()
            .orElseThrow();
        assertEquals(1f, ceiling.y() + ceiling.height(), 0.0001f,
            "the ceiling slab reaches the top edge, so the map frame is the arena shell");

        for (Box box : model.terrainBoxes()) {
            assertTrue(box.x() >= 0f && box.x() + box.width() <= 1.0001f, "inside the arena: " + box);
            assertTrue(box.y() >= 0f && box.y() + box.height() <= 1.0001f, "inside the arena: " + box);
        }
    }

    @Test
    @DisplayName("a body's marker sits at its centre, in normalised arena space")
    void blipsAreNormalisedCentres() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        View view = model.view(input(viewer, List.of()), T0);

        Blip self = only(view.blips(), Allegiance.SELF);
        assertEquals(400f / WorldConfig.ARENA_WIDTH, self.x(), 0.0001f);
        // The centre: feet + half the standing height. Not the feet, and not the eye.
        assertEquals(625f / WorldConfig.ARENA_HEIGHT, self.y(), 0.0001f);
        assertEquals(Kind.PLAYER, self.kind());
        assertFalse(self.stale());
        assertEquals(1f, self.alpha());
        assertEquals(1, view.liveCount());
    }

    @Test
    @DisplayName("the widget's frame is fitted to the arena aspect and centred, never stretched")
    void fitPreservesAspect() {
        View view = model.view(input(player(1, TEAM_A, 400f, 600f, 0f), List.of()), T0);

        // A 210x160 box holds a 3:2 map at 210x140, letterboxed top and bottom.
        Box wide = view.fit(10f, 20f, 210f, 160f);
        assertEquals(210f, wide.width(), 0.001f);
        assertEquals(140f, wide.height(), 0.001f);
        assertEquals(10f, wide.x(), 0.001f);
        assertEquals(30f, wide.y(), 0.001f, "centred vertically in the leftover 20 pixels");

        // A tall, narrow box is height-limited instead.
        Box tall = view.fit(0f, 0f, 100f, 200f);
        assertEquals(100f, tall.width(), 0.001f);
        assertEquals(100f / 1.5f, tall.height(), 0.001f);
        assertEquals((200f - 100f / 1.5f) / 2f, tall.y(), 0.001f);

        assertEquals(0f, view.fit(5f, 5f, 0f, 100f).width(),
            "an empty frame draws nothing rather than dividing by zero");
    }

    @Test
    @DisplayName("the camera's view rectangle is normalised and clamped into the arena")
    void viewportIsClamped() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);

        View inside = model.view(new Input(
            viewer, List.of(), List.of(), List.of(), eyes(viewer, List.of(), List.of()), null,
            new Rect(300f, 400f, 600f, 400f), false), T0);
        assertNotNull(inside.viewport());
        assertEquals(0.1f, inside.viewport().x(), 0.0001f);
        assertEquals(0.2f, inside.viewport().y(), 0.0001f);
        assertEquals(0.2f, inside.viewport().width(), 0.0001f);
        assertEquals(0.2f, inside.viewport().height(), 0.0001f);

        View outside = model.view(new Input(
            viewer, List.of(), List.of(), List.of(), eyes(viewer, List.of(), List.of()), null,
            new Rect(-500f, -100f, 4000f, 3000f), false), T0);
        assertEquals(new Box(0f, 0f, 1f, 1f), outside.viewport(),
            "a detached camera looking past the edge still reports the whole arena");

        assertNull(model.view(new Input(
                viewer, List.of(), List.of(), List.of(), eyes(viewer, List.of(), List.of()), null,
                null, false), T0).viewport(),
            "no camera rectangle means no viewport marker");
    }

    @Test
    @DisplayName("before a spawn the frame is the arena and the camera, with nothing marked")
    void noViewerNoMarkers() {
        View view = model.view(new Input(
            null, List.of(), List.of(), List.of(), null, null, new Rect(0f, 0f, 1000f, 800f), false), T0);
        assertTrue(view.blips().isEmpty());
        assertEquals(model.terrainBoxes(), view.terrain());
        assertNotNull(view.viewport());
        assertEquals(0, view.liveCount());
        assertEquals(0, view.ghostCount());
    }

    // --- The gate -------------------------------------------------------------------------------

    @Test
    @DisplayName("an enemy marker appears only where the fog lights them")
    void enemiesAreGatedByTheObserverSet() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);

        View lit = model.view(input(viewer, List.of(player(2, TEAM_B, 700f, 600f, 0f)), false), T0);
        assertEquals(Kind.PLAYER, only(lit.blips(), Allegiance.ENEMY).kind());
        assertFalse(only(lit.blips(), Allegiance.ENEMY).stale());
        assertEquals(2, lit.liveCount(), "the viewer and the enemy they can see");
        assertEquals(0, lit.ghostCount());

        // A different enemy, out of the cone: the earlier one is not left behind as a ghost
        // because it is gone from the frame, which is what the snapshot's absence means.
        View dark = model.view(input(viewer, List.of(player(3, TEAM_B, 200f, 600f, 0f)), false), T0);
        assertTrue(dark.blips().stream().noneMatch(b -> b.allegiance() == Allegiance.ENEMY),
            "behind the viewer: the snapshot knows where they are, the map must not say");
        assertEquals(1, dark.liveCount(), "only the viewer's own marker");
    }

    @Test
    @DisplayName("teammates are shown whether or not they are lit, unless the toggle says otherwise")
    void allyExemptionFollowsTheToggle() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player behind = player(2, TEAM_A, 200f, 600f, 0f);

        // Ungated first, so nothing is remembered yet to ghost on the second frame.
        View hidden = model.view(input(viewer, List.of(behind), false), T0);
        assertTrue(hidden.blips().stream().noneMatch(b -> b.allegiance() == Allegiance.ALLY),
            "with the toggle off a teammate is gated exactly like an enemy");

        View shown = model.view(input(viewer, List.of(behind), true), T0);
        Blip ally = only(shown.blips(), Allegiance.ALLY);
        assertFalse(ally.stale(), "a teammate shown by rule is a live marker, not a memory");
        assertEquals(200f / WorldConfig.ARENA_WIDTH, ally.x(), 0.0001f);
    }

    @Test
    @DisplayName("Neutral has no teammates, so the exemption never covers another Neutral")
    void neutralGetsNoExemption() {
        Player viewer = player(1, NEUTRAL, 400f, 600f, 0f);
        Player otherNeutral = player(2, NEUTRAL, 200f, 600f, 0f);

        View view = model.view(input(viewer, List.of(otherNeutral), true), T0);
        assertTrue(view.blips().stream().allMatch(b -> b.allegiance() == Allegiance.SELF),
            "two Neutrals fight each other, so neither is shown for free");
    }

    @Test
    @DisplayName("devices are markers too: the viewer's own always, anyone else's only when lit")
    void deviceMarkers() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 180f);
        DroneEntity own = drone(10, 1, TEAM_A, 700f, 650f);
        DroneEntity enemy = drone(11, 2, TEAM_B, 700f, 650f);

        // Facing away from both: the viewer's own drone is their property, the enemy's is not lit.
        View away = model.view(input(viewer, List.of(), List.of(own), List.of(), false), T0);
        assertEquals(2, away.blips().size(), "the viewer's body and their own drone");
        Blip ownBlip = onlyKind(away.blips(), Kind.DRONE);
        assertEquals(Allegiance.SELF, ownBlip.allegiance());
        assertFalse(ownBlip.stale());

        View withEnemy = model.view(input(viewer, List.of(), List.of(own, enemy), List.of(), false), T0 + 10L);
        assertEquals(2, withEnemy.blips().size(), "an enemy drone in the dark is not a marker");

        viewer.aimAngle = 0f;
        View enemyLit = model.view(input(viewer, List.of(), List.of(enemy), List.of(), false), T0 + 20L);
        Blip enemyDrone = only(enemyLit.blips(), Allegiance.ENEMY);
        assertEquals(Kind.DRONE, enemyDrone.kind());
        assertEquals(700f / WorldConfig.ARENA_WIDTH, enemyDrone.x(), 0.0001f);
        assertEquals(650f / WorldConfig.ARENA_HEIGHT, enemyDrone.y(), 0.0001f,
            "a device is marked at its own position, not at a box corner");

        // An ally's device follows the teammate toggle exactly like an ally's body. Gated first.
        DroneEntity allyDrone = drone(12, 3, TEAM_A, 200f, 650f);
        viewer.aimAngle = 180f;
        assertEquals(1, model.view(input(viewer, List.of(), List.of(allyDrone), List.of(), false), T0 + 30L)
            .blips().size(), "an allied drone in the dark is gated with the toggle off");
        assertEquals(2, model.view(input(viewer, List.of(), List.of(allyDrone), List.of(), true), T0 + 40L)
            .blips().size(), "and shown for free with it on");
    }

    @Test
    @DisplayName("a stuck camera the viewer owns is on the map however far out of sight it is")
    void ownCameraIsMarked() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 180f);
        CameraEntity stuck = camera(20, 1, TEAM_A, 1900f, 300f, true);

        View view = model.view(input(viewer, List.of(), List.of(), List.of(stuck), false), T0);
        assertEquals(2, view.blips().size(), "the viewer and their camera, 1500 units out of sight");
        Blip post = onlyKind(view.blips(), Kind.CAMERA);
        assertEquals(Allegiance.SELF, post.allegiance());
        assertEquals(1900f / WorldConfig.ARENA_WIDTH, post.x(), 0.0001f);
    }

    // --- Memory ---------------------------------------------------------------------------------

    @Test
    @DisplayName("a lost sighting holds, fades and expires on the wall clock")
    void ghostEnvelope() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player enemy = player(2, TEAM_B, 700f, 600f, 0f);

        View seen = model.view(input(viewer, List.of(enemy), false), T0);
        assertEquals(0, seen.ghostCount());
        assertEquals(1, model.trackedCount());

        viewer.aimAngle = 180f; // the enemy steps out of the cone
        long hold = T0 + (long) (MinimapModel.GHOST_HOLD_SECONDS * 1000f);
        View held = model.view(input(viewer, List.of(enemy), false), hold);
        Blip ghost = only(held.blips(), Allegiance.ENEMY);
        assertTrue(ghost.stale());
        assertEquals(1f, ghost.alpha(), "still fully lit at the end of the hold");
        assertEquals(1, held.ghostCount());
        assertEquals(1, held.liveCount(), "the viewer's own marker is the only live one");

        long midFade = T0 + (long) ((MinimapModel.GHOST_HOLD_SECONDS
            + MinimapModel.GHOST_FADE_SECONDS / 2f) * 1000f);
        assertEquals(0.5f, only(model.view(input(viewer, List.of(enemy), false), midFade)
            .blips(), Allegiance.ENEMY).alpha(), 0.02f);

        long expired = T0 + (long) (MinimapModel.GHOST_LIFETIME_SECONDS * 1000f) + 1L;
        View gone = model.view(input(viewer, List.of(enemy), false), expired);
        assertTrue(gone.blips().stream().noneMatch(b -> b.allegiance() == Allegiance.ENEMY));
        assertEquals(0, model.trackedCount(), "an expired sighting is forgotten, not kept forever");
    }

    @Test
    @DisplayName("the envelope is the same shape the kill feed ages by")
    void ghostAlphaIsShared() {
        assertEquals(1f, MinimapModel.ghostAlpha(-1f), "a clock running backwards cannot age a ghost");
        assertEquals(1f, MinimapModel.ghostAlpha(0f));
        assertEquals(1f, MinimapModel.ghostAlpha(MinimapModel.GHOST_HOLD_SECONDS));
        assertEquals(0.5f,
            MinimapModel.ghostAlpha(
                MinimapModel.GHOST_HOLD_SECONDS + MinimapModel.GHOST_FADE_SECONDS / 2f),
            0.001f);
        assertEquals(0f, MinimapModel.ghostAlpha(MinimapModel.GHOST_LIFETIME_SECONDS));
        assertEquals(0f, MinimapModel.ghostAlpha(MinimapModel.GHOST_LIFETIME_SECONDS + 10f));
    }

    @Test
    @DisplayName("a ghost stays where it was last seen — it is a memory, not a slow update")
    void ghostDoesNotTrack() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player enemy = player(2, TEAM_B, 700f, 600f, 0f);
        model.view(input(viewer, List.of(enemy), false), T0);

        // The viewer turns away and the enemy walks on. The snapshot still carries the new
        // position; the map keeps showing the last one that was actually lit.
        viewer.aimAngle = 180f;
        enemy.x = 900f;
        enemy.y = 800f;
        Blip ghost =
            only(model.view(input(viewer, List.of(enemy), false), T0 + 500L).blips(), Allegiance.ENEMY);
        assertTrue(ghost.stale());
        assertEquals(700f / WorldConfig.ARENA_WIDTH, ghost.x(), 0.0001f);
        assertEquals(625f / WorldConfig.ARENA_HEIGHT, ghost.y(), 0.0001f);
    }

    @Test
    @DisplayName("a death retires the marker at once, even mid-fade")
    void deathRetiresTheMarker() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player enemy = player(2, TEAM_B, 700f, 600f, 0f);
        model.view(input(viewer, List.of(enemy), false), T0);
        viewer.aimAngle = 180f;
        assertEquals(1, model.view(input(viewer, List.of(enemy), false), T0 + 100L).ghostCount());

        enemy.alive = false;
        View view = model.view(input(viewer, List.of(enemy), false), T0 + 200L);
        assertTrue(view.blips().stream().noneMatch(b -> b.allegiance() == Allegiance.ENEMY),
            "a corpse is not a sighting; the kill feed already said why");
        assertEquals(0, model.trackedCount());
    }

    @Test
    @DisplayName("a destroyed or disconnected device is forgotten rather than remembered")
    void absentTargetsAreForgotten() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        DroneEntity enemy = drone(11, 2, TEAM_B, 700f, 650f);
        model.view(input(viewer, List.of(), List.of(enemy), List.of(), false), T0);
        assertEquals(1, model.trackedCount());

        viewer.aimAngle = 180f;
        View hidden = model.view(input(viewer, List.of(), List.of(enemy), List.of(), false), T0 + 100L);
        assertEquals(1, hidden.ghostCount(), "still in the world, just out of sight: a ghost");

        // Shot down. The snapshot is not culled, so absence means gone, not hidden.
        View destroyed = model.view(input(viewer, List.of(), List.of(), List.of(), false), T0 + 200L);
        assertEquals(0, destroyed.ghostCount());
        assertEquals(0, model.trackedCount());
    }

    @Test
    @DisplayName("a respawn empties the map: the last life's sightings are about a body you left")
    void respawnClearsMemory() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player enemy = player(2, TEAM_B, 700f, 600f, 0f);
        model.view(input(viewer, List.of(enemy), false), T0);
        viewer.aimAngle = 180f;
        assertEquals(1, model.view(input(viewer, List.of(enemy), false), T0 + 100L).ghostCount());

        viewer.alive = false;
        model.view(input(viewer, List.of(enemy), false), T0 + 200L);
        viewer.alive = true;
        viewer.x = 240f;
        viewer.y = 180f;

        View respawned = model.view(input(viewer, List.of(enemy), false), T0 + 300L);
        assertEquals(0, respawned.ghostCount());
        assertEquals(0, model.trackedCount());
        assertEquals(1, respawned.blips().size(), "the fresh life's own marker, at the spawn");
        assertEquals(240f / WorldConfig.ARENA_WIDTH, respawned.blips().get(0).x(), 0.0001f);
    }

    @Test
    @DisplayName("the viewer's own marker is there alive or dead, and drawn last")
    void selfMarkerIsAlwaysLast() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        Player enemy = player(2, TEAM_B, 700f, 600f, 0f);

        View alive = model.view(input(viewer, List.of(enemy), false), T0);
        assertEquals(Allegiance.SELF, alive.blips().get(alive.blips().size() - 1).allegiance(),
            "last, so the widget draws it on top of anything it overlaps");

        viewer.alive = false;
        View dead = model.view(input(viewer, List.of(enemy), false), T0 + 100L);
        assertEquals(Allegiance.SELF, dead.blips().get(dead.blips().size() - 1).allegiance(),
            "you never lose track of where your own corpse is");
    }

    @Test
    @DisplayName("memory is bounded, forgetting the least recently seen sighting first")
    void memoryIsBounded() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        List<DroneEntity> allied = new ArrayList<>();
        int count = MinimapModel.MAX_TRACKED + 6;
        for (int i = 0; i < count; i++) {
            allied.add(drone(100 + i, 2 + i, TEAM_A, 100f + i * 40f, 600f));
        }

        View view = model.view(input(viewer, List.of(), allied, List.of(), true), T0);
        assertEquals(MinimapModel.MAX_TRACKED, model.trackedCount());
        assertEquals(count + 1, view.blips().size(),
            "bounding memory does not drop a live marker: every allied drone is still drawn");
        assertEquals(count + 1, view.liveCount());
        assertEquals(0, view.ghostCount());
    }

    @Test
    @DisplayName("a null input or null lists are an empty frame, not a crash")
    void nullsAreSurvivable() {
        assertEquals(0, model.view(null, T0).blips().size());

        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        View view = model.view(new Input(viewer, null, null, null, null, null, null, false), T0);
        assertEquals(1, view.blips().size(), "the viewer's own marker needs no eyes at all");
        assertEquals(Allegiance.SELF, view.blips().get(0).allegiance());
    }

    @Test
    @DisplayName("clear() forgets everything, for the transitions a respawn does not cover")
    void clearForgets() {
        Player viewer = player(1, TEAM_A, 400f, 600f, 0f);
        model.view(input(viewer, List.of(player(2, TEAM_B, 700f, 600f, 0f)), false), T0);
        assertEquals(1, model.trackedCount());

        model.clear();
        assertEquals(0, model.trackedCount());
    }
}
