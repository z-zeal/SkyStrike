package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;

/**
 * The directional damage vignette's maths (playable build plan M4 §5), driven by
 * {@link PacketDamageEvent}.
 *
 * <p>A damage event already carries where the round landed, so the direction to tint is the
 * bearing from the victim's centre to that point — not a guess from the attacker's last known
 * position, which a fog-of-war game frequently does not have. Events whose impact point is on
 * top of the victim (fall damage, a fuel tank going up underneath them, point-blank melee)
 * have no meaningful bearing; {@link #isDirectional} says so and the widget then flashes the
 * whole frame instead of one edge.
 *
 * <p>Intensity is the share of the health bar the hit took, floored so even a graze registers,
 * and the flash decays quadratically so it is unmistakable at the moment of the hit and gone
 * before the next engagement.
 */
public final class DamageVignetteMath {

    /** How long one hit's tint lives, seconds. */
    public static final float DURATION_SECONDS = 0.9f;

    /** Damage of this share of the health bar saturates the tint. */
    public static final float SATURATING_DAMAGE_FRACTION = 0.35f;

    /** Even the smallest hit shows at least this much. */
    public static final float MIN_INTENSITY = 0.25f;

    /** Impacts closer than this to the victim's centre carry no usable bearing. */
    public static final float DIRECTIONAL_EPSILON = 0.5f;

    private DamageVignetteMath() {
    }

    /**
     * Bearing from the victim to the impact, in degrees, wrapped to (−180, 180]: 0 is screen
     * right, 90 straight up — the same convention as {@code Player.aimAngle}.
     */
    public static float directionDegrees(float victimX, float victimY, float impactX, float impactY) {
        return Angles.wrap(Angles.ofVector(impactX - victimX, impactY - victimY));
    }

    /** The bearing for one event against one victim. */
    public static float directionDegrees(PacketDamageEvent damage, Player victim) {
        if (damage == null || victim == null) {
            return 0f;
        }
        return directionDegrees(victim.centerX(), victim.centerY(), damage.x, damage.y);
    }

    /** False when the impact is effectively inside the victim, so no single edge is to blame. */
    public static boolean isDirectional(PacketDamageEvent damage, Player victim) {
        if (damage == null || victim == null) {
            return false;
        }
        float dx = damage.x - victim.centerX();
        float dy = damage.y - victim.centerY();
        return dx * dx + dy * dy > DIRECTIONAL_EPSILON * DIRECTIONAL_EPSILON;
    }

    /**
     * How hard the hit reads, 0–1: {@link #MIN_INTENSITY} for a graze up to 1 for a hit taking
     * {@link #SATURATING_DAMAGE_FRACTION} of the bar or more. Zero and negative damage show
     * nothing at all.
     */
    public static float intensity(float amount) {
        if (!(amount > 0f)) {
            return 0f;
        }
        float saturating = CombatConfig.MAX_HEALTH * SATURATING_DAMAGE_FRACTION;
        float share = saturating <= 0f ? 1f : amount / saturating;
        return Math.min(1f, Math.max(MIN_INTENSITY, share));
    }

    /**
     * Fade curve over the life of one tint: 1 at the instant of the hit, 0 at
     * {@link #DURATION_SECONDS} and after. Quadratic, so it falls away fast but never snaps.
     */
    public static float fade(float ageSeconds) {
        if (!(ageSeconds > 0f)) {
            return ageSeconds < 0f ? 0f : 1f;
        }
        if (ageSeconds >= DURATION_SECONDS) {
            return 0f;
        }
        float remaining = 1f - ageSeconds / DURATION_SECONDS;
        return remaining * remaining;
    }

    /** Intensity and fade together: what the widget actually multiplies its tint alpha by. */
    public static float alpha(float amount, float ageSeconds) {
        return intensity(amount) * fade(ageSeconds);
    }

    /**
     * Blends a new hit into a tint already on screen. Two hits from opposite sides do not
     * cancel into a straight-ahead arrow: the stronger contribution wins the bearing, which is
     * the one a player must turn toward.
     *
     * @return the bearing to draw after the new hit lands
     */
    public static float mergeDirection(
            float currentDegrees, float currentAlpha, float incomingDegrees, float incomingAlpha) {
        return incomingAlpha >= currentAlpha ? Angles.wrap(incomingDegrees) : Angles.wrap(currentDegrees);
    }
}
