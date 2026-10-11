package io.github.skystrike.server.combat;

import io.github.skystrike.server.fx.EffectSink;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.map.BlockBreak;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.MapBlock;
import io.github.skystrike.shared.map.MapDamageState;
import io.github.skystrike.shared.map.MaterialTable;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.MeleeDefinition;
import java.util.Collection;

/**
 * Server-side melee resolution (mechanics §5.2): an arc test in front of the attacker, damage
 * through {@link DamageService} so death and the kill feed exist once, and knockback as a real
 * physics impulse — a velocity change, not a scripted displacement. A bat swung at an airborne
 * enemy genuinely launches them; whether that is off a ledge or into a fire is between the
 * players.
 *
 * <p>Geometry: the arc is a circle of the weapon's range around the attacker's centre, cut to a
 * ±{@value WeaponConfig#MELEE_ARC_HALF_ANGLE_DEGREES}° wedge around the aim. The impulse points
 * from the attacker to the victim — hit things go where you hit them — with the aim direction as
 * the fallback for a victim standing exactly on top of the attacker.
 *
 * <p>Knockback is applied only when the victim survives the hit. {@link DamageService} zeroes a
 * dead player's velocity, and the dead drop out of the simulation rather than sailing off as
 * ragdolls — that is a later phase's indulgence.
 *
 * <p>A swing lands on the map as well as on bodies when a destruction state is wired: the
 * same arc decides which blocks it covers, the shared table decides which surfaces accept a
 * melee hit, and a break is reported as the material's own impact. Nothing is repaired.
 */
public final class MeleeSystem {

    /** Position overlap below which direction-to-target is meaningless and the aim is used. */
    private static final float OVERLAP_EPSILON = 1e-4f;

    /**
     * The per-round destruction state, when a live match is being simulated. Null in the
     * geometry-only use the combat tests make of this class, where there is nothing to hit but
     * bodies.
     */
    private MapDamageState destruction;

    /** Where a block breaking under the swing is seen. Null in tests; presentation only. */
    private EffectSink effectSink;

    /**
     * Wires the live destruction state: a swing then lands on the blocks its arc covers, at
     * {@link DamageSource#MELEE}'s retention. Null detaches.
     */
    public void setDestruction(MapDamageState destruction) {
        this.destruction = destruction;
    }

    /** Installs the effect sink. Null detaches; safe to call more than once. */
    public void setEffectSink(EffectSink effectSink) {
        this.effectSink = effectSink;
    }

    /**
     * Angular boundary slack. Trigonometry through float30-something degrees does not reproduce
     * the exact half-angle at the wedge edge ({@code shortestDelta} can land a rounding hair
     * past it), so "inclusive" is implemented with a tolerance, like every other boundary in
     * this codebase.
     */
    private static final float ANGLE_BOUNDARY_EPSILON_DEGREES = 1e-3f;

    /**
     * Resolves one swing.
     *
     * @param targets every candidate victim in the match (the attacker is skipped by id)
     * @return how many players the swing connected with
     */
    public int swing(
            Player attacker,
            MeleeDefinition melee,
            Collection<? extends Player> targets,
            DamageService damage) {

        if (attacker == null || !attacker.alive || melee == null || targets == null || damage == null) {
            return 0;
        }

        float originX = attacker.centerX();
        float originY = attacker.centerY();
        int hits = 0;

        for (Player target : targets) {
            if (target == null || target.id == attacker.id || !target.alive) {
                continue;
            }
            if (!DamageService.canDamage(attacker, target)) {
                continue;
            }
            float targetX = target.centerX();
            float targetY = target.centerY();
            if (!inArc(originX, originY, attacker.aimAngle, targetX, targetY, melee.range())) {
                continue;
            }

            DamageService.DamageResult result = damage.applyWithSource(
                attacker,
                attacker.id,
                target,
                melee.damage(),
                HitZone.BODY,
                melee.id().wireId(),
                targetX,
                targetY,
                0f,
                originX,
                originY);
            if (result == null) {
                continue;
            }
            hits++;

            if (target.alive) {
                float dx = targetX - originX;
                float dy = targetY - originY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance > OVERLAP_EPSILON) {
                    target.vx += dx / distance * melee.knockback();
                    target.vy += dy / distance * melee.knockback();
                } else {
                    float aimRadians = Angles.toRadians(attacker.aimAngle);
                    target.vx += (float) Math.cos(aimRadians) * melee.knockback();
                    target.vy += (float) Math.sin(aimRadians) * melee.knockback();
                }
            }
        }
        damageBlocks(attacker, melee);
        return hits;
    }

    /**
     * Applies the swing to the blocks its arc covers.
     *
     * <p>Range is measured to the block's <b>nearest point</b>, not its centre: a body is measured
     * at its centre because it is small, while a wall's centre can be far outside a swing that
     * lands square on its face. The arc is otherwise the whole test, exactly as it is for a body —
     * occluding blocks and not bodies would make the same swing miss one thing it visibly reaches
     * and hit another.
     */
    private void damageBlocks(Player attacker, MeleeDefinition melee) {
        if (destruction == null) {
            return;
        }
        float originX = attacker.centerX();
        float originY = attacker.centerY();
        for (MapBlock block : destruction.liveMap().blocks()) {
            if (!MaterialTable.takesDamage(block, DamageSource.MELEE)) {
                continue;
            }
            Rect rect = block.rect();
            float nearX = rect.clampX(originX);
            float nearY = rect.clampY(originY);
            if (!inArc(originX, originY, attacker.aimAngle, nearX, nearY, melee.range())) {
                continue;
            }
            BlockBreak broke =
                destruction.damage(block, DamageSource.MELEE, melee.damage(), nearX, nearY);
            if (broke != null && effectSink != null) {
                effectSink.emit(new EffectSpawn(
                    broke.effect(), broke.x(), broke.y(), attacker.aimAngle, 1f));
            }
        }
    }

    /**
     * The arc test, kept public and pure so both the swing and its tests speak about exactly the
     * same wedge: within {@code range} of the origin (boundary inclusive) and within the
     * half-angle of the aim (boundary inclusive). A target all but overlapping the attacker is
     * always in the arc — at that distance the angle is noise.
     */
    public static boolean inArc(
            float originX,
            float originY,
            float aimAngleDegrees,
            float targetX,
            float targetY,
            float range) {
        float dx = targetX - originX;
        float dy = targetY - originY;
        if (dx * dx + dy * dy > range * range) {
            return false;
        }
        if (dx * dx + dy * dy < 1f) {
            return true; // standing on the blade: past the point where angles mean anything
        }
        float toTarget = Angles.ofVector(dx, dy);
        float delta = Math.abs(Angles.shortestDelta(aimAngleDegrees, toTarget));
        return delta <= WeaponConfig.MELEE_ARC_HALF_ANGLE_DEGREES + ANGLE_BOUNDARY_EPSILON_DEGREES;
    }
}
