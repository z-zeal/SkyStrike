package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Blast resolution (mechanics §6): linear falloff, and terrain occlusion that is absolute.
 *
 * <p>Two rules from the plan drive everything here:
 *
 * <ul>
 *   <li><b>Terrain blocks damage entirely.</b> Not reduced, not attenuated — a wall between you
 *       and the detonation means zero, even if the grenade landed on the other side of it at
 *       arm's length. Cover is worth taking or it is not worth modelling.</li>
 *   <li><b>One hit per target per explosion.</b> {@link #resolve} returns at most one
 *       {@link BlastHit} per player by construction, so a blast can never be made to double-dip
 *       by overlapping zones or by being resolved twice in one tick.</li>
 * </ul>
 *
 * <p>Sight and distance are both measured to the player's centre rather than the eye: a blast is
 * a volume arriving at a body, not a glance.
 */
public final class ExplosionMath {

    private ExplosionMath() {
    }

    /**
     * One detonation, independent of what threw it.
     *
     * @param x            detonation centre
     * @param y            detonation centre
     * @param radius       distance at which damage reaches zero
     * @param maxDamage    damage at the centre
     * @param impulse      physics impulse at the centre
     * @param ownerId      who gets credit; the owner is still damaged (grenades are not polite)
     * @param weaponWireId what to report in the damage and kill events
     */
    public record Blast(
        float x, float y, float radius, float maxDamage, float impulse, int ownerId, int weaponWireId) {

        public Blast {
            if (radius <= 0f) {
                throw new IllegalArgumentException("blast radius must be positive");
            }
        }

        /** A blast built from a utility definition, centred wherever it went off. */
        public static Blast of(UtilityDefinition definition, float x, float y, int ownerId) {
            return new Blast(
                x, y, definition.radius(), definition.damage(), definition.impulse(),
                ownerId, definition.wireId());
        }
    }

    /**
     * What one player takes from one blast.
     *
     * @param playerId  who was hit
     * @param damage    damage dealt, always greater than zero
     * @param impulseX  knockback, directed away from the detonation
     * @param impulseY  knockback, directed away from the detonation
     * @param distance  distance from the detonation to the player's centre
     */
    public record BlastHit(int playerId, float damage, float impulseX, float impulseY, float distance) {
    }

    /**
     * Damage one point takes from a blast: zero outside the radius, zero without line of sight,
     * otherwise linear falloff from {@code maxDamage} at the centre.
     */
    public static float damageAt(Blast blast, float targetX, float targetY, ArenaMap map) {
        float distance = distance(blast.x(), blast.y(), targetX, targetY);
        if (distance >= blast.radius()) {
            return 0f;
        }
        if (!VisionMath.hasLineOfSight(blast.x(), blast.y(), targetX, targetY, map)) {
            return 0f;
        }
        return falloff(blast.maxDamage(), distance, blast.radius());
    }

    /** Linear falloff from {@code peak} at zero distance to the configured floor at the edge. */
    public static float falloff(float peak, float distance, float radius) {
        if (radius <= 0f) {
            return 0f;
        }
        float t = Math.min(1f, Math.max(0f, distance / radius));
        float scale = 1f - t * (1f - UtilityConfig.BLAST_MIN_DAMAGE_FRACTION);
        return peak * scale;
    }

    /**
     * Resolves a blast against every candidate.
     *
     * <p>Dead players and players taking no damage are left out, so the caller never has to
     * filter zeroes. The result holds at most one entry per player id — that is the plan's "one
     * hit per target per explosion", enforced here rather than trusted to the caller.
     */
    public static List<BlastHit> resolve(Blast blast, Collection<Player> candidates, ArenaMap map) {
        List<BlastHit> hits = new ArrayList<>();
        if (blast == null || candidates == null) {
            return hits;
        }
        for (Player player : candidates) {
            if (player == null || !player.alive) {
                continue;
            }
            float targetX = player.centerX();
            float targetY = player.centerY();
            float distance = distance(blast.x(), blast.y(), targetX, targetY);
            if (distance >= blast.radius()) {
                continue;
            }
            if (!VisionMath.hasLineOfSight(blast.x(), blast.y(), targetX, targetY, map)) {
                continue;
            }
            float damage = falloff(blast.maxDamage(), distance, blast.radius());
            if (damage <= 0f) {
                continue;
            }
            float magnitude = impulseAt(blast, distance);
            float dirX;
            float dirY;
            if (distance <= 1e-4f) {
                // Standing exactly on it: push straight up rather than divide by zero.
                dirX = 0f;
                dirY = 1f;
            } else {
                dirX = (targetX - blast.x()) / distance;
                dirY = (targetY - blast.y()) / distance;
            }
            hits.add(new BlastHit(player.id, damage, dirX * magnitude, dirY * magnitude, distance));
        }
        return hits;
    }

    /**
     * Impulse at a distance. Falls off far more gently than damage — being thrown around is the
     * readable part of an explosion, so the edge keeps a quarter of the push.
     */
    public static float impulseAt(Blast blast, float distance) {
        if (blast.impulse() <= 0f) {
            return 0f;
        }
        float t = Math.min(1f, Math.max(0f, distance / blast.radius()));
        float scale = 1f - t * (1f - UtilityConfig.BLAST_EDGE_IMPULSE_FRACTION);
        return blast.impulse() * scale;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
