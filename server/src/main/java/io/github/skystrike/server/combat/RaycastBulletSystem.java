package io.github.skystrike.server.combat;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Geometry;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import java.util.Collection;

/**
 * The swept path of one round, resolved against terrain and player hitboxes.
 *
 * <p>This exists because of one number: at 60 Hz the fastest sniper round covers 32.5 units a tick and the tunnel
 * roofs are 14 units thick. A point test at the new position walks straight through them, and
 * the resulting bug — rounds that occasionally pass through a specific piece of geometry — is
 * close to impossible to find from a bug report. So the segment from the previous position to
 * the new one is tested instead, using the one shared {@code Geometry} implementation that line
 * of sight and explosion occlusion also use.
 *
 * <p>Nearest entry wins across both sets, so a player standing behind a wall is protected by the
 * wall and a player standing in front of it is not.
 */
public final class RaycastBulletSystem {

    /** What a round ran into. */
    public enum HitType {
        TERRAIN,
        PLAYER
    }

    /**
     * One resolved impact.
     *
     * @param type     terrain or player
     * @param fraction how far along the swept segment the impact happened, in {@code [0, 1]}
     * @param x        impact position
     * @param y        impact position
     * @param player   the player hit, or {@code null} for terrain
     * @param terrain  the solid hit, or {@code null} for a player
     */
    public record Hit(HitType type, float fraction, float x, float y, Player player, Rect terrain) {
    }

    /**
     * One resolved impact against a gadget device (a drone or a throw camera).
     *
     * @param fraction how far along the swept segment the impact happened, in {@code [0, 1]}
     * @param x        impact position
     * @param y        impact position
     * @param drone    the drone hit, or {@code null} when a camera was hit
     * @param camera   the camera hit, or {@code null} when a drone was hit
     */
    public record GadgetHit(float fraction, float x, float y, DroneEntity drone, CameraEntity camera) {
    }

    private final ArenaMap arena;

    public RaycastBulletSystem(ArenaMap arena) {
        this.arena = arena;
    }

    public ArenaMap arena() {
        return arena;
    }

    /**
     * Resolves the segment {@code (x0,y0) → (x1,y1)} against the world.
     *
     * <p>A zero-length segment degenerates into a point test, which is what a projectile slower
     * than {@link io.github.skystrike.shared.config.CombatConfig#SWEEP_SPEED_THRESHOLD} gets.
     *
     * @param targets      candidate players; dead ones are skipped
     * @param ownerId      the player who fired, excluded unless {@code canHitOwner}
     * @param canHitOwner  true once the round has cleared its own muzzle
     * @return the nearest impact, or {@code null} when the path is clear
     */
    public Hit resolve(
            float x0,
            float y0,
            float x1,
            float y1,
            Collection<Player> targets,
            int ownerId,
            boolean canHitOwner) {

        float bestTerrain = Float.MAX_VALUE;
        Rect hitRect = null;
        if (arena != null) {
            for (Rect solid : arena.solids()) {
                float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, solid);
                if (entry >= 0f && entry < bestTerrain) {
                    bestTerrain = entry;
                    hitRect = solid;
                }
            }
        }

        float bestPlayer = Float.MAX_VALUE;
        Player hitPlayer = null;
        if (targets != null) {
            for (Player target : targets) {
                if (target == null || !target.alive) {
                    continue;
                }
                if (target.id == ownerId && !canHitOwner) {
                    continue;
                }
                float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, target.hitbox());
                if (entry >= 0f && entry < bestPlayer) {
                    bestPlayer = entry;
                    hitPlayer = target;
                }
            }
        }

        if (hitPlayer != null && bestPlayer <= bestTerrain) {
            return new Hit(HitType.PLAYER, bestPlayer, lerp(x0, x1, bestPlayer),
                lerp(y0, y1, bestPlayer), hitPlayer, null);
        }
        if (hitRect != null) {
            return new Hit(HitType.TERRAIN, bestTerrain, lerp(x0, x1, bestTerrain),
                lerp(y0, y1, bestTerrain), null, hitRect);
        }
        return null;
    }

    /**
     * Resolves the segment {@code (x0,y0) → (x1,y1)} against gadget devices only: every live
     * drone and camera, nearest entry winning.
     *
     * <p>Devices are world objects, not players: anyone may shoot anyone's drone or camera —
     * denying a team its eyes is legitimate play — and the owner's own rounds are held to the
     * same self-hit grace as they are against players.
     *
     * @param drones      live drones; destroyed ones are skipped
     * @param cameras     live cameras; destroyed ones are skipped
     * @param ownerId     the player who fired, excluded unless {@code canHitOwner}
     * @param canHitOwner true once the round has cleared its own muzzle
     * @return the nearest device impact, or {@code null} when the path is clear of devices
     */
    public GadgetHit resolveGadgets(
            float x0,
            float y0,
            float x1,
            float y1,
            Collection<DroneEntity> drones,
            Collection<CameraEntity> cameras,
            int ownerId,
            boolean canHitOwner) {
        float best = Float.MAX_VALUE;
        DroneEntity hitDrone = null;
        CameraEntity hitCamera = null;
        if (drones != null) {
            for (DroneEntity drone : drones) {
                if (drone == null || drone.isDestroyed()) {
                    continue;
                }
                if (drone.ownerId == ownerId && !canHitOwner) {
                    continue;
                }
                float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, drone.hitbox());
                if (entry >= 0f && entry < best) {
                    best = entry;
                    hitDrone = drone;
                    hitCamera = null;
                }
            }
        }
        if (cameras != null) {
            for (CameraEntity camera : cameras) {
                if (camera == null || camera.isDestroyed()) {
                    continue;
                }
                if (camera.ownerId == ownerId && !canHitOwner) {
                    continue;
                }
                float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, camera.hitbox());
                if (entry >= 0f && entry < best) {
                    best = entry;
                    hitCamera = camera;
                    hitDrone = null;
                }
            }
        }
        if (hitDrone == null && hitCamera == null) {
            return null;
        }
        return new GadgetHit(best, lerp(x0, x1, best), lerp(y0, y1, best), hitDrone, hitCamera);
    }

    /** True when terrain blocks the straight line between two points. */
    public boolean blockedByTerrain(float x0, float y0, float x1, float y1) {
        if (arena == null) {
            return false;
        }
        for (Rect solid : arena.solids()) {
            if (Geometry.segmentIntersectsAabb(x0, y0, x1, y1, solid)) {
                return true;
            }
        }
        return false;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
