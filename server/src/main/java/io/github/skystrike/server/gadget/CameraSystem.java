package io.github.skystrike.server.gadget;

import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.gadget.CameraFlight;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetPress;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative lifecycle of every thrown observation camera (mechanics §7.2).
 *
 * <p>A camera exists from the moment it is thrown: while flying it is stepped by
 * {@link CameraFlight} — the one shared throwable integrator, so its arc is a grenade's arc —
 * and the first surface it touches parks it permanently as a fixed observation post. A stuck
 * camera cannot move; it trades the drone's mobility for persistence, and while its owner views
 * through it the cone follows their aim (the camera swivels, the post does not).
 *
 * <p>Press transitions are the shared {@link GadgetPress} machine; the only map-dependent rule
 * lives here as a veto — a throw whose release point is inside terrain or outside the arena is
 * refused rather than spawning a camera inside a wall. Damage lands on the entity's health from
 * the bullet system; {@link #sweep(Collection)} destroys a spent camera, a camera that left the
 * arena, or one whose owner died or left, and returns a viewer to their own eyes.
 */
public final class CameraSystem {

    private final ArenaMap arena;
    private final List<CameraEntity> cameras = new ArrayList<>();

    /**
     * Where destruction and stick visuals go (build plan M7 §8.1). Null in geometry-only tests;
     * a missing sink never changes gameplay, only presentation.
     */
    private EffectSink effectSink;

    /**
     * Scratch flight state, reused across cameras within a tick. Stepping is sequential on the
     * tick thread, so one scratch serves every flying camera without allocating per tick.
     */
    private final ThrownUtility flightScratch = new ThrownUtility();

    private int nextId = 1;

    public CameraSystem(ArenaMap arena) {
        if (arena == null) {
            throw new IllegalArgumentException("arena is required");
        }
        this.arena = arena;
    }

    /** Installs the effect sink. Null detaches; safe to call more than once. */
    public void setEffectSink(EffectSink effectSink) {
        this.effectSink = effectSink;
    }

    /**
     * One Q/E press on the player's camera slot, dispatched from the loadout tick. Throws the
     * camera, starts viewing through it once it has stuck, or returns the view to the body.
     */
    public void press(Player player) {
        if (player == null || player.loadout == null || !player.alive || player.isSlowed()) {
            return;
        }
        GadgetSlot slot = player.loadout.cameraSlot();
        if (slot == null) {
            return;
        }
        CameraEntity live = byOwner(player.id);
        GadgetPress.Outcome outcome = GadgetPress.resolve(
            GadgetId.CAMERA,
            player.surveillance(),
            slot.isUsable(),
            live != null,
            live != null && live.stuck);
        // The shared machine cannot see the map: a throw with no clear release point is refused
        // here, before the slot is marked deployed.
        if (outcome == GadgetPress.Outcome.CAMERA_THROWN && !throwPointClear(player)) {
            outcome = GadgetPress.Outcome.IGNORED;
        }
        GadgetPress.apply(player, slot, outcome);
        if (outcome == GadgetPress.Outcome.CAMERA_THROWN) {
            throwCamera(player);
        }
    }

    /**
     * Steps the camera owned by {@code player}, if any. A flying camera follows its arc; a stuck
     * camera stays put but its cone follows the owner's aim while they view through it.
     */
    public void stepOwned(Player player, PlayerInput input, float dt) {
        if (player == null || !player.alive || dt <= 0f) {
            return;
        }
        CameraEntity camera = byOwner(player.id);
        if (camera == null) {
            return;
        }
        if (!camera.stuck) {
            stepFlight(camera, dt);
        }
        if (camera.stuck && player.surveillance() == SurveillanceView.CAMERA && input != null) {
            camera.aimAngle = Angles.wrap(input.aimAngle);
        }
        mirrorDurability(player, camera);
    }

    /**
     * Removes cameras whose owner died or left, and destroys the spent ones and the ones that
     * left the arena. Runs once per tick after combat, on the tick thread.
     */
    public void sweep(Collection<Player> players) {
        for (int i = cameras.size() - 1; i >= 0; i--) {
            CameraEntity camera = cameras.get(i);
            Player owner = findById(players, camera.ownerId);
            if (owner == null || !owner.alive) {
                cameras.remove(i);
                if (owner != null) {
                    releaseOwner(owner);
                }
                continue;
            }
            if (camera.isDestroyed()) {
                destroy(camera, owner);
                cameras.remove(i);
            }
        }
    }

    /** Live cameras — flying or stuck — in spawn order. Mutations stay inside this system. */
    public List<CameraEntity> active() {
        return Collections.unmodifiableList(cameras);
    }

    /** The camera owned by {@code playerId}, or {@code null} when none is in the world. */
    public CameraEntity byOwner(int playerId) {
        for (CameraEntity camera : cameras) {
            if (camera.ownerId == playerId) {
                return camera;
            }
        }
        return null;
    }

    /** The camera owned by {@code playerId} once it has stuck, or {@code null} while it flies. */
    public CameraEntity byOwnerStuck(int playerId) {
        CameraEntity camera = byOwner(playerId);
        return camera != null && camera.stuck ? camera : null;
    }

    /** Every camera owned by {@code playerId} — at most one, but the list keeps callers honest. */
    public List<CameraEntity> ownedBy(int playerId) {
        List<CameraEntity> owned = new ArrayList<>();
        for (CameraEntity camera : cameras) {
            if (camera.ownerId == playerId) {
                owned.add(camera);
            }
        }
        return owned;
    }

    public int count() {
        return cameras.size();
    }

    public ArenaMap arena() {
        return arena;
    }

    public void clear() {
        cameras.clear();
    }

    /** Launches a camera from the owner's eye along their aim, on a throwable's arc. */
    private void throwCamera(Player owner) {
        float x = CameraFlight.launchX(owner.eyeX(), owner.aimAngle);
        float y = CameraFlight.launchY(owner.eyeY(), owner.aimAngle);
        CameraEntity camera = new CameraEntity(
            nextId++,
            owner.id,
            owner.teamIndex,
            x,
            y,
            CameraFlight.launchVelocityX(owner.aimAngle),
            CameraFlight.launchVelocityY(owner.aimAngle),
            owner.aimAngle,
            GadgetConfig.CAMERA_HEALTH);
        cameras.add(camera);
    }

    /**
     * Steps one flying camera through the shared integrator and applies the stick decision. The
     * contact position the integrator leaves is already flush with the surface, so sticking is
     * a copy, not a bounce.
     */
    private void stepFlight(CameraEntity camera, float dt) {
        flightScratch.x = camera.x;
        flightScratch.y = camera.y;
        flightScratch.prevX = camera.x;
        flightScratch.prevY = camera.y;
        flightScratch.vx = camera.vx;
        flightScratch.vy = camera.vy;
        flightScratch.aimAngle = camera.aimAngle;
        flightScratch.age = 0f;
        flightScratch.fuseRemaining = 0f;
        flightScratch.resting = false;
        flightScratch.bounces = 0;
        flightScratch.contactNormalX = 0f;
        flightScratch.contactNormalY = 0f;

        CameraFlight.Outcome outcome = CameraFlight.step(flightScratch, dt, arena);
        camera.x = flightScratch.x;
        camera.y = flightScratch.y;
        camera.prevX = flightScratch.prevX;
        camera.prevY = flightScratch.prevY;
        camera.vx = flightScratch.vx;
        camera.vy = flightScratch.vy;
        switch (outcome) {
            case STUCK -> {
                camera.stuck = true;
                camera.contactNormalX = flightScratch.contactNormalX;
                camera.contactNormalY = flightScratch.contactNormalY;
                camera.vx = 0f;
                camera.vy = 0f;
                // A small spark where the lens meets the surface: the stick is worth hearing.
                emit(EffectType.BULLET_IMPACT_METAL, camera.x, camera.y, 0f, 1f);
            }
            case OFF_ARENA -> {
                // §7.2: a camera that leaves the arena is destroyed. Marking it spent lets the
                // sweep destroy it through the one destruction path, effects included.
                camera.health = 0f;
            }
            case FLYING -> {
                // the flight continues next tick
            }
        }
    }

    /** A downed (or lost) camera is gone for the rest of the life and a viewer loses the view. */
    private void destroy(CameraEntity camera, Player owner) {
        if (owner.loadout != null) {
            GadgetSlot slot = owner.loadout.cameraSlot();
            if (slot != null) {
                slot.broken = true;
                slot.active = false;
                slot.durability = 0f;
            }
        }
        releaseOwner(owner);
        emit(EffectType.IMPACT_EXPLOSION, camera.x, camera.y, 0f, 0.35f);
    }

    /** The owner's death or disconnect takes the camera with them; the slot follows at respawn. */
    private void releaseOwner(Player owner) {
        if (owner.surveillance() == SurveillanceView.CAMERA) {
            owner.surveillanceView = SurveillanceView.SELF.ordinal();
        }
        if (owner.loadout != null) {
            GadgetSlot slot = owner.loadout.cameraSlot();
            if (slot != null) {
                slot.active = false;
            }
        }
    }

    /** The gadget slot's durability mirrors the entity's health so one HUD bar reads both. */
    private void mirrorDurability(Player owner, CameraEntity camera) {
        if (owner.loadout == null) {
            return;
        }
        GadgetSlot slot = owner.loadout.cameraSlot();
        if (slot != null) {
            slot.durability = camera.health;
        }
    }

    /**
     * A throw needs a clear release point: inside the arena, outside terrain, and with the
     * eye-to-release segment unobstructed — the same rule a grenade throw answers to.
     */
    private boolean throwPointClear(Player owner) {
        float x = CameraFlight.launchX(owner.eyeX(), owner.aimAngle);
        float y = CameraFlight.launchY(owner.eyeY(), owner.aimAngle);
        float radius = GadgetConfig.CAMERA_RADIUS;
        if (x < radius || x > arena.width() - radius || y < radius || y > arena.height() - radius) {
            return false;
        }
        Rect release = new Rect(x - radius, y - radius, radius * 2f, radius * 2f);
        if (MapQueries.overlapsSolid(arena, release)) {
            return false;
        }
        return VisionMath.hasLineOfSight(owner.eyeX(), owner.eyeY(), x, y, arena);
    }

    private void emit(EffectType type, float x, float y, float angle, float scale) {
        if (effectSink != null) {
            effectSink.emit(new EffectSpawn(type, x, y, angle, scale));
        }
    }

    private static Player findById(Collection<Player> players, int id) {
        if (players == null) {
            return null;
        }
        for (Player player : players) {
            if (player != null && player.id == id) {
                return player;
            }
        }
        return null;
    }
}
