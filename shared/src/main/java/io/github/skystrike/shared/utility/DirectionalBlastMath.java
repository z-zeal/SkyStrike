package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.MaterialTable;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Occluded radial damage restricted to a facing cone, used by the placed claymore.
 *
 * <p>This deliberately mirrors {@link ExplosionMath}: linear falloff, an absolute terrain
 * block, one result per live target, and an impulse directed away from the device. The additional
 * cone test happens before the occlusion ray so a player standing behind the claymore is never
 * hit by its forward charge. Occlusion is the shared {@link Blocking#BLAST} column, which the
 * rule table pins to sight, so the claymore and a frag can never disagree about a window.
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
                || MapQueries.lineBlocked(
                    map, blast.x(), blast.y(), targetX, targetY, Blocking.BLAST)) {
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

    /**
     * Damage one block takes from one directional blast, measured at the block's nearest point to
     * the detonation and only when that point is inside the cone.
     *
     * <p>The cone test happens on the measured point, not the block's centre: a claymore aimed
     * across a crate's near corner catches that corner, and the far half of the crate being outside
     * the charge's arc is not a reason for the corner to escape it. Occlusion and falloff are
     * {@link ExplosionMath}'s, so a crate between a claymore and its target — and a pane that
     * passes sight — behave exactly as they do for a frag.
     */
    public static float damageAtBlock(Blast blast, MapBlock block, ArenaMap map) {
        Objects.requireNonNull(blast, "blast");
        Objects.requireNonNull(block, "block");
        Rect rect = block.rect();
        float nearX = rect.clampX(blast.x());
        float nearY = rect.clampY(blast.y());
        if (!contains(blast, nearX, nearY)) {
            return 0f;
        }
        if (ExplosionMath.occludedExcluding(map, blast.x(), blast.y(), rect, nearX, nearY)) {
            return 0f;
        }
        float distance = distance(blast.x(), blast.y(), nearX, nearY);
        return ExplosionMath.falloff(blast.maxDamage(), distance, blast.radius());
    }

    /**
     * Applies one directional blast to every block the live map can lose, in map order, and
     * returns what broke.
     *
     * <p>Same shape as {@link ExplosionMath#resolveBlocks}: only surfaces the shared table says
     * accept an explosion's damage are asked, and a wall's permanent kind never enters the loop.
     */
    public static List<BlockBreak> resolveBlocks(Blast blast, MapDamageState state) {
        List<BlockBreak> broke = new ArrayList<>();
        if (blast == null || state == null) {
            return broke;
        }
        ArenaMap map = state.liveMap();
        for (MapBlock block : map.blocks()) {
            if (!MaterialTable.takesDamage(block, DamageSource.BLAST)) {
                continue;
            }
            float damage = damageAtBlock(blast, block, map);
            if (damage <= 0f) {
                continue;
            }
            Rect rect = block.rect();
            BlockBreak result = state.damage(
                block, DamageSource.BLAST, damage, rect.clampX(blast.x()), rect.clampY(blast.y()));
            if (result != null) {
                broke.add(result);
            }
        }
        return broke;
    }

    /** Uses the same gentle impulse falloff as an ordinary explosion. */
    public static float impulseAt(Blast blast, float distance) {
        ExplosionMath.Blast radial = new ExplosionMath.Blast(
            blast.x(), blast.y(), blast.radius(), blast.maxDamage(), blast.impulse(),
            blast.ownerId(), blast.weaponWireId());
        return ExplosionMath.impulseAt(radial, distance);
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
