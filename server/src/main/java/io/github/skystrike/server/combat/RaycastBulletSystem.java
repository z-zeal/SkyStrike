package io.github.skystrike.server.combat;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockRules;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.MaterialTable;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Geometry;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
 *
 * <p>Terrain is read as the shared {@link Blocking#BULLETS} column of {@code BlockRules} through
 * {@link ArenaMap#blockers(Blocking)}, never as a list of kinds: intact glass and one-way
 * platforms are simply absent from that view, which is what lets a round pass through a window
 * without this class knowing what a window is.
 *
 * <p>With a {@link MapDamageState} wired, every sweep here reads the <b>live</b> map, so a block
 * that left the map stops stopping rounds, and the terrain {@link Hit} names the
 * {@link MapBlock} it met so the impact can take its look and sound from the surface's material.
 * {@link #damageableCrossings} is the complementary question — what a segment crosses that a
 * blast, swing or round could change — because a pane is in no blocking view and a damage path
 * cannot find it in one.
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
     * @param terrain  the solid's rectangle, or {@code null} for a player
     * @param block    the solid's block — the surface's material is what the impact looks and
     *                 sounds like — or {@code null} for a player
     */
    public record Hit(
            HitType type, float fraction, float x, float y, Player player, Rect terrain, MapBlock block) {
    }

    /**
     * One block on a round's path that a damage source can act on.
     *
     * @param block    the block
     * @param fraction where the segment enters it, in {@code [0, 1)}
     * @param x        entry position
     * @param y        entry position
     */
    public record BlockCrossing(MapBlock block, float fraction, float x, float y) {
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

    /**
     * The per-round destruction state, when this raycaster resolves against a live match. Null in
     * geometry-only use, where the authored map is the whole world; the server wires it, and the
     * live map it exposes is then what every sweep below reads, so a destroyed block stops
     * blocking the moment it leaves the map.
     */
    private MapDamageState destruction;

    public RaycastBulletSystem(ArenaMap arena) {
        this.arena = arena;
    }

    /**
     * Wires the destruction state whose live map this raycaster resolves against. Null detaches;
     * safe to call more than once, like every other optional collaborator in the server.
     */
    public void setDestruction(MapDamageState destruction) {
        this.destruction = destruction;
    }

    /** The map every sweep here reads: the live one when a destruction state is wired. */
    public ArenaMap arena() {
        return map();
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
        MapBlock hitBlock = null;
        ArenaMap map = map();
        if (map != null) {
            for (MapBlock block : map.blocks()) {
                // The column decides, never a kind check: intact glass and a one-way platform are
                // simply absent from what a round can meet.
                if (!BlockRules.blocks(block.kind(), Blocking.BULLETS)) {
                    continue;
                }
                float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, block.rect());
                if (entry >= 0f && entry < bestTerrain) {
                    bestTerrain = entry;
                    hitBlock = block;
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
                lerp(y0, y1, bestPlayer), hitPlayer, null, null);
        }
        if (hitBlock != null) {
            return new Hit(HitType.TERRAIN, bestTerrain, lerp(x0, x1, bestTerrain),
                lerp(y0, y1, bestTerrain), null, hitBlock.rect(), hitBlock);
        }
        return null;
    }

    /**
     * Every block this segment crosses that {@code source} can hurt, nearest entry first, limited
     * to the part of the path strictly before {@code limit} (a fraction of the segment).
     *
     * <p>This is the other half of {@link #resolve}: a window is in no blocking view, so a damage
     * path has to ask what a segment meets in the live map rather than what a line query reports.
     * {@link MaterialTable#takesDamage} is the filter, so a stone breakable the round cannot chip
     * is left to the ordinary blocking hit, and every returned block is one a hit could change.
     *
     * @param source the damage source about to be applied, which selects the retention column
     * @param limit  exclusive upper bound on the entry fraction, for a walk bounded by the nearest
     *               blocking hit; pass {@code 1f} or more to walk the whole segment
     */
    public List<BlockCrossing> damageableCrossings(
            DamageSource source, float x0, float y0, float x1, float y1, float limit) {
        List<BlockCrossing> crossings = new ArrayList<>();
        ArenaMap map = map();
        if (map == null) {
            return crossings;
        }
        for (MapBlock block : map.blocks()) {
            if (!MaterialTable.takesDamage(block, source)) {
                continue;
            }
            float entry = Geometry.segmentAabbEntry(x0, y0, x1, y1, block.rect());
            if (entry < 0f || entry >= limit) {
                continue;
            }
            crossings.add(new BlockCrossing(block, entry, lerp(x0, x1, entry), lerp(y0, y1, entry)));
        }
        crossings.sort((a, b) -> Float.compare(a.fraction(), b.fraction()));
        return crossings;
    }

    /** The authored map, or the live one when a destruction state is wired. Never mutated. */
    private ArenaMap map() {
        return destruction == null ? arena : destruction.liveMap();
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

    /**
     * True when terrain blocks the straight line between two points for the given purpose.
     *
     * <p>The purpose is explicit because the question has more than one right answer: the muzzle
     * guard asks {@link Blocking#BULLETS} (a round is absorbed by whatever would have stopped it),
     * while sight and blast ask the same segment through their own column.
     */
    public boolean blockedByTerrain(float x0, float y0, float x1, float y1, Blocking purpose) {
        return MapQueries.lineBlocked(map(), x0, y0, x1, y1, purpose);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
