package io.github.skystrike.server.combat;

import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.server.gadget.CameraSystem;
import io.github.skystrike.server.gadget.DroneSystem;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
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
 * <p>Impacts resolve against players, terrain <b>and the Phase 6 gadget devices</b> (drones and
 * throw cameras, mechanics §7): a round can land on a device, and the nearest impact across all
 * three target families wins, so a device standing in front of a player takes the round.
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

    /**
     * Where impact visuals go (build plan M7 §8.1). Null in geometry-only tests; a missing sink
     * never changes gameplay, only presentation.
     */
    private EffectSink effectSink;

    /**
     * The gadget device systems whose entities rounds can hit (mechanics §7: drones and cameras
     * are destructible). Null in combat-only tests — no devices, no device impacts.
     */
    private DroneSystem droneSystem;
    private CameraSystem cameraSystem;

    private int nextId = 1;
    private long spawned;
    private long terrainImpacts;
    private long playerImpacts;
    private long gadgetImpacts;

    public BulletSystem(ArenaMap arena) {
        this.arena = arena;
        this.raycaster = new RaycastBulletSystem(arena);
    }

    /** Installs the effect sink. Null detaches; safe to call more than once. */
    public void setEffectSink(EffectSink effectSink) {
        this.effectSink = effectSink;
    }

    /**
     * Wires the gadget device systems whose entities are valid bullet targets. Null systems (or
     * null arguments) simply contribute no targets, so geometry-only tests see no change.
     */
    public void setGadgetSystems(DroneSystem drones, CameraSystem cameras) {
        this.droneSystem = drones;
        this.cameraSystem = cameras;
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

            // A round can also land on a gadget device; the nearest impact across players,
            // terrain and devices wins, so a drone in front of a player shields them.
            RaycastBulletSystem.GadgetHit gadgetHit = resolveGadgetHit(
                fromX, fromY, projectile.x, projectile.y, projectile.ownerId, canHitOwner);
            if (gadgetHit != null && (hit == null || gadgetHit.fraction() < hit.fraction())) {
                projectile.x = gadgetHit.x();
                projectile.y = gadgetHit.y();
                applyGadgetHit(projectile, gadgetHit);
                projectiles.remove(i);
                continue;
            }

            if (hit != null) {
                projectile.x = hit.x();
                projectile.y = hit.y();
                if (hit.type() == RaycastBulletSystem.HitType.PLAYER) {
                    playerImpacts++;
                    applyHit(projectile, hit, targets, damage);
                } else {
                    terrainImpacts++;
                    emitTerrainImpact(projectile);
                }
                projectiles.remove(i);
                continue;
            }

            if (BallisticsMath.isExpired(projectile, ballistics) || isOutOfBounds(projectile)) {
                projectiles.remove(i);
            }
        }
    }

    /**
     * One surface impact effect (build plan M7 §8.1): the impact point, along the round's travel
     * direction. Every arena solid is concrete today — the metal and wood variants of the effect
     * catalogue wait on a surface-material pass, and the wire enum already carries them.
     */
    private void emitTerrainImpact(Projectile projectile) {
        if (effectSink == null) {
            return;
        }
        float travelAngle = Angles.toDegrees((float) Math.atan2(projectile.vy, projectile.vx));
        effectSink.emit(new EffectSpawn(
            EffectType.BULLET_IMPACT_CONCRETE, projectile.x, projectile.y, travelAngle, 1f));
    }

    /** The gadget-device sweep for one round's path, or {@code null} with no devices wired. */
    private RaycastBulletSystem.GadgetHit resolveGadgetHit(
            float fromX, float fromY, float toX, float toY, int ownerId, boolean canHitOwner) {
        if (droneSystem == null && cameraSystem == null) {
            return null;
        }
        return raycaster.resolveGadgets(
            fromX, fromY, toX, toY,
            droneSystem == null ? null : droneSystem.active(),
            cameraSystem == null ? null : cameraSystem.active(),
            ownerId,
            canHitOwner);
    }

    /**
     * One round landing on a gadget device. Devices have no hit zones and no shield: the round's
     * damage after falloff drains the device's health, and the owning system destroys it when the
     * pool runs out. A device hit is a metal spark, not concrete dust.
     */
    private void applyGadgetHit(Projectile projectile, RaycastBulletSystem.GadgetHit hit) {
        gadgetImpacts++;
        WeaponDefinition stats = WeaponRegistry.ofOrdinal(projectile.weaponId);
        float damage = BallisticsMath.damageAfterFalloff(
            stats.damage(), projectile.distanceTravelled, stats.ballistics());
        if (hit.drone() != null) {
            hit.drone().applyDamage(damage);
        } else if (hit.camera() != null) {
            hit.camera().applyDamage(damage);
        }
        if (effectSink != null) {
            float travelAngle = Angles.toDegrees((float) Math.atan2(projectile.vy, projectile.vx));
            effectSink.emit(new EffectSpawn(
                EffectType.BULLET_IMPACT_METAL, projectile.x, projectile.y, travelAngle, 1f));
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
            projectile.distanceTravelled,
            targets);
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

    /** Rounds that landed on a gadget device (drone or camera) since the server started. */
    public long gadgetImpactCount() {
        return gadgetImpacts;
    }

    public ArenaMap arena() {
        return arena;
    }

    public void clear() {
        projectiles.clear();
    }
}
