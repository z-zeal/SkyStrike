package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.PlayerConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.physics.PlayerMotion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Health out of 150, fuel out of the tank the simulation actually clamps against. */
class HudVitalsTest {

    private Player player;

    @BeforeEach
    void setUp() {
        player = new Player(1, "Nova", 0, 100f, 100f);
    }

    @Test
    @DisplayName("health is a fraction of the 150 maximum and clamps at both ends")
    void health() {
        assertEquals(150f, HudVitals.maxHealth());
        assertEquals(1f, HudVitals.healthFraction(player), 1e-4f);

        player.health = 75f;
        assertEquals(0.5f, HudVitals.healthFraction(player), 1e-4f);
        assertFalse(HudVitals.lowHealth(player));

        player.health = 30f;
        assertEquals(0.2f, HudVitals.healthFraction(player), 1e-4f);
        assertTrue(HudVitals.lowHealth(player));

        player.health = -20f;
        assertEquals(0f, HudVitals.healthFraction(player), 1e-4f);
        assertEquals(0f, HudVitals.healthFraction(null));
    }

    @Test
    @DisplayName("the fuel denominator is the simulation's own capacity, fuel tank included")
    void fuelCapacityMatchesTheSimulation() {
        assertEquals(PlayerConfig.MAX_FUEL, HudVitals.fuelCapacity(player), 1e-4f);
        assertEquals(PlayerMotion.fuelCapacity(player), HudVitals.fuelCapacity(player), 1e-4f);

        player.loadout.setGadgets(GadgetId.FUEL_TANK, null);
        assertEquals(175f, HudVitals.fuelCapacity(player), 1e-4f);
        assertEquals(PlayerMotion.fuelCapacity(player), HudVitals.fuelCapacity(player), 1e-4f,
            "one capacity rule, or a full tank draws a part-full bar");

        player.fuel = 175f;
        assertEquals(1f, HudVitals.fuelFraction(player), 1e-4f);
        player.fuel = 100f;
        assertEquals(100f / 175f, HudVitals.fuelFraction(player), 1e-4f);
    }

    @Test
    @DisplayName("the recharge-grounded rule: recharging on the ground, stalled in the air")
    void rechargeGroundedRule() {
        player.fuel = 40f;
        player.grounded = true;
        player.jetpacking = false;
        assertTrue(HudVitals.fuelRecharging(player));
        assertFalse(HudVitals.fuelStalled(player));
        assertEquals(3f, HudVitals.secondsToFull(player), 1e-4f);

        player.jetpacking = true;
        assertFalse(HudVitals.fuelRecharging(player), "burning while grounded is not recharging");

        player.jetpacking = false;
        player.grounded = false;
        assertFalse(HudVitals.fuelRecharging(player));
        assertTrue(HudVitals.fuelStalled(player), "airborne fuel does not come back");
        assertEquals(0f, HudVitals.secondsToFull(player), "nothing to promise while airborne");

        player.grounded = true;
        player.fuel = PlayerConfig.MAX_FUEL;
        assertFalse(HudVitals.fuelRecharging(player), "a full tank is not recharging");
        assertFalse(HudVitals.fuelStalled(player));
        assertEquals(0f, HudVitals.secondsToFull(player));
    }

    @Test
    @DisplayName("the fuel readout quotes burn time and warns near empty")
    void fuelWarnings() {
        player.fuel = 100f;
        assertEquals(5f, HudVitals.secondsOfThrust(player), 1e-4f);
        assertFalse(HudVitals.lowFuel(player));

        player.fuel = 25f;
        assertEquals(1.25f, HudVitals.secondsOfThrust(player), 1e-4f);
        assertTrue(HudVitals.lowFuel(player));

        player.fuel = 0f;
        assertEquals(0f, HudVitals.secondsOfThrust(player));
        assertEquals(0f, HudVitals.fuelFraction(player), 1e-4f);
    }
}
