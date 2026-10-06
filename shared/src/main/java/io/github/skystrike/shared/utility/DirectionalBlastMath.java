package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.vision.VisionMath;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Occluded radial damage restricted to a facing cone, used by the placed claymore.
 *
 * <p>This deliberately mirrors {@link ExplosionMath}: linear falloff, an absolute terrain
 * block, one result per live target, and an impulse directed away from the device. The additional
 * cone test happens before the line-of-sight ray so a player standing behind the claymore is
 * never hit by its forward charge.
 */
public final class DirectionalBlastMath {

    private DirectionalBlastMath() {
    }

    /** One directional detonation. {@code directionDegrees} points through the cone centre. */
    public record Blast(
        float x,
        float y,
        float radius,
        float maxDamage,
        float impulse,
        float directionDegrees,
        float halfAngleDegrees,
        int ownerId,
        int weaponWireId
    ) {
        public Blast {
            if (radius <= 0f) {
                throw new IllegalArgumentException("blast radius must be positive");
            }
            if (halfAngleDegrees <= 0f || halfAngleDegrees > 180f) {
                throw new IllegalArgumentException("blast half-angle must be in (0, 180]");
            }
        }

        public static Blast of(UtilityDefinition definition, float x, float y, float directionDegrees, int ownerId) {
            return new Blast(
                x,
                y,
                definition.radius(),
                definition.damage(),
                definition.impulse(),
                directionDegrees,
                io.github.skystrike.shared.config.UtilityConfig.CLAYMORE_CONE_HALF_ANGLE_DEGREES,
                ownerId,
                definition.wireId());
        }
    }

    /** A single player reached by the cone. The shape matches {@link ExplosionMath.BlastHit}. */
    public record BlastHit(int playerId, float damage, float impulseX, float impulseY, float distance) {
    }

    /** True when a point is inside the blast's forward cone, including its edge. */
    public static boolean contains(Blast blast, float targetX, float targetY) {
        if (blast == null) {
            return false;
        }
        float dx = targetX - blast.x();
        float dy = targetY - blast.y();
        float distanceSq = dx * dx + dy * dy;
        if (distanceSq >= blast.radius() * blast.radius()) {
            return false;
        }
        if (distanceSq <= 1e-8f) {
            return true;
        }
        float angle = Angles.toDegrees((float) Math.atan2(dy, dx));
        return Math.abs(Angles.shortestDelta(blast.directionDegrees(), angle)) <= blast.halfAngleDegrees();
    }

    /** Resolves the cone against candidates, applying the same occlusion/falloff rules as a frag. */
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
            if (!contains(blast, targetX, targetY)
                || !VisionMath.hasLineOfSight(blast.x(), blast.y(), targetX, targetY, map)) {
                continue;
            }
            float dx = targetX - blast.x();
            float dy = targetY - blast.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float damage = ExplosionMath.falloff(blast.maxDamage(), distance, blast.radius());
            if (damage <= 0f) {
                continue;
            }
            float impulse = impulseAt(blast, distance);
            float directionX = distance <= 1e-4f ? 0f : dx / distance;
            float directionY = distance <= 1e-4f ? 1f : dy / distance;
            hits.add(new BlastHit(player.id, damage, directionX * impulse, directionY * impulse, distance));
        }
        return hits;
    }

    /** Uses the same gentle impulse falloff as an ordinary explosion. */
    public static float impulseAt(Blast blast, float distance) {
        ExplosionMath.Blast radial = new ExplosionMath.Blast(
            blast.x(), blast.y(), blast.radius(), blast.maxDamage(), blast.impulse(),
            blast.ownerId(), blast.weaponWireId());
        return ExplosionMath.impulseAt(radial, distance);
    }
}
