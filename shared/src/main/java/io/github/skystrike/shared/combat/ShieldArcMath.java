package io.github.skystrike.shared.combat;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.ShieldState;

/**
 * Front/rear shield-arc absorption (mechanics §7.3, structure plan
 * {@code shared/combat/ShieldArcMath}).
 *
 * <p>Equipped, the shield covers a {@link GadgetConfig#SHIELD_FRONT_ARC_DEGREES} arc centred on
 * the aim direction; stowed, it covers a {@link GadgetConfig#SHIELD_REAR_ARC_DEGREES} arc
 * centred directly behind it. Broken, it covers nothing. Angles go through
 * {@link Angles#shortestDelta}, so a hit arriving at −179° against an aim of +179° measures 2°
 * apart, not 358° — wrapping around ±180 can never flip the answer.
 *
 * <p>Absorbed damage comes off durability instead of health. One hit can both drain the last
 * of the pool and spill the remainder into health: the plan specifies the subtraction and the
 * permanent break at zero but not the overflow, so the overflow <b>passing through to health is
 * provisional</b> — chosen because the alternative (a 1-durability shield soaking a 126-damage
 * sniper round for free) makes the last sliver of pool worth more than the whole shield.
 *
 * <p>This lives in {@code shared} so the server's authoritative application and the client's
 * predicted feedback run the identical arithmetic and can never disagree.
 */
public final class ShieldArcMath {

    private ShieldArcMath() {
    }

    /**
     * The outcome of one hit against a shield.
     *
     * @param absorbed        damage taken by the shield's durability pool
     * @param healthDamage    damage left over for the normal health pipeline
     * @param durabilityAfter pool remaining after the hit
     * @param broke           true when this hit emptied the pool
     */
    public record Absorption(float absorbed, float healthDamage, float durabilityAfter, boolean broke) {

        /** A hit the shield did not touch: everything continues to health. */
        public static Absorption none(float damage, float durability) {
            return new Absorption(0f, Math.max(0f, damage), Math.max(0f, durability), false);
        }

        public boolean absorbedAnything() {
            return absorbed > 0f;
        }
    }

    /**
     * Whether a shield in {@code state} covers a hit arriving from {@code incomingDegrees}.
     *
     * @param state           the shield's current state; {@code null} and BROKEN cover nothing
     * @param facingDegrees   the wearer's aim direction, degrees
     * @param incomingDegrees direction <b>from the wearer to the damage source</b>, degrees —
     *                        for a bullet this is the direction back along its flight
     */
    public static boolean covers(ShieldState state, float facingDegrees, float incomingDegrees) {
        if (state == null || !state.absorbs()) {
            return false;
        }
        float arcCentre = state == ShieldState.EQUIPPED ? facingDegrees : facingDegrees + 180f;
        float halfArc = (state == ShieldState.EQUIPPED
            ? GadgetConfig.SHIELD_FRONT_ARC_DEGREES
            : GadgetConfig.SHIELD_REAR_ARC_DEGREES) / 2f;
        return Math.abs(Angles.shortestDelta(arcCentre, incomingDegrees)) <= halfArc;
    }

    /**
     * Splits one covered hit between durability and health. A shield with nothing left in the
     * pool absorbs nothing — a broken shield's durability is zero, so it degrades correctly
     * even if the caller forgot to check the state.
     */
    public static Absorption absorb(float damage, float durability) {
        if (damage <= 0f) {
            return Absorption.none(0f, durability);
        }
        if (durability <= 0f) {
            return Absorption.none(damage, 0f);
        }
        float absorbed = Math.min(damage, durability);
        float remainingPool = durability - absorbed;
        return new Absorption(absorbed, damage - absorbed, remainingPool, remainingPool <= 0f);
    }

    /**
     * The full resolution in one call: arc test, then the durability split. A hit outside the
     * protected arc — or against a broken shield — passes through untouched.
     */
    public static Absorption resolve(
            ShieldState state,
            float facingDegrees,
            float incomingDegrees,
            float damage,
            float durability) {
        if (!covers(state, facingDegrees, incomingDegrees)) {
            return Absorption.none(damage, durability);
        }
        return absorb(damage, durability);
    }
}
