package io.github.skystrike.shared.combat;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;

/**
 * Which zone a round landed in (mechanics §4.1).
 *
 * <p>The head is the top 28% of <b>current</b> height, never a fixed number of units. That one
 * choice is what makes crouching a real defensive option: a crouched player is 30 units tall, so
 * the head zone starts 21.6 units off the floor instead of 36, and it shrinks from 14 units of
 * height to 8.4.
 */
public final class HitZoneMath {

    private HitZoneMath() {
    }

    /** World height at which the head zone starts for a player of {@code height} standing at {@code footY}. */
    public static float headZoneBottom(float footY, float height) {
        return footY + height * CombatConfig.HEAD_ZONE_START_FRACTION;
    }

    /** Head zone of {@code player} at their current stance. */
    public static float headZoneBottom(Player player) {
        return headZoneBottom(player.y, player.currentHeight());
    }

    /**
     * Resolves a hit from the impact height alone.
     *
     * @param impactY world Y of the impact
     * @param footY   world Y of the player's feet (the player's position origin)
     * @param height  the player's current height
     */
    public static HitZone resolve(float impactY, float footY, float height) {
        if (height <= 0f) {
            return HitZone.BODY;
        }
        return impactY >= headZoneBottom(footY, height) ? HitZone.HEAD : HitZone.BODY;
    }

    /** Resolves a hit against a live player's current stance. */
    public static HitZone resolve(float impactY, Player target) {
        return resolve(impactY, target.y, target.currentHeight());
    }

    /**
     * Resolves a hit against a live player including their gadget state: the Phase 6 entry
     * point. The rear fuel-tank zone exists exactly while the target's loadout carries a worn,
     * intact tank ({@link io.github.skystrike.shared.model.PlayerLoadout#hasFuelTank()}); with
     * no tank — or a detonated one — this is identical to {@link #resolve(float, Player)}.
     */
    public static HitZone resolve(float impactX, float impactY, Player target) {
        boolean hasFuelTank = target.loadout != null && target.loadout.hasFuelTank();
        return resolveWithGadget(
            impactX, impactY, target.y, target.x, target.currentHeight(),
            target.isFacingRight(), hasFuelTank);
    }

    /**
     * Resolves a hit including the rear-mounted fuel tank.
     *
     * <p>This is the single hit-zone implementation: Phase 3 always passed {@code false} for
     * {@code hasFuelTank}, and the Phase 6 gadget state now feeds the flag through
     * {@link #resolve(float, float, Player)} rather than through a second implementation.
     *
     * <p><b>The rear-facing condition:</b> the tank occupies a narrow strip on the side of the
     * hitbox the player's aim points <i>away</i> from — aiming right puts the tank on the left
     * edge, and vice versa — between 30% and 72% of current height, so it sits under the head
     * zone and behind the body. A head hit always stays a head hit.
     *
     * @param facingRight the direction the player's aim points, which is where the tank is not
     */
    public static HitZone resolveWithGadget(
            float impactX,
            float impactY,
            float footY,
            float centreX,
            float height,
            boolean facingRight,
            boolean hasFuelTank) {
        HitZone base = resolve(impactY, footY, height);
        if (!hasFuelTank || base == HitZone.HEAD) {
            return base;
        }

        float strip = PlayerConfig.WIDTH * CombatConfig.FUEL_TANK_WIDTH_FRACTION;
        float rearEdge = facingRight ? centreX - PlayerConfig.WIDTH / 2f : centreX + PlayerConfig.WIDTH / 2f;
        boolean inRearStrip = facingRight
            ? impactX <= rearEdge + strip
            : impactX >= rearEdge - strip;

        boolean inTankBand = impactY >= footY + height * CombatConfig.FUEL_TANK_BOTTOM_FRACTION
            && impactY <= footY + height * CombatConfig.FUEL_TANK_TOP_FRACTION;

        return inRearStrip && inTankBand ? HitZone.FUEL_TANK : base;
    }

    /** Damage multiplier for a zone. Mirrors {@link HitZone#damageMultiplier()}. */
    public static float multiplier(HitZone zone) {
        return zone == null ? CombatConfig.BODY_DAMAGE_MULTIPLIER : zone.damageMultiplier();
    }

    /** Damage after the zone multiplier. */
    public static float applyZone(float damage, HitZone zone) {
        return damage * multiplier(zone);
    }
}
