package io.github.skystrike.server.combat;

import io.github.skystrike.server.weapons.WeaponStats;
import io.github.skystrike.shared.combat.BallisticsMath;
import io.github.skystrike.shared.combat.HitZoneMath;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

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
    private final Deque<DamageResult> pending = new ArrayDeque<>();

    public DamageService(KillFeedService killFeed) {
        this.killFeed = killFeed == null ? new KillFeedService() : killFeed;
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
        boolean neutralInvolved = attacker.teamIndex == CombatConfig.NEUTRAL_TEAM_INDEX
            || target.teamIndex == CombatConfig.NEUTRAL_TEAM_INDEX;
        if (neutralInvolved) {
            return true;
        }
        if (attacker.teamIndex == target.teamIndex) {
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
            WeaponStats stats,
            float impactX,
            float impactY,
            float distanceTravelled) {

        if (stats == null || !canDamage(attacker, target)) {
            return null;
        }

        HitZone zone = HitZoneMath.resolve(impactY, target);
        float afterFalloff =
            BallisticsMath.damageAfterFalloff(stats.damage(), distanceTravelled, stats.ballistics());
        float damage = HitZoneMath.applyZone(afterFalloff, zone);

        return apply(attacker, attackerId, target, damage, zone, stats.id().ordinal(),
            impactX, impactY, distanceTravelled);
    }

    /**
     * Applies an already-resolved amount. Explosions and melee (Phases 4 and 5) come through
     * here too, so death handling exists exactly once.
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

        if (target == null || !target.alive || damage <= 0f) {
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
                && attacker.teamIndex == target.teamIndex
                && attacker.teamIndex != CombatConfig.NEUTRAL_TEAM_INDEX;
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
