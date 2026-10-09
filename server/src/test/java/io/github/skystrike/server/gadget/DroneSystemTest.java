package io.github.skystrike.server.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authoritative drone lifecycle (mechanics §7.1): deploy, pilot, exit, motion, destruction
 * and the owner-death sweep. The press transitions come from the shared {@code GadgetPress}
 * machine; what is asserted here is what the server does with them.
 */
class DroneSystemTest {

    private static final float DT = 1f / 60f;

    private final ArenaMap arena = ArenaMap.standard();
    private DroneSystem drones;
    private Player owner;

    @BeforeEach
    void setUp() {
        drones = new DroneSystem(arena);
        // Open sky, so the deployed drone and any fired round are never absorbed by geometry.
        owner = new Player(2, "Nova", 0, 400f, 1200f);
        owner.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
    }

    private static PlayerInput input(float moveX, boolean jump, boolean crouch, float aim) {
        return new PlayerInput(1L, moveX, jump, crouch, false, false, false, aim);
    }

    // --- Press ------------------------------------------------------------------------------------

    @Test
    @DisplayName("the first Q press deploys a drone above the owner and marks the slot active")
    void firstPressDeploys() {
        drones.press(owner);

        DroneEntity drone = drones.byOwner(owner.id);
        assertNotNull(drone, "a drone entity exists");
        assertTrue(owner.loadout.gadgetQ.active, "the slot reads deployed");
        assertEquals(SurveillanceView.SELF, owner.surveillance(), "deploying does not switch the view");
        assertEquals(owner.centerX(), drone.x, 1e-6f);
        assertEquals(
            owner.y + owner.currentHeight() + GadgetConfig.DRONE_SPAWN_OFFSET_Y,
            drone.y,
            1e-6f,
            "the drone deploys one reference unit above the owner");
        assertEquals(GadgetConfig.DRONE_HEALTH, drone.health, 1e-6f);
    }

    @Test
    @DisplayName("the second press pilots the drone; the third returns the view but keeps it deployed")
    void pressWalksDeployPilotExit() {
        drones.press(owner);
        drones.press(owner);
        assertEquals(SurveillanceView.DRONE, owner.surveillance());
        assertTrue(owner.loadout.gadgetQ.active);

        drones.press(owner);
        assertEquals(SurveillanceView.SELF, owner.surveillance());
        assertNotNull(drones.byOwner(owner.id), "the drone stays deployed after the pilot exits");
        assertTrue(owner.loadout.gadgetQ.active);
    }

    @Test
    @DisplayName("a broken slot, a dead owner and a stunned owner refuse the press")
    void refusedPresses() {
        owner.loadout.gadgetQ.applyDurabilityDamage(999f);
        drones.press(owner);
        assertNull(drones.byOwner(owner.id), "a broken slot deploys nothing");
        assertFalse(owner.loadout.gadgetQ.active);

        owner.loadout.gadgetQ.resetForRespawn();
        owner.alive = false;
        drones.press(owner);
        assertNull(drones.byOwner(owner.id), "a dead owner deploys nothing");

        owner.alive = true;
        owner.slowRemaining = 1f;
        drones.press(owner);
        assertNull(drones.byOwner(owner.id), "a stunned owner deploys nothing");
    }

    @Test
    @DisplayName("a press on a slot holding something else does nothing")
    void wrongSlotIsIgnored() {
        owner.loadout.setGadgets(GadgetId.SHIELD, GadgetId.NONE);
        drones.press(owner);
        assertNull(drones.byOwner(owner.id));
        assertFalse(owner.loadout.gadgetQ.active, "the shield's slot is not marked deployed");
    }

    // --- Motion -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a piloted drone flies with the input and its cone follows the aim")
    void pilotedDroneFliesWithInput() {
        drones.press(owner);
        drones.press(owner);

        float startX = drones.byOwner(owner.id).x;
        float startY = drones.byOwner(owner.id).y;
        for (int i = 0; i < 30; i++) {
            drones.stepOwned(owner, input(1f, true, false, 45f), DT);
        }
        DroneEntity drone = drones.byOwner(owner.id);
        assertTrue(drone.x > startX, "moveX steers the drone");
        assertTrue(drone.y > startY, "jump climbs");
        assertEquals(45f, drone.aimAngle, 1e-4f, "the cone follows the aim while piloting");
    }

    @Test
    @DisplayName("an unpiloted drone damps to a hover where it was left")
    void unpilotedDroneHovers() {
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.vx = 150f;

        drones.stepOwned(owner, input(1f, false, false, 0f), DT);

        assertTrue(drone.vx < 150f, "the stray velocity damps with no pilot");
        assertEquals(0f, drone.vy, 1e-6f);
    }

    @Test
    @DisplayName("the slot's durability mirrors the drone's health for the HUD")
    void durabilityMirrorsHealth() {
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.applyDamage(11f);

        drones.stepOwned(owner, input(0f, false, false, 0f), DT);

        assertEquals(GadgetConfig.DRONE_HEALTH - 11f, owner.loadout.gadgetQ.durability, 1e-6f);
    }

    // --- Sweep ------------------------------------------------------------------------------------

    @Test
    @DisplayName("a spent drone is destroyed: slot broken for the life, pilot returned to their eyes")
    void sweepDestroysSpentDrone() {
        drones.press(owner);
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.applyDamage(GadgetConfig.DRONE_HEALTH);

        drones.sweep(List.of(owner));

        assertNull(drones.byOwner(owner.id), "the entity is removed");
        GadgetSlot slot = owner.loadout.gadgetQ;
        assertTrue(slot.broken, "broken for the rest of the life");
        assertFalse(slot.active);
        assertEquals(0f, slot.durability, 1e-6f);
        assertEquals(SurveillanceView.SELF, owner.surveillance(),
            "a pilot loses the view with the drone");
    }

    @Test
    @DisplayName("a drone whose owner died is removed with them and does not mark the slot broken")
    void sweepRemovesDeadOwnersDrone() {
        drones.press(owner);
        drones.press(owner);
        owner.alive = false;

        drones.sweep(List.of(owner));

        assertNull(drones.byOwner(owner.id));
        assertFalse(owner.loadout.gadgetQ.active, "the deployed flag does not outlive the life");
        assertFalse(owner.loadout.gadgetQ.broken, "death is not destruction");
        assertEquals(SurveillanceView.SELF, owner.surveillance());
    }

    @Test
    @DisplayName("a drone whose owner left the match is removed")
    void sweepRemovesDisconnectedOwnersDrone() {
        drones.press(owner);
        drones.sweep(List.of());
        assertNull(drones.byOwner(owner.id));
    }

    // --- Bullets ----------------------------------------------------------------------------------

    @Test
    @DisplayName("a round lands on a drone and drains its health; the sweep destroys it")
    void bulletsDamageAndDestroyDrones() {
        drones.press(owner);
        DroneEntity drone = drones.byOwner(owner.id);
        drone.health = 5f;

        Player shooter = new Player(1, "Rook", 1, 300f, 1230f);
        shooter.aimAngle = 0f;
        BulletSystem bullets = new BulletSystem(arena);
        bullets.setGadgetSystems(drones, null);
        assertNotNull(bullets.spawn(shooter, WeaponId.IRON_CARBINE, 0f), "the muzzle is clear");

        for (int i = 0; i < 30 && drone.health > 0f; i++) {
            bullets.step(DT, List.of(shooter), null);
        }
        assertTrue(drone.health <= 0f, "the round reached the drone");
        assertEquals(1, bullets.gadgetImpactCount());

        drones.sweep(List.of(owner, shooter));
        assertNull(drones.byOwner(owner.id), "the sweep destroys the spent drone");
        assertTrue(owner.loadout.gadgetQ.broken);
    }
}
