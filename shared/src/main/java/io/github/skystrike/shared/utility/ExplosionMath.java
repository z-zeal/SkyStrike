package io.github.skystrike.shared.utility;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.Blocking;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MapQueries;
import io.github.skystrike.shared.map.MaterialTable;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Geometry;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Blast resolution (mechanics §6): linear falloff, and terrain occlusion that is absolute.
 *
 * <p>Two rules from the plan drive everything here:
 *
 * <ul>
 *   <li><b>Terrain blocks damage entirely.</b> Not reduced, not attenuated — a wall between you
 *       and the detonation means zero, even if the grenade landed on the other side of it at
 *       arm's length. Cover is worth taking or it is not worth modelling. The question is asked
 *       as the shared {@link Blocking#BLAST} column, which the rule table pins to sight: a
 *       window you can see and shoot through never stops a blast either.</li>
 *   <li><b>One hit per target per explosion.</b> {@link #resolve} returns at most one
 *       {@link BlastHit} per player by construction, so a blast can never be made to double-dip
 *       by overlapping zones or by being resolved twice in one tick.</li>
 *   <li><b>Blocks take the same blast, measured at their nearest face.</b> {@link #resolveBlocks}
 *       asks the shared {@link MaterialTable} which surfaces accept an explosion's damage and
 *       {@link MapDamageState} what that did to them; a wall never enters the loop, and the
 *       shipped arena's fixture range — one crate and one pane per side — is what a blast in the
 *       air can actually open.</li>
 * </ul>
 *
 * <p>Sight and distance are both measured to the player's centre rather than the eye: a blast is
 * a volume arriving at a body, not a glance.
 */
public final class ExplosionMath {

    /**
     * Slack at the far end of an occlusion ray: an entry fraction closer to 1 than this is the
     * ray touching that block's face, not a block standing in the way. In world units it is the
     * fraction of the ray's length, so a two-hundred-unit ray tolerates a fifth of a unit.
     */
    private static final float OCCLUSION_ENDPOINT_TOLERANCE = 1e-3f;

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
        if (MapQueries.lineBlocked(map, blast.x(), blast.y(), targetX, targetY, Blocking.BLAST)) {
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
            if (MapQueries.lineBlocked(map, blast.x(), blast.y(), targetX, targetY, Blocking.BLAST)) {
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

    /**
     * Damage one block takes from one blast, measured at the block's nearest point to the
     * detonation.
     *
     * <p>Measured at the nearest face rather than the centre because a blast is a volume arriving
     * at a surface: a grenade lying against a crate is touching it, and a detonation just outside a
     * thick wall's face must not read as far away because the wall's centre is. Occlusion is the
     * shared {@link Blocking#BLAST} column with the target's own rectangle excluded — the segment
     * ends inside the block being measured, and a block cannot shadow itself.
     */
    public static float damageAtBlock(Blast blast, MapBlock block, ArenaMap map) {
        Objects.requireNonNull(blast, "blast");
        Objects.requireNonNull(block, "block");
        Rect rect = block.rect();
        float nearX = rect.clampX(blast.x());
        float nearY = rect.clampY(blast.y());
        float distance = distance(blast.x(), blast.y(), nearX, nearY);
        if (distance >= blast.radius()) {
            return 0f;
        }
        if (occludedExcluding(map, blast.x(), blast.y(), rect, nearX, nearY)) {
            return 0f;
        }
        return falloff(blast.maxDamage(), distance, blast.radius());
    }

    /**
     * Applies one blast to every block the live map can lose, in map order.
     *
     * <p>{@link MaterialTable#takesDamage} is what decides which blocks are asked: a wall's kind is
     * permanent and a stone surface refuses nothing but blasts, so a wall never enters the loop —
     * a blast resolves nothing against the arena's stone, and only the map's authored breakables
     * and panes can answer it. Each block is damaged at most once per blast by construction.
     *
     * @return the blocks that left the map, in the order they broke
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

    /**
     * True when something other than {@code target} stands between the detonation and
     * {@code (toX, toY)} on the shared {@link Blocking#BLAST} column.
     *
     * <p>The target's own rectangle is skipped: the measured point is inside the block being
     * asked about, so the block would otherwise always shadow itself. The entry fraction is
     * required to be strictly inside the segment — a ray that only grazes another block's face at
     * its far end is contact, not cover — with a small tolerance so a shared face between two
     * neighbouring blocks does not read as occlusion.
     */
    static boolean occludedExcluding(
            ArenaMap map, float fromX, float fromY, Rect target, float toX, float toY) {
        if (map == null) {
            return false;
        }
        for (Rect rect : map.blockers(Blocking.BLAST)) {
            if (rect.equals(target)) {
                continue;
            }
            float entry = Geometry.segmentAabbEntry(fromX, fromY, toX, toY, rect);
            if (entry >= 0f && entry < 1f - OCCLUSION_ENDPOINT_TOLERANCE) {
                return true;
            }
        }
        return false;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
