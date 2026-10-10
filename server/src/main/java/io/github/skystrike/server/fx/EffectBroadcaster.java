package io.github.skystrike.server.fx;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.VisionConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.SmokeVolume;
import io.github.skystrike.shared.vision.VisionMath;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates the tick's effect requests and culls them per recipient (build plan M7 §8.1).
 *
 * <p>Systems emit into this sink during the tick; when the snapshot broadcast runs, the server
 * drains the window once and sends each recipient only the effects that recipient can plausibly
 * see. Culling samples the same {@link VisionMath} the entity and combat code uses, on a disc
 * around the effect whose radius is the type's {@link io.github.skystrike.shared.effect.EffectType#cullRadius()
 * cull radius} — the reach of what is visible (the attached light), not the particle spread — so
 * an explosion just behind a wall still reaches observers who can see the wall face its light
 * lands on, while a fully hidden detonation sends nothing at all.
 *
 * <p>The visibility bar is the same one M6's player lights use: the peripheral floor is not
 * enough. An effect outside both the observer's cone and their vision bubble (M14: the close,
 * all-round sight around the body) is culled, which is what keeps additive particles from glowing
 * at observers who cannot see the source. Gameplay state is never culled this way — only these
 * ephemeral visuals.
 *
 * <p>Threading: emit and drain both run on the tick thread. The pending list is deliberately not
 * synchronised, like the rest of the simulation.
 */
public final class EffectBroadcaster implements EffectSink {

    /**
     * Upper bound on requests buffered between two snapshot broadcasts, so an effect spam loop
     * cannot exhaust the heap. Effects are presentation; dropping the excess is correct.
     */
    public static final int MAX_PENDING_SPAWNS = 512;

    /** Sample points on the culling disc sit at this fraction of its radius. */
    private static final float DISC_SAMPLE_FRACTION = 0.75f;

    private static final int DISC_SAMPLE_COUNT = 8;

    /**
     * Who a culling decision is made for: the recipient's player, plus the gadget devices they
     * own. A live drone or stuck camera is an extra observer in its own right — an effect seen
     * only through the player's own drone must still be sent, because the recipient's screen is
     * showing that drone's cone.
     */
    public record Recipient(Player player, List<DroneEntity> drones, List<CameraEntity> cameras) {

        public Recipient {
            drones = drones == null ? List.of() : List.copyOf(drones);
            cameras = cameras == null ? List.of() : List.copyOf(cameras);
        }

        /** A recipient with no devices: the player observer alone. */
        public static Recipient of(Player player) {
            return new Recipient(player, List.of(), List.of());
        }
    }

    private final List<EffectSpawn> pending = new ArrayList<>();
    private int nextSeed = 1;

    /**
     * Records one effect request. Assigns the deterministic layout seed here, in one place, so
     * every client that receives the event lays out the same particles.
     */
    @Override
    public void emit(EffectSpawn spawn) {
        if (spawn == null || pending.size() >= MAX_PENDING_SPAWNS) {
            return;
        }
        spawn.seed = nextSeed++;
        pending.add(spawn);
    }

    /**
     * Hands the accumulated window over and starts a new one. Returns an immutable empty list
     * when nothing was emitted, so callers can skip the per-recipient work entirely.
     */
    public List<EffectSpawn> drain() {
        if (pending.isEmpty()) {
            return List.of();
        }
        List<EffectSpawn> drained = List.copyOf(pending);
        pending.clear();
        return drained;
    }

    /**
     * Copies successful muzzle reports into the state packet's sound-cue list. These cues are not
     * vision-culled: the client applies the weapon's audible radius and terrain attenuation, while
     * the visual muzzle flash remains governed by {@link #cullFor}.
     */
    public List<EffectSpawn> gunfireFor(List<EffectSpawn> spawns) {
        if (spawns == null || spawns.isEmpty()) {
            return List.of();
        }
        List<EffectSpawn> gunfire = new ArrayList<>();
        for (EffectSpawn spawn : spawns) {
            if (spawn != null
                && spawn.type == EffectType.MUZZLE_FLASH
                && spawn.sourcePlayerId >= 0
                && WeaponId.isValidOrdinal(spawn.weaponId)) {
                gunfire.add(spawn.copy());
            }
        }
        return List.copyOf(gunfire);
    }

    /** Drops everything buffered; used on shutdown so a reused broadcaster starts clean. */
    public void clear() {
        pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }

    /**
     * The subset of {@code spawns} the given recipient can plausibly see: any sample point on the
     * effect's culling disc must clear the same visibility bar M6's player lights use (inside the
     * cone or the all-round vision bubble, hard line of sight, smoke counts) — judged from the
     * recipient's own eyes <b>or from any device they own</b>. M14 added the bubbles: an effect
     * just behind the recipient is on their screen now, so it is sent. A recipient piloting a drone is watching that drone's cone, so an
     * effect the drone can see is an effect they can see, even with their back to it.
     *
     * @param recipient the recipient's authoritative player state plus their owned devices
     * @param smoke     live smoke volumes, exactly as gameplay sight queries see them
     */
    public List<EffectSpawn> cullFor(
            List<EffectSpawn> spawns,
            Recipient recipient,
            ArenaMap arena,
            List<SmokeVolume> smoke) {
        if (spawns == null || spawns.isEmpty() || recipient == null
            || recipient.player() == null || arena == null) {
            return List.of();
        }
        Player observer = recipient.player();
        float reach = observer.ads ? VisionConfig.REACH_ADS : VisionConfig.REACH_HIP;
        List<EffectSpawn> visible = new ArrayList<>(spawns.size());
        for (EffectSpawn spawn : spawns) {
            if (spawn == null || spawn.type == null) {
                continue;
            }
            float radius = spawn.type.cullRadius() * Math.max(0.25f, spawn.scale);
            if (isVisibleToRecipient(observer, recipient, spawn.x, spawn.y, radius, arena, smoke, reach)) {
                visible.add(spawn);
            }
        }
        return visible;
    }

    /**
     * The recipient sees the effect if their own eyes do, or if any device they own does. The
     * device check is the raw-position {@code VisionMath.canObserverSeeTarget} — a drone or
     * camera is not a {@code Player} — with the effect's culling disc as the target box.
     */
    private static boolean isVisibleToRecipient(
            Player observer,
            Recipient recipient,
            float x,
            float y,
            float radius,
            ArenaMap arena,
            List<SmokeVolume> smoke,
            float reach) {
        if (isAnySampleVisible(observer, x, y, radius, arena, smoke, reach)) {
            return true;
        }
        Rect disc = new Rect(x - radius, y - radius, radius * 2f, radius * 2f);
        for (DroneEntity drone : recipient.drones()) {
            if (drone == null) {
                continue;
            }
            if (VisionMath.canObserverSeeTarget(
                    drone.x, drone.y, drone.aimAngle,
                    GadgetConfig.DRONE_VISION_RANGE, disc, arena, smoke)) {
                return true;
            }
            // M14: the drone's all-round bubble, on the screen-facing bar its screen shows.
            if (VisionMath.isTargetLit(
                    drone.x, drone.y, 0f, GadgetConfig.DEVICE_BUBBLE_RADIUS,
                    VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES, disc, arena, smoke)) {
                return true;
            }
        }
        for (CameraEntity camera : recipient.cameras()) {
            if (camera == null || !camera.stuck) {
                continue;
            }
            if (VisionMath.canObserverSeeTarget(
                    camera.x, camera.y, camera.aimAngle,
                    GadgetConfig.CAMERA_VISION_RANGE, disc, arena, smoke)) {
                return true;
            }
            if (VisionMath.isTargetLit(
                    camera.x, camera.y, 0f, GadgetConfig.DEVICE_BUBBLE_RADIUS,
                    VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES, disc, arena, smoke)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnySampleVisible(
            Player observer,
            float x,
            float y,
            float radius,
            ArenaMap arena,
            List<SmokeVolume> smoke,
            float reach) {
        if (isVisibleFrom(observer, x, y, arena, smoke, reach)) {
            return true;
        }
        for (int i = 0; i < DISC_SAMPLE_COUNT; i++) {
            double angle = i * (Math.PI * 2.0 / DISC_SAMPLE_COUNT);
            float px = x + (float) (Math.cos(angle) * radius * DISC_SAMPLE_FRACTION);
            float py = y + (float) (Math.sin(angle) * radius * DISC_SAMPLE_FRACTION);
            if (isVisibleFrom(observer, px, py, arena, smoke, reach)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVisibleFrom(
            Player observer,
            float x,
            float y,
            ArenaMap arena,
            List<SmokeVolume> smoke,
            float reach) {
        float visibility = VisionMath.calculateVisibility(
            observer.eyeX(),
            observer.eyeY(),
            observer.aimAngle,
            reach,
            x,
            y,
            arena,
            smoke);
        // The shared vision function retains a faint peripheral floor outside the cone. An effect
        // must clear that floor, exactly like M6's player-light sources, so additive particles
        // never identify a detonation the observer cannot see. The bar is the shared constant, so
        // the minimap's blip gate and this culler cannot drift apart about what "visible" means.
        if (visibility > VisionConfig.LIT_VISIBILITY_THRESHOLD) {
            return true;
        }
        // M14: the body's vision bubble is all-round, so an effect close behind the observer is
        // on screen too. It is judged on the same bar, with the bubble's full-circle cone.
        float bubble = VisionMath.calculateVisibility(
            observer.eyeX(),
            observer.eyeY(),
            0f,
            VisionConfig.BODY_BUBBLE_RADIUS,
            VisionConfig.FULL_CIRCLE_HALF_ANGLE_DEGREES,
            x,
            y,
            arena,
            smoke);
        return bubble > VisionConfig.LIT_VISIBILITY_THRESHOLD;
    }
}
