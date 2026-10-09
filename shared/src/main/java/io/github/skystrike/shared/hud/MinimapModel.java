package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.vision.ObserverSet;
import io.github.skystrike.shared.vision.SmokeVolume;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The minimap as data (roadmap Phase 7 HUD, mechanics §4): who is on it, where, and how long a
 * sighting is still worth showing.
 *
 * <p>The widget in {@code core/ui/hud/Minimap} only draws what this class hands it. That split is
 * the reason the model lives here rather than in the client: the interesting question is not how to
 * draw a dot but <b>which dots may be drawn at all</b>, and that question has a wrong answer that
 * looks like a feature. The snapshot the client receives is deliberately not culled by the vision
 * cone — {@code GameServer.broadcastSnapshot} explains why — so every player's true position is
 * sitting in memory on every client. A minimap that read it directly would be a wallhack the game
 * ships with, showing enemies the fog is hiding and the renderer swore were invisible.
 *
 * <p>So every marker is gated by {@link ObserverSet#isLit}: the same eyes, the same cone angles
 * and the same presentation bar that decide what the visibility pass lights. A blip appears if
 * and only if the thing it marks is lit on the viewer's screen — not merely non-zero, which a
 * body standing behind you also is.
 *
 * <p><b>Memory.</b> Gating alone would make markers strobe at the cone edge — the same failure the
 * interpolated ADS reach exists to avoid. A sighting therefore lingers as a <i>ghost</i>: it holds,
 * fades, and expires on the wall clock. A ghost is a memory of an observation, never a low-rate
 * update: its position is frozen at the last moment it was genuinely seen, so it cannot quietly
 * track a target the viewer has lost. Ghosts are retired immediately when their target stops
 * existing — the snapshot is unculled, so an absent player or device is a dead or destroyed one,
 * not a hidden one, and a marker for it would misreport the world rather than the view.
 *
 * <p><b>Coordinates.</b> Everything this class emits is in normalised arena space: {@code (0, 0)}
 * is the arena's bottom-left corner, {@code (1, 1)} its top-right, and y is <b>up</b> — the world's
 * orientation and the HUD's own, since {@code Matrix4.setToOrtho2D} is y-up. A {@link Box} therefore
 * maps straight into {@code ShapeRenderer.rect} once the widget has scaled it into its frame, with
 * no flip to forget.
 */
public final class MinimapModel {

    /**
     * How long a lost sighting stays fully lit before it starts to fade. <b>Provisional</b> — no
     * plan document sets a minimap memory; this is long enough that a target stepping behind a
     * crate does not blink, short enough that the marker is clearly stale before it expires.
     */
    public static final float GHOST_HOLD_SECONDS = 1.0f;

    /** How long the fade itself takes. <b>Provisional</b>, with the hold above. */
    public static final float GHOST_FADE_SECONDS = 2.5f;

    /** Total life of one lost sighting. */
    public static final float GHOST_LIFETIME_SECONDS = GHOST_HOLD_SECONDS + GHOST_FADE_SECONDS;

    /**
     * Upper bound on remembered sightings. Defensive rather than expected: memory only ever holds
     * targets present in the snapshot, so the join cap bounds it already. Bounded anyway, because
     * an unbounded map keyed by entity id would grow for the life of the process.
     */
    public static final int MAX_TRACKED = 64;

    /** What a marker marks. Decides its shape in the widget. */
    public enum Kind {
        PLAYER, DRONE, CAMERA
    }

    /** Whose side a marker is on. Decides its colour in the widget. */
    public enum Allegiance {
        /** The viewer's own body or device. */
        SELF,
        /** A teammate: the same allegiance, and not Neutral (mechanics §10). */
        ALLY,
        /** Anyone else, including another Neutral. */
        ENEMY
    }

    /**
     * A rectangle in normalised arena space, y-up: {@code (0, 0)} bottom-left, {@code (1, 1)}
     * top-right. Scaled by the widget's fitted frame, it is a screen rectangle.
     */
    public record Box(float x, float y, float width, float height) {
    }

    /**
     * One marker.
     *
     * @param x          normalised arena x of the marker's centre
     * @param y          normalised arena y of the marker's centre
     * @param kind       what it marks, which is how the widget picks a shape
     * @param allegiance whose side it is on, which is how the widget picks a colour
     * @param alpha      1 while the sighting is live or freshly lost, falling to 0 as it expires
     * @param stale      true for a remembered sighting rather than a live one — the widget draws
     *                   these hollow, so a memory is never mistaken for something you can see
     */
    public record Blip(float x, float y, Kind kind, Allegiance allegiance, float alpha, boolean stale) {
    }

    /**
     * Everything one minimap frame is built from. The composition root assembles it from state it
     * already has; the model reaches into nothing.
     *
     * @param viewer       the predicted local player, or {@code null} before a spawn
     * @param others       every other player in the snapshot, <b>dead ones included</b> — a death
     *                     retires its marker immediately, and the model can only do that if it is
     *                     told the player is dead rather than shown a list they vanished from
     * @param drones       every drone in the world, with the piloted one at its predicted position
     * @param cameras      every throw camera in the world, in flight or stuck
     * @param eyes         the viewer's observer set — the very one the visibility pass draws
     * @param smoke        live smoke volumes, exactly as gameplay sight queries see them
     * @param viewport     the world rectangle the camera is showing, or {@code null} to omit it
     * @param alliesAlways show teammates whether or not they are lit (mechanics §4's "teammates
     *                     visible on minimap" toggle, which defaults to on)
     */
    public record Input(
        Player viewer,
        List<Player> others,
        List<DroneEntity> drones,
        List<CameraEntity> cameras,
        ObserverSet eyes,
        List<SmokeVolume> smoke,
        Rect viewport,
        boolean alliesAlways
    ) {

        public Input {
            others = orEmpty(others);
            drones = orEmpty(drones);
            cameras = orEmpty(cameras);
            smoke = orEmpty(smoke);
            eyes = eyes == null ? ObserverSet.empty() : eyes;
        }

        /**
         * A null list and a null entry both read as "nothing here". A snapshot with a hole in it
         * is a bug worth reporting, but not one worth dropping a frame over: the HUD is the last
         * thing standing between the player and an unplayable screen.
         */
        private static <T> List<T> orEmpty(List<T> source) {
            if (source == null || source.isEmpty()) {
                return List.of();
            }
            return source.stream().filter(Objects::nonNull).toList();
        }
    }

    /**
     * One frame of minimap.
     *
     * @param terrain      the arena's static geometry, computed once by the model and handed to
     *                     every frame as the same immutable list
     * @param blips        the markers to draw, the viewer's own last so it draws on top
     * @param viewport     the camera's view rectangle, clamped into the arena, or {@code null}
     * @param arenaAspect  arena width over height, so the widget can fit without knowing the map
     * @param liveCount    markers that are lit right now, the viewer's own included
     * @param ghostCount   markers that are remembered rather than seen
     */
    public record View(List<Box> terrain, List<Blip> blips, Box viewport, float arenaAspect,
                       int liveCount, int ghostCount) {

        public View {
            terrain = terrain == null ? List.of() : List.copyOf(terrain);
            blips = blips == null ? List.of() : List.copyOf(blips);
        }

        /** A frame with nothing on it: the arena, no markers, no view rectangle. */
        public static View empty(List<Box> terrain, float arenaAspect) {
            return new View(terrain, List.of(), null, arenaAspect, 0, 0);
        }

        /**
         * The largest rectangle of this map's aspect that fits inside {@code boxWidth × boxHeight},
         * centred in it. The widget draws inside the result and never stretches the arena: a
         * squashed map would put a marker further from the centre than the world does.
         */
        public Box fit(float boxX, float boxY, float boxWidth, float boxHeight) {
            if (boxWidth <= 0f || boxHeight <= 0f || arenaAspect <= 0f) {
                return new Box(boxX, boxY, 0f, 0f);
            }
            float width = boxWidth;
            float height = width / arenaAspect;
            if (height > boxHeight) {
                height = boxHeight;
                width = height * arenaAspect;
            }
            return new Box(
                boxX + (boxWidth - width) * 0.5f,
                boxY + (boxHeight - height) * 0.5f,
                width,
                height);
        }
    }

    /** What a memory is keyed by. Kind and id together: device ids are per-system, so a drone and
     * a camera can share one. */
    private record Target(Kind kind, int id) {
    }

    /** One remembered sighting: where it was, when it was seen, and whose side it was on. */
    private record Memory(long seenAtMillis, float x, float y, Allegiance allegiance) {
    }

    private final ArenaMap arena;
    private final float arenaWidth;
    private final float arenaHeight;
    private final List<Box> terrain;
    private final Map<Target, Memory> memory = new LinkedHashMap<>();

    /** Tracks the viewer's alive flag so a respawn empties the map exactly once. */
    private boolean viewerWasAlive = true;

    public MinimapModel(ArenaMap arena) {
        this.arena = Objects.requireNonNull(arena, "the minimap is a picture of one arena");
        // Never zero: a degenerate arena would divide every marker into infinity.
        this.arenaWidth = Math.max(1f, arena.width());
        this.arenaHeight = Math.max(1f, arena.height());
        List<Box> boxes = new ArrayList<>(arena.solids().size());
        for (Rect solid : arena.solids()) {
            boxes.add(new Box(
                solid.left() / arenaWidth,
                solid.bottom() / arenaHeight,
                solid.width() / arenaWidth,
                solid.height() / arenaHeight));
        }
        this.terrain = List.copyOf(boxes);
    }

    /** The arena's static geometry, in normalised map space. The same list every call. */
    public List<Box> terrainBoxes() {
        return terrain;
    }

    /** Arena width over height: {@code 1.5} for the shipped 3000 × 2000 arena. */
    public float arenaAspect() {
        return arenaWidth / arenaHeight;
    }

    /** Sightings remembered right now, live or fading. Diagnostics and tests. */
    public int trackedCount() {
        return memory.size();
    }

    /**
     * Forgets every sighting. A respawn does this itself; this is for the transitions a respawn
     * does not cover — leaving a match, or handing the model to a different server.
     */
    public void clear() {
        memory.clear();
    }

    /**
     * 1 while a lost sighting is held, falling to 0 across the fade, 0 once it has expired. The
     * same envelope shape the kill feed uses, so the HUD ages things one way.
     */
    public static float ghostAlpha(float ageSeconds) {
        if (ageSeconds <= GHOST_HOLD_SECONDS) {
            return 1f;
        }
        if (ageSeconds >= GHOST_LIFETIME_SECONDS) {
            return 0f;
        }
        return 1f - (ageSeconds - GHOST_HOLD_SECONDS) / GHOST_FADE_SECONDS;
    }

    /**
     * Builds one frame. Ages by wall clock, like the kill feed, so a frame spike cannot empty the
     * map and a paused client cannot hoard it.
     *
     * @param nowMillis the wall clock the ghost envelope is measured against
     */
    public View view(Input input, long nowMillis) {
        if (input == null) {
            return View.empty(terrain, arenaAspect());
        }
        Player viewer = input.viewer();
        if (viewer == null) {
            // No spawn yet: no eyes and no markers, but the arena and the camera's view rectangle
            // are still worth drawing, so the frame is not a blank box while joining.
            return new View(terrain, List.of(), viewportBox(input.viewport()), arenaAspect(), 0, 0);
        }
        if (viewer.alive && !viewerWasAlive) {
            // A new life starts with an empty map: the last life's sightings are about a body that
            // no longer exists, seen from a position the player is no longer at.
            memory.clear();
        }
        viewerWasAlive = viewer.alive;

        // Retire the memories of targets that no longer exist before anything else reads them.
        forgetAbsentees(input);

        List<Blip> blips = new ArrayList<>();
        Set<Target> seen = new HashSet<>();
        int ghosts = 0;

        for (Player other : input.others()) {
            if (!other.alive) {
                continue;
            }
            Target key = new Target(Kind.PLAYER, other.id);
            Allegiance allegiance = Team.areAllies(viewer.teamIndex, other.teamIndex)
                ? Allegiance.ALLY
                : Allegiance.ENEMY;
            if (!marked(allegiance, other.hitbox(), input)) {
                continue;
            }
            float x = normaliseX(other.centerX());
            float y = normaliseY(other.centerY());
            memory.put(key, new Memory(nowMillis, x, y, allegiance));
            seen.add(key);
            blips.add(new Blip(x, y, Kind.PLAYER, allegiance, 1f, false));
        }

        for (DroneEntity drone : input.drones()) {
            addDevice(
                blips, seen, new Target(Kind.DRONE, drone.id), Kind.DRONE,
                allegianceOf(viewer, drone.ownerId, drone.teamIndex),
                drone.hitbox(), drone.x, drone.y, input, nowMillis);
        }

        for (CameraEntity camera : input.cameras()) {
            addDevice(
                blips, seen, new Target(Kind.CAMERA, camera.id), Kind.CAMERA,
                allegianceOf(viewer, camera.ownerId, camera.teamIndex),
                camera.hitbox(), camera.x, camera.y, input, nowMillis);
        }

        for (Iterator<Map.Entry<Target, Memory>> it = memory.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Target, Memory> entry = it.next();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            Memory last = entry.getValue();
            float age = (nowMillis - last.seenAtMillis()) / 1000f;
            if (age >= GHOST_LIFETIME_SECONDS) {
                it.remove();
                continue;
            }
            // The frozen position is the point: a ghost that followed the target's true position
            // would be the unculled snapshot leaking through the HUD after all.
            blips.add(new Blip(
                last.x(), last.y(), entry.getKey().kind(), last.allegiance(), ghostAlpha(age), true));
            ghosts++;
        }

        evictOverflow();

        // The viewer's own body last, so its marker draws on top of anything it overlaps — and
        // whether alive or not: you never lose track of where your own corpse is.
        blips.add(new Blip(
            normaliseX(viewer.centerX()),
            normaliseY(viewer.centerY()),
            Kind.PLAYER,
            Allegiance.SELF,
            1f,
            false));

        return new View(
            terrain, blips, viewportBox(input.viewport()), arenaAspect(), blips.size() - ghosts, ghosts);
    }

    /**
     * One device marker, gated exactly like a body: the viewer's own devices are always on the map
     * — they are the player's own property, and the gadget slots already report their state — and
     * anyone else's has to be lit, unless the teammate exemption covers it.
     */
    private void addDevice(
            List<Blip> blips,
            Set<Target> seen,
            Target key,
            Kind kind,
            Allegiance allegiance,
            Rect hitbox,
            float worldX,
            float worldY,
            Input input,
            long nowMillis) {
        if (!marked(allegiance, hitbox, input)) {
            return;
        }
        float x = normaliseX(worldX);
        float y = normaliseY(worldY);
        memory.put(key, new Memory(nowMillis, x, y, allegiance));
        seen.add(key);
        blips.add(new Blip(x, y, kind, allegiance, 1f, false));
    }

    /**
     * Whether a target of this allegiance earns a marker: the viewer's own always does, a
     * teammate's does while mechanics §4's "teammates visible on minimap" toggle is on, and
     * anything else only when the observer set has it lit. One spelling, used for bodies and
     * devices alike, so the two can never disagree about who gets the exemption.
     */
    private boolean marked(Allegiance allegiance, Rect hitbox, Input input) {
        return allegiance == Allegiance.SELF
            || (allegiance == Allegiance.ALLY && input.alliesAlways())
            || input.eyes().isLit(hitbox, arena, input.smoke());
    }

    /**
     * Drops the memories of targets that are gone rather than hidden. The snapshot is not culled,
     * so absence is authoritative: a player who died or left, a drone that was shot down, a camera
     * that was destroyed. Keeping their markers would report a world that no longer exists.
     */
    private void forgetAbsentees(Input input) {
        Set<Target> present = new HashSet<>();
        for (Player other : input.others()) {
            if (other.alive) {
                present.add(new Target(Kind.PLAYER, other.id));
            }
        }
        for (DroneEntity drone : input.drones()) {
            present.add(new Target(Kind.DRONE, drone.id));
        }
        for (CameraEntity camera : input.cameras()) {
            present.add(new Target(Kind.CAMERA, camera.id));
        }
        memory.keySet().retainAll(present);
    }

    /** Keeps memory inside {@link #MAX_TRACKED} by forgetting the least recently seen sighting. */
    private void evictOverflow() {
        while (memory.size() > MAX_TRACKED) {
            Target oldest = null;
            long oldestSeen = Long.MAX_VALUE;
            for (Map.Entry<Target, Memory> entry : memory.entrySet()) {
                if (entry.getValue().seenAtMillis() < oldestSeen) {
                    oldestSeen = entry.getValue().seenAtMillis();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            memory.remove(oldest);
        }
    }

    private Allegiance allegianceOf(Player viewer, int ownerId, int teamIndex) {
        if (ownerId == viewer.id) {
            return Allegiance.SELF;
        }
        return Team.areAllies(viewer.teamIndex, teamIndex) ? Allegiance.ALLY : Allegiance.ENEMY;
    }

    private Box viewportBox(Rect viewport) {
        if (viewport == null) {
            return null;
        }
        // Clamped rather than cropped: the camera can look past the arena edge while detached, and
        // a marker for it must stay inside the frame the widget draws.
        float x0 = clamp01(viewport.left() / arenaWidth);
        float x1 = clamp01(viewport.right() / arenaWidth);
        float y0 = clamp01(viewport.bottom() / arenaHeight);
        float y1 = clamp01(viewport.top() / arenaHeight);
        return new Box(x0, y0, Math.max(0f, x1 - x0), Math.max(0f, y1 - y0));
    }

    private float normaliseX(float worldX) {
        return clamp01(worldX / arenaWidth);
    }

    private float normaliseY(float worldY) {
        return clamp01(worldY / arenaHeight);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
