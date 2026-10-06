package io.github.skystrike.server.gadget;

import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.utility.ExplosionMath;
import java.util.Collection;

/**
 * Authoritative passive fuel-tank destruction.
 *
 * <p>The tank is detected by {@code HitZoneMath} in {@code DamageService}; this system is called
 * only after that one zone resolution. It marks the intact tank broken, kills its wearer through
 * the normal death/kill-feed path without allowing the wearer's shield to save them, then reuses
 * {@link ExplosionMath} for the 120-damage, terrain-occluded blast against every other target.
 * {@code ExplosionMath.resolve} gives the blast one candidate hit per player, and each accepted
 * hit is handed back to {@code DamageService}, so friendly fire, shields, health and attribution
 * remain centralised.
 */
public final class FuelTankSystem {

    private final ArenaMap arena;

    public FuelTankSystem(ArenaMap arena) {
        if (arena == null) {
            throw new IllegalArgumentException("arena is required");
        }
        this.arena = arena;
    }

    /**
     * Detonates an intact tank at the bullet impact point.
     *
     * @return the wearer's authoritative kill result, or {@code null} when no intact tank was
     *         available
     */
    public DamageService.DamageResult detonate(
            Player wearer,
            Player attacker,
            int attackerId,
            float impactX,
            float impactY,
            Collection<Player> candidates,
            DamageService damage) {
        if (wearer == null || wearer.loadout == null || !wearer.alive || damage == null) {
            return null;
        }
        GadgetSlot tank = wearer.loadout.fuelTankSlot();
        if (tank == null || !tank.isUsable()) {
            return null;
        }

        // A tank is a one-shot life-state transition. Do this before resolving the blast so the
        // wearer cannot be found again as an intact target and detonate twice.
        tank.broken = true;
        tank.active = false;
        tank.durability = 0f;

        int wireId = GadgetId.FUEL_TANK.wireId();
        DamageService.DamageResult wearerResult = damage.applyUnshielded(
            attacker,
            attackerId,
            wearer,
            Math.max(PlayerConfig.MAX_HEALTH, GadgetConfig.FUEL_TANK_EXPLOSION_DAMAGE),
            HitZone.FUEL_TANK,
            wireId,
            impactX,
            impactY,
            0f);

        ExplosionMath.Blast blast = new ExplosionMath.Blast(
            impactX,
            impactY,
            GadgetConfig.FUEL_TANK_EXPLOSION_RADIUS,
            GadgetConfig.FUEL_TANK_EXPLOSION_DAMAGE,
            GadgetConfig.FUEL_TANK_EXPLOSION_IMPULSE,
            attackerId,
            wireId);
        if (candidates != null) {
            for (ExplosionMath.BlastHit hit : ExplosionMath.resolve(blast, candidates, arena)) {
                Player target = byId(candidates, hit.playerId());
                if (target == null || target == wearer || !DamageService.canDamage(attacker, target)) {
                    continue;
                }
                DamageService.DamageResult result = damage.apply(
                    attacker,
                    attackerId,
                    target,
                    hit.damage(),
                    HitZone.BODY,
                    wireId,
                    impactX,
                    impactY,
                    hit.distance());
                if (result != null && target.alive) {
                    target.vx += hit.impulseX();
                    target.vy += hit.impulseY();
                }
            }
        }
        return wearerResult;
    }

    /** Convenience form for direct tests and callers whose impact is the wearer's centre. */
    public DamageService.DamageResult detonate(
            Player wearer,
            Player attacker,
            Collection<Player> candidates,
            DamageService damage) {
        return detonate(
            wearer,
            attacker,
            attacker == null ? -1 : attacker.id,
            wearer == null ? 0f : wearer.centerX(),
            wearer == null ? 0f : wearer.centerY(),
            candidates,
            damage);
    }

    public ArenaMap arena() {
        return arena;
    }

    private static Player byId(Collection<Player> players, int id) {
        for (Player player : players) {
            if (player != null && player.id == id) {
                return player;
            }
        }
        return null;
    }
}
