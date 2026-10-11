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
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MaterialTable;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.weapons.HeldWeaponGeometry;
import io.github.skystrike.shared.weapons.WeaponBallistics;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The authoritative life of every round in flight.
 *
 * <p>Spawn at the drawn muzzle, integrate with {@code BallisticsMath}, sweep the tick's path for
 * impacts, apply damage, retire. Nothing else in the server moves a bullet.
 *
 * <p>Impacts resolve against players, terrain <b>and the Phase 6 gadget devices</b> (drones and
 * throw cameras, mechanics §7): a round can land on a device, and the nearest impact across all
 * three target families wins, so a device standing in front of a player takes the round.
 *
 * <p>Three rules are worth stating out loud:
 * <ul>
 *   <li>A round fired while hugging a wall is <b>absorbed at the muzzle</b>: the eye-to-muzzle
 *       segment is tested at spawn, so the barrel length that makes the tracer leave the drawn
 *       barrel can never place a round on the far side of geometry. The muzzle is the client's
 *       drawn barrel tip ({@link HeldWeaponGeometry}), not a fixed offset — which is also why a
 *       long gun needs more clear space in front of the eye before it will fire at all.</li>
 *   <li>A round cannot hit its owner for the first {@value CombatConfig#SELF_HIT_GRACE_SECONDS}
 *       seconds. After that it can, because self-damage is on and flying into your own shot is a
 *       legitimate way to die.</li>
 *   <li>Rounds retire on lifetime, on falling below a minimum speed, past a multiple of their
 *       maximum range, or on leaving the arena — a stalled bullet never rains down on someone a
 *       second later.</li>
 *   <li>A round acts on the block it meets. A chip stops the round in the surface it could not
 *       open; a block that leaves the map — a shattered pane, the last hit on a breakable — lets
 *       the round fly on through the hole with the small pass-through speed loss, so shooting
 *       through a window is a shot, not a wall.</li>
 * </ul>
 */
public final class BulletSystem {

    /**
     * Upper bound on how many blocks one round may act on inside one tick before it is retired.
     * A round would have to cross eight damageable blocks in a single 60 Hz step to reach it, so
     * the bound never fires on a real map; it exists so a pathological one cannot make a single
     * round resolve unbounded work.
     */
    private static final int MAX_BLOCK_ACTIONS_PER_TICK = 8;

    private final ArenaMap arena;
    private final RaycastBulletSystem raycaster;
    private final List<Projectile> projectiles = new ArrayList<>();

    /**
     * The per-round destruction state, set by the server. Null in geometry-only use: without it
     * nothing on the map can be hurt, a pane is transparent exactly as the blocking column says,
     * and every sweep reads the authored map — which is what the combat tests pin.
     */
    private MapDamageState destruction;

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
     * Wires the live destruction state: rounds then chip breakables, shatter panes and fly on
     * through the holes they open, and every terrain query follows the live map. Null detaches.
     */
    public void setDestruction(MapDamageState destruction) {
        this.destruction = destruction;
        this.raycaster.setDestruction(destruction);
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

        float eyeX = owner.eyeX();
        float eyeY = owner.eyeY();
        // The round leaves the barrel the client actually draws, which is a different distance
        // for every weapon (a pistol is ~22 units out, the longest sniper ~62).
        float muzzleX = HeldWeaponGeometry.muzzleX(weaponId.ordinal(), eyeX, angleDegrees);
        float muzzleY = HeldWeaponGeometry.muzzleY(weaponId.ordinal(), eyeY, angleDegrees);

        // The barrel is longer than it used to be, so this guard matters more, not less: the
        // segment the round is born along is tested against the terrain, and a shot fired while
        // hugging a wall is absorbed at the muzzle instead of appearing on the far side of it.
        // Note that a longer barrel needs more clear space in front of the eye to exist at all.
        // The guard asks the shared BULLETS column, exactly as the round's own sweep does: the
        // muzzle is not blocked by anything the round could have passed through.
        if (raycaster.blockedByTerrain(eyeX, eyeY, muzzleX, muzzleY, Blocking.BULLETS)) {
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

            boolean spent = resolvePath(projectile, targets, damage, canHitOwner, fromX, fromY);
            if (spent) {
                projectiles.remove(i);
                continue;
            }

            if (BallisticsMath.isExpired(projectile, ballistics) || isOutOfBounds(projectile)) {
                projectiles.remove(i);
            }
        }
    }

    /**
     * Walks one round's path this tick, nearest first, and resolves what it meets.
     *
     * <p>The walk exists because a round can now change the map as it travels: a pane shatters and
     * the round flies on through the aperture with a small speed loss, and the last hit that opens
     * a breakable lets the round continue through the hole as well. Each iteration resolves the
     * nearest player-or-blocking hit and the nearest damageable block in front of it; a block the
     * round took out is gone from the live map, so the next iteration's sweep simply does not see
     * it. Everything else the round meets spends it — a player, a device, a wall, a closed door,
     * or a surface whose material refuses the round — and its position is set to the impact so the
     * effect lands where it stopped.
     *
     * @return true when the round is spent and should be retired
     */
    private boolean resolvePath(
            Projectile projectile,
            Collection<Player> targets,
            DamageService damage,
            boolean canHitOwner,
            float fromX,
            float fromY) {

        // The tick's end position, kept apart from the round's own position: passing through a
        // block moves neither the round nor the segment it still has to fly this tick.
        float endX = projectile.x;
        float endY = projectile.y;
        float startX = fromX;
        float startY = fromY;

        for (int step = 0; step < MAX_BLOCK_ACTIONS_PER_TICK; step++) {
            RaycastBulletSystem.Hit hit = raycaster.resolve(
                startX, startY, endX, endY, targets, projectile.ownerId, canHitOwner);
            float stopFraction = hit == null ? 1f : hit.fraction();

            // A round can also land on a gadget device; the nearest impact across players,
            // terrain and devices wins, so a drone in front of a player shields them.
            RaycastBulletSystem.GadgetHit gadgetHit = resolveGadgetHit(
                startX, startY, endX, endY, projectile.ownerId, canHitOwner);

            // Every damageable block strictly in front of whatever is about to spend the round.
            // The device is part of that bound: a window between the round and a drone is still
            // on the path and has to shatter before the round reaches the drone. A window is in
            // no blocking view, so this is where a round meets one at all.
            float crossingLimit = stopFraction;
            if (gadgetHit != null && gadgetHit.fraction() < crossingLimit) {
                crossingLimit = gadgetHit.fraction();
            }
            RaycastBulletSystem.BlockCrossing crossing =
                firstCrossingInFront(projectile, startX, startY, crossingLimit);
            if (crossing != null) {
                projectile.x = crossing.x();
                projectile.y = crossing.y();
                if (!damageBlock(projectile, crossing.block(), crossing.x(), crossing.y())) {
                    return true;
                }
                loseSpeedForPassThrough(projectile);
                startX = crossing.x();
                startY = crossing.y();
                projectile.x = endX;
                projectile.y = endY;
                continue;
            }

            if (gadgetHit != null && gadgetHit.fraction() < stopFraction) {
                projectile.x = gadgetHit.x();
                projectile.y = gadgetHit.y();
                applyGadgetHit(projectile, gadgetHit);
                return true;
            }

            if (hit == null) {
                projectile.x = endX;
                projectile.y = endY;
                return false;
            }

            projectile.x = hit.x();
            projectile.y = hit.y();
            if (hit.type() == RaycastBulletSystem.HitType.PLAYER) {
                playerImpacts++;
                applyHit(projectile, hit, targets, damage);
                return true;
            }

            // A blocker: a wall or closed door, or a surface whose material refuses the round. If
            // the round is what takes it out of the map, the round flies on through the hole.
            if (damageBlock(projectile, hit.block(), hit.x(), hit.y())) {
                loseSpeedForPassThrough(projectile);
                startX = hit.x();
                startY = hit.y();
                projectile.x = endX;
                projectile.y = endY;
                continue;
            }
            terrainImpacts++;
            return true;
        }

        // The guard above is far above any real path; retiring is the safe end if a map ever
        // produces one that is not.
        return true;
    }

    /**
     * The nearest block on the segment that this source could hurt, or {@code null} when the
     * round cannot change anything on it — including the case of no destruction state at all, in
     * which nothing on the map can be hurt and a pane is transparent as the blocking column says.
     */
    private RaycastBulletSystem.BlockCrossing firstCrossingInFront(
            Projectile projectile, float fromX, float fromY, float limit) {
        if (destruction == null) {
            return null;
        }
        List<RaycastBulletSystem.BlockCrossing> crossings = raycaster.damageableCrossings(
            DamageSource.BULLET, fromX, fromY, projectile.x, projectile.y, limit);
        return crossings.isEmpty() ? null : crossings.get(0);
    }

    /**
     * Applies the round's damage to one block and emits its surface impact.
     *
     * <p>The damage is the same falloff number a player at that distance would take, because a
     * round does not know what it is hitting until the material's retention answers. The impact's
     * type comes from the block's material, so a crate sounds and looks like wood and a pane
     * shatters the way {@code GLASS_SHATTER} was authored to.
     *
     * @return true when this hit took the block out of the map
     */
    private boolean damageBlock(Projectile projectile, MapBlock block, float x, float y) {
        WeaponDefinition stats = WeaponRegistry.ofOrdinal(projectile.weaponId);
        float damage = BallisticsMath.damageAfterFalloff(
            stats.damage(), projectile.distanceTravelled, stats.ballistics());
        BlockBreak broke = destruction == null
            ? null
            : destruction.damage(block, DamageSource.BULLET, damage, x, y);
        emitImpact(projectile, block, x, y);
        return broke != null;
    }

    /** The small cost of punching through: paid once per block the round took out. */
    private static void loseSpeedForPassThrough(Projectile projectile) {
        float kept = 1f - CombatConfig.PASS_THROUGH_SPEED_LOSS_FRACTION;
        projectile.vx *= kept;
        projectile.vy *= kept;
    }

    /**
     * One surface impact effect (build plan M7 §8.1): the impact point, along the round's travel
     * direction, with the type the surface's {@code MaterialTable} row names — concrete, metal,
     * wood or the pane's shatter.
     */
    private void emitImpact(Projectile projectile, MapBlock block, float x, float y) {
        if (effectSink == null || block == null) {
            return;
        }
        float travelAngle = Angles.toDegrees((float) Math.atan2(projectile.vy, projectile.vx));
        effectSink.emit(new EffectSpawn(
            MaterialTable.profileFor(block.material()).impactEffect(), x, y, travelAngle, 1f));
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

    /** The map rounds are resolved against: the live one when a destruction state is wired. */
    public ArenaMap arena() {
        return raycaster.arena();
    }

    public void clear() {
        projectiles.clear();
    }
}
