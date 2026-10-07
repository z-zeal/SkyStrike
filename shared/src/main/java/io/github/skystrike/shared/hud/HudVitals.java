package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.physics.PlayerMotion;

/**
 * The read model behind the health and fuel bars (playable build plan M4 §5).
 *
 * <p>Health is the 150 of {@link CombatConfig#MAX_HEALTH}; fuel is the 100 of
 * {@link PlayerConfig#MAX_FUEL}, except that a worn fuel tank enlarges the tank — so the
 * denominator comes from {@link PlayerMotion#fuelCapacity(Player)}, the same function the
 * simulation clamps against, never from a second copy of the multiplier.
 *
 * <p><b>The recharge-grounded rule</b> is the one piece of fuel behaviour a player cannot see
 * from the number alone: fuel only comes back while standing on something
 * ({@code PlayerMotion} step 6). The bar therefore distinguishes <i>recharging</i> (grounded,
 * not full) from <i>stalled</i> (airborne, not full) so a player in the air understands why the
 * bar is not moving, and {@link #secondsToFull(Player)} is only ever quoted for the first.
 */
public final class HudVitals {

    /** Below this fraction the fuel bar turns to its warning colour. */
    public static final float LOW_FUEL_FRACTION = 0.25f;

    /** Below this fraction the health bar turns to its warning colour. */
    public static final float LOW_HEALTH_FRACTION = 0.30f;

    private HudVitals() {
    }

    public static float maxHealth() {
        return CombatConfig.MAX_HEALTH;
    }

    /** Health as 0–1 of the 150 maximum. A dead or absent player reads 0. */
    public static float healthFraction(Player player) {
        if (player == null) {
            return 0f;
        }
        return clamp01(player.health / CombatConfig.MAX_HEALTH);
    }

    /** This player's tank size, fuel-tank gadget included. */
    public static float fuelCapacity(Player player) {
        return player == null ? PlayerConfig.MAX_FUEL : PlayerMotion.fuelCapacity(player);
    }

    /** Fuel as 0–1 of {@link #fuelCapacity(Player)}. */
    public static float fuelFraction(Player player) {
        if (player == null) {
            return 0f;
        }
        float capacity = fuelCapacity(player);
        return capacity <= 0f ? 0f : clamp01(player.fuel / capacity);
    }

    /** True while fuel is actually coming back: on the ground, not burning, not already full. */
    public static boolean fuelRecharging(Player player) {
        return player != null
            && player.grounded
            && !player.jetpacking
            && player.fuel < fuelCapacity(player);
    }

    /**
     * True when fuel is missing and cannot come back yet, because the player is airborne. This
     * is the recharge-grounded rule as the HUD states it: land to refuel.
     */
    public static boolean fuelStalled(Player player) {
        return player != null && !player.grounded && player.fuel < fuelCapacity(player);
    }

    /** Seconds of standing still to fill the tank, 0 when it is full or cannot recharge yet. */
    public static float secondsToFull(Player player) {
        if (!fuelRecharging(player)) {
            return 0f;
        }
        float missing = fuelCapacity(player) - player.fuel;
        return missing <= 0f ? 0f : missing / PlayerConfig.FUEL_RECHARGE_RATE;
    }

    /** Seconds of continuous thrust left in the tank, 0 when empty. */
    public static float secondsOfThrust(Player player) {
        if (player == null || player.fuel <= 0f) {
            return 0f;
        }
        return player.fuel / PlayerConfig.FUEL_BURN_RATE;
    }

    public static boolean lowHealth(Player player) {
        return player != null && player.alive && healthFraction(player) <= LOW_HEALTH_FRACTION;
    }

    public static boolean lowFuel(Player player) {
        return player != null && fuelFraction(player) <= LOW_FUEL_FRACTION;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.min(1f, Math.max(0f, value));
    }
}
