package io.github.skystrike.server.combat;

import io.github.skystrike.shared.config.WeaponConfig;
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
 */
public final class MeleeSystem {

    /** Position overlap below which direction-to-target is meaningless and the aim is used. */
    private static final float OVERLAP_EPSILON = 1e-4f;

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

            DamageService.DamageResult result = damage.apply(
                attacker,
                attacker.id,
                target,
                melee.damage(),
                HitZone.BODY,
                melee.id().wireId(),
                targetX,
                targetY,
                0f);
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
        return hits;
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
