package io.github.skystrike.server.combat;

import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.shared.combat.BallisticsMath;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The authoritative life of every round in flight.
 *
 * <p>Spawn at the muzzle, integrate with {@code BallisticsMath}, sweep the tick's path for
 * impacts, apply damage, retire. Nothing else in the server moves a bullet.
 *
 * <p>Three rules are worth stating out loud:
 * <ul>
 *   <li>A round fired while hugging a wall is <b>absorbed at the muzzle</b>: the eye-to-muzzle
 *       segment is tested at spawn, so the offset that makes the tracer leave the barrel can
 *       never place a round on the far side of geometry.</li>
 *   <li>A round cannot hit its owner for the first {@value CombatConfig#SELF_HIT_GRACE_SECONDS}
 *       seconds. After that it can, because self-damage is on and flying into your own shot is a
 *       legitimate way to die.</li>
 *   <li>Rounds retire on lifetime, on falling below a minimum speed, past a multiple of their
 *       maximum range, or on leaving the arena — a stalled bullet never rains down on someone a
 *       second later.</li>
 * </ul>
 */
public final class BulletSystem {

    private final ArenaMap arena;
    private final RaycastBulletSystem raycaster;
    private final List<Projectile> projectiles = new ArrayList<>();

    private int nextId = 1;
    private long spawned;
    private long terrainImpacts;
    private long playerImpacts;

    public BulletSystem(ArenaMap arena) {
        this.arena = arena;
        this.raycaster = new RaycastBulletSystem(arena);
    }

    /**
     * Launches one round from {@code owner}'s muzzle along {@code angleDegrees}.
     *
     * @return the live round, or {@code null} when the muzzle is inside geometry or the cap is hit
     */
    public Projectile spawn(Player owner, WeaponId weaponId, float angleDegrees) {
        if (owner == null || weaponId == null) {
            return null;
        }
        if (projectiles.size() >= CombatConfig.MAX_ACTIVE_PROJECTILES) {
            return null;
        }

        WeaponBallistics ballistics = WeaponBallistics.of(weaponId);
        float aimRadians = Angles.toRadians(angleDegrees);
        float directionX = (float) Math.cos(aimRadians);
        float directionY = (float) Math.sin(aimRadians);

        float eyeX = owner.eyeX();
        float eyeY = owner.eyeY();
        float muzzleX = eyeX + directionX * CombatConfig.MUZZLE_OFFSET;
        float muzzleY = eyeY + directionY * CombatConfig.MUZZLE_OFFSET;

        if (raycaster.blockedByTerrain(eyeX, eyeY, muzzleX, muzzleY)) {
            return null;
        }

        Projectile projectile = new Projectile(
            nextId++,
            owner.id,
            owner.teamIndex,
            weaponId.ordinal(),
            muzzleX,
            muzzleY,
            BallisticsMath.muzzleVelocityX(angleDegrees, ballistics.muzzleSpeed()),
            BallisticsMath.muzzleVelocityY(angleDegrees, ballistics.muzzleSpeed()));
        projectiles.add(projectile);
        spawned++;
        return projectile;
    }

    /**
     * Steps every live round by {@code dt} and resolves what it ran into.
     *
     * @param targets candidate victims — every live player in the match
     * @param damage  where hits are applied; may be {@code null} in geometry-only tests
     */
    public void step(float dt, Collection<Player> targets, DamageService damage) {
        if (dt <= 0f) {
            return;
        }

        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile projectile = projectiles.get(i);
            WeaponId weaponId = projectile.weapon();
            WeaponBallistics ballistics = WeaponBallistics.of(weaponId);

            BallisticsMath.step(projectile, ballistics, dt);

            // Fast rounds sweep the tick's whole path; slow ones degenerate to a point test.
            boolean swept = BallisticsMath.requiresSweep(projectile);
            float fromX = swept ? projectile.prevX : projectile.x;
            float fromY = swept ? projectile.prevY : projectile.y;

            boolean canHitOwner = CombatConfig.SELF_DAMAGE
                && projectile.age > CombatConfig.SELF_HIT_GRACE_SECONDS;

            RaycastBulletSystem.Hit hit = raycaster.resolve(
                fromX, fromY, projectile.x, projectile.y, targets, projectile.ownerId, canHitOwner);

            if (hit != null) {
                projectile.x = hit.x();
                projectile.y = hit.y();
                if (hit.type() == RaycastBulletSystem.HitType.PLAYER) {
                    playerImpacts++;
                    applyHit(projectile, hit, targets, damage);
                } else {
                    terrainImpacts++;
                }
                projectiles.remove(i);
                continue;
            }

            if (BallisticsMath.isExpired(projectile, ballistics) || isOutOfBounds(projectile)) {
                projectiles.remove(i);
            }
        }
    }

    private void applyHit(
            Projectile projectile,
            RaycastBulletSystem.Hit hit,
            Collection<Player> targets,
            DamageService damage) {
        if (damage == null) {
            return;
        }
        WeaponDefinition stats = WeaponRegistry.ofOrdinal(projectile.weaponId);
        Player attacker = findById(targets, projectile.ownerId);
        damage.applyBulletDamage(
            attacker,
            projectile.ownerId,
            hit.player(),
            stats,
            hit.x(),
            hit.y(),
            projectile.distanceTravelled);
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

    private static boolean isOutOfBounds(Projectile projectile) {
        return projectile.x < 0f
            || projectile.y < 0f
            || projectile.x > WorldConfig.ARENA_WIDTH
            || projectile.y > WorldConfig.ARENA_HEIGHT;
    }

    /** Live rounds, in spawn order. */
    public List<Projectile> active() {
        return Collections.unmodifiableList(projectiles);
    }

    public int count() {
        return projectiles.size();
    }

    public long spawnedCount() {
        return spawned;
    }

    public long terrainImpactCount() {
        return terrainImpacts;
    }

    public long playerImpactCount() {
        return playerImpacts;
    }

    public ArenaMap arena() {
        return arena;
    }

    public void clear() {
        projectiles.clear();
    }
}
