package io.github.skystrike.server.combat;

import io.github.skystrike.server.gadget.FuelTankSystem;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import io.github.skystrike.shared.combat.BallisticsMath;
import io.github.skystrike.shared.combat.ShieldArcMath;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.combat.HitZoneMath;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ShieldState;
import io.github.skystrike.shared.model.Team;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Applies damage: falloff, hit zone, friendly fire, death (mechanics §4.1, §4.2, §4.6).
 *
 * <p>Every number a round does to a player is decided here, in one order that never varies:
 * muzzle damage → distance falloff → zone multiplier. Doing falloff first and the headshot
 * second is what keeps "a headshot is worth exactly double" true at every range.
 *
 * <p>Friendly fire and self-damage are on by default and Neutral fights everyone, per §4.6.
 */
public final class DamageService {

    /**
     * One resolved damage instance, ready to be turned into a packet.
     *
     * @param amount            damage actually applied, after falloff and zone
     * @param remainingHealth   victim health after the hit
     * @param distanceTravelled path length of the round that caused it
     */
    public record DamageResult(
        int attackerId,
        int targetId,
        float amount,
        float remainingHealth,
        HitZone zone,
        int weaponId,
        float x,
        float y,
        float distanceTravelled,
        boolean killed
    ) {
        public boolean isSelfInflicted() {
            return attackerId == targetId;
        }
    }

    private final KillFeedService killFeed;
    private final FuelTankSystem fuelTankSystem;
    private final Deque<DamageResult> pending = new ArrayDeque<>();

    /** {@code sv_godmode} (build plan M3 §4): true for a target id makes every hit on it a no-op. */
    private IntPredicate godmode = id -> false;

    public DamageService(KillFeedService killFeed) {
        this(killFeed, null);
    }

    /** Production constructor; the optional system keeps legacy combat tests tank-free. */
    public DamageService(KillFeedService killFeed, FuelTankSystem fuelTankSystem) {
        this.killFeed = killFeed == null ? new KillFeedService() : killFeed;
        this.fuelTankSystem = fuelTankSystem;
    }

    /**
     * Wires the per-player {@code sv_godmode} flag in. Defaults to "nobody", so tests and legacy
     * call sites that never call this see unchanged behaviour.
     */
    public void setGodmodePredicate(IntPredicate godmode) {
        this.godmode = godmode == null ? id -> false : godmode;
    }

    /**
     * Whether {@code attacker} may damage {@code target} at all.
     *
     * <p>Both friendly fire and self-damage are enabled by default, so the only real filters are
     * "the target is alive" and the config switches. Neutral players are outside the team system
     * entirely: they damage and are damaged by everyone, including other Neutrals.
     */
    public static boolean canDamage(Player attacker, Player target) {
        if (target == null || !target.alive) {
            return false;
        }
        if (attacker == null) {
            return true;
        }
        if (attacker.id == target.id) {
            return CombatConfig.SELF_DAMAGE;
        }
        // One spelling of "same side", shared with the kill feed's [FF] tag and the minimap's
        // blip colours. Neutral is outside the team system, so a Neutral attacker always reaches
        // this branch's `true` — including against another Neutral.
        if (Team.areAllies(attacker.teamIndex, target.teamIndex)) {
            return CombatConfig.FRIENDLY_FIRE;
        }
        return true;
    }

    /**
     * Resolves one round landing on one player.
     *
     * @param attacker          the shooter, or {@code null} if they have since left
     * @param attackerId        the shooter's id, which survives their disconnect
     * @param impactY           impact height, which decides the hit zone
     * @param distanceTravelled path length of the round, which decides the falloff
     * @return the applied result, or {@code null} when the hit was not allowed
     */
    public DamageResult applyBulletDamage(
            Player attacker,
            int attackerId,
            Player target,
            WeaponDefinition stats,
            float impactX,
            float impactY,
            float distanceTravelled) {
        return applyBulletDamage(
            attacker, attackerId, target, stats, impactX, impactY, distanceTravelled,
            target == null ? Collections.emptyList() : Collections.singletonList(target));
    }

    /**
     * Bullet entry point used by the authoritative projectile system. Hit-zone resolution happens
     * once here; a fuel-tank result is handed to {@link FuelTankSystem} without resolving the
     * zone a second time.
     */
    public DamageResult applyBulletDamage(
            Player attacker,
            int attackerId,
            Player target,
            WeaponDefinition stats,
            float impactX,
            float impactY,
            float distanceTravelled,
            Collection<Player> candidates) {

        if (stats == null || !canDamage(attacker, target)) {
            return null;
        }

        HitZone zone = HitZoneMath.resolve(impactX, impactY, target);
        float afterFalloff =
            BallisticsMath.damageAfterFalloff(stats.damage(), distanceTravelled, stats.ballistics());
        float damage = HitZoneMath.applyZone(afterFalloff, zone);
        float healthDamage = passThroughShield(
            attacker, target, damage, impactX, impactY);
        if (healthDamage <= 0f) {
            return null;
        }

        if (zone == HitZone.FUEL_TANK && fuelTankSystem != null) {
            return fuelTankSystem.detonate(
                target, attacker, attackerId, impactX, impactY, candidates, this);
        }
        return applyHealth(
            attacker, attackerId, target, healthDamage, zone, stats.id().ordinal(),
            impactX, impactY, distanceTravelled);
    }

    /**
     * Applies an already-resolved amount. Explosions, melee and utility damage come through here
     * too, so the shield and death handling exist exactly once.
     */
    public DamageResult apply(
            Player attacker,
            int attackerId,
            Player target,
            float damage,
            HitZone zone,
            int weaponId,
            float x,
            float y,
            float distanceTravelled) {
        return applyWithSource(
            attacker, attackerId, target, damage, zone, weaponId, x, y, distanceTravelled, x, y);
    }

    /**
     * Applies damage whose event position differs from its source direction, as with melee: the
     * event belongs at the victim while the shield must face the attacker.
     */
    public DamageResult applyWithSource(
            Player attacker,
            int attackerId,
            Player target,
            float damage,
            HitZone zone,
            int weaponId,
            float x,
            float y,
            float distanceTravelled,
            float sourceX,
            float sourceY) {
        if (target == null || !target.alive || damage <= 0f) {
            return null;
        }
        float healthDamage = passThroughShield(attacker, target, damage, sourceX, sourceY);
        if (healthDamage <= 0f) {
            return null;
        }
        return applyHealth(
            attacker, attackerId, target, healthDamage, zone, weaponId, x, y, distanceTravelled);
    }

    /** Applies lethal/direct damage without a shield interception (the tank wearer rule). */
    public DamageResult applyUnshielded(
            Player attacker,
            int attackerId,
            Player target,
            float damage,
            HitZone zone,
            int weaponId,
            float x,
            float y,
            float distanceTravelled) {
        return applyHealth(
            attacker, attackerId, target, damage, zone, weaponId, x, y, distanceTravelled);
    }

    /**
     * Resolves the wearer's shield once for one incoming damage instance. A complete absorption
     * produces no health event; partial absorption continues through the one normal health path.
     * The source point is the blast, attacker, melee origin or bullet impact supplied by callers.
     */
    private float passThroughShield(Player attacker, Player target, float damage, float x, float y) {
        if (target.loadout == null) {
            return damage;
        }
        GadgetSlot shield = target.loadout.shieldSlot();
        if (shield == null) {
            return damage;
        }
        ShieldState state = shield.shieldState();
        float sourceX = x;
        float sourceY = y;
        float dx = sourceX - target.centerX();
        float dy = sourceY - target.centerY();
        if (dx * dx + dy * dy <= 1e-8f && attacker != null && attacker.id != target.id) {
            sourceX = attacker.centerX();
            sourceY = attacker.centerY();
            dx = sourceX - target.centerX();
            dy = sourceY - target.centerY();
        }
        float incoming = Angles.ofVector(dx, dy);
        ShieldArcMath.Absorption absorption = ShieldArcMath.resolve(
            state, target.aimAngle, incoming, damage, shield.durability);
        if (absorption.absorbed() > 0f) {
            shield.applyDurabilityDamage(absorption.absorbed());
        }
        return absorption.healthDamage();
    }

    /** The one health/death bookkeeping path. */
    private DamageResult applyHealth(
            Player attacker,
            int attackerId,
            Player target,
            float damage,
            HitZone zone,
            int weaponId,
            float x,
            float y,
            float distanceTravelled) {
        if (target == null || !target.alive || damage <= 0f || godmode.test(target.id)) {
            return null;
        }

        target.health = Math.max(0f, target.health - damage);
        boolean killed = target.health <= 0f;

        if (killed) {
            target.alive = false;
            target.health = 0f;
            target.deaths++;
            target.respawnTimer = CombatConfig.RESPAWN_DELAY_SECONDS;
            target.vx = 0f;
            target.vy = 0f;
            target.jetpacking = false;

            boolean selfInflicted = attacker == null || attacker.id == target.id;
            boolean friendlyFire = !selfInflicted
                && Team.areAllies(attacker.teamIndex, target.teamIndex);
            if (!selfInflicted && !friendlyFire) {
                attacker.kills++;
            }
            killFeed.recordKill(attacker, target, weaponId, zone == HitZone.HEAD);
        }

        DamageResult result = new DamageResult(
            attackerId, target.id, damage, target.health, zone, weaponId, x, y,
            distanceTravelled, killed);
        pending.addLast(result);
        return result;
    }

    /** Returns every damage instance since the last call and clears the queue. */
    public List<DamageResult> drain() {
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        List<DamageResult> results = new ArrayList<>(pending);
        pending.clear();
        return results;
    }

    public int pendingCount() {
        return pending.size();
    }

    public KillFeedService killFeed() {
        return killFeed;
    }
}
