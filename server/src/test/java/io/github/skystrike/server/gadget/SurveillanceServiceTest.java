package io.github.skystrike.server.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The server's surveillance view transitions (mechanics §9): the view-cycle edge advances
 * self → drone → camera → self against the devices the player actually owns, and the exit edge
 * always lands on the body's own eyes.
 */
class SurveillanceServiceTest {

    private final ArenaMap arena = ArenaMap.standard();
    private DroneSystem drones;
    private CameraSystem cameras;
    private SurveillanceService surveillance;
    private Player player;

    @BeforeEach
    void setUp() {
        drones = new DroneSystem(arena);
        cameras = new CameraSystem(arena);
        surveillance = new SurveillanceService(drones, cameras);
        player = new Player(1, "Nova", 0, 400f, 1200f);
    }

    @Test
    @DisplayName("the cycle edge advances self → drone → camera → self as devices become available")
    void cycleFollowsAvailability() {
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.SELF, player.surveillance(), "nothing deployed: a no-op");

        drones.press(player);
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.DRONE, player.surveillance());

        // A flying camera is not available; only a stuck one is.
        player.aimAngle = 0f;
        player.loadout.setGadgets(io.github.skystrike.shared.gadget.GadgetId.DRONE,
            io.github.skystrike.shared.gadget.GadgetId.CAMERA);
        cameras.press(player);
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.SELF, player.surveillance(),
            "a flying camera cannot be cycled to");

        CameraEntity camera = cameras.byOwner(player.id);
        camera.stuck = true;
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.CAMERA, player.surveillance());

        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.SELF, player.surveillance(), "camera wraps to self");
    }

    @Test
    @DisplayName("the exit edge returns to the body from any view, and is a no-op on self")
    void exitAlwaysLandsOnSelf() {
        drones.press(player);
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.DRONE, player.surveillance());

        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_EXIT);
        assertEquals(SurveillanceView.SELF, player.surveillance());

        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_EXIT);
        assertEquals(SurveillanceView.SELF, player.surveillance(), "already on self");
    }

    @Test
    @DisplayName("a drone owned by someone else is not this player's view")
    void anotherPlayersDevicesAreNotAvailable() {
        Player other = new Player(2, "Rook", 1, 500f, 1200f);
        other.loadout.setGadgets(io.github.skystrike.shared.gadget.GadgetId.DRONE,
            io.github.skystrike.shared.gadget.GadgetId.NONE);
        drones.press(other);

        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertEquals(SurveillanceView.SELF, player.surveillance(),
            "someone else's drone does not count");
    }

    @Test
    @DisplayName("degenerate edges change nothing")
    void degenerateEdgesAreNoOps() {
        surveillance.applyViewAction(null, PacketPlayerInput.VIEW_CYCLE);
        surveillance.applyViewAction(player, PacketPlayerInput.NO_VIEW_ACTION);
        surveillance.applyViewAction(player, 999);
        assertEquals(SurveillanceView.SELF, player.surveillance());
    }

    @Test
    @DisplayName("the lock flag reads the player's view")
    void lockFlagReadsTheView() {
        assertFalse(SurveillanceService.isLocked(player));
        assertFalse(SurveillanceService.isLocked(null));

        drones.press(player);
        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_CYCLE);
        assertTrue(SurveillanceService.isLocked(player));

        surveillance.applyViewAction(player, PacketPlayerInput.VIEW_EXIT);
        assertFalse(SurveillanceService.isLocked(player));
    }

    @Test
    @DisplayName("the drone and camera systems answer availability by owner")
    void availabilityQueries() {
        assertNull(drones.byOwner(player.id));
        assertNull(cameras.byOwnerStuck(player.id));

        drones.press(player);
        DroneEntity drone = drones.byOwner(player.id);
        assertTrue(drones.ownedBy(player.id).contains(drone));
        assertEquals(1, drones.ownedBy(player.id).size());

        player.aimAngle = 0f;
        player.loadout.setGadgets(io.github.skystrike.shared.gadget.GadgetId.DRONE,
            io.github.skystrike.shared.gadget.GadgetId.CAMERA);
        cameras.press(player);
        assertNull(cameras.byOwnerStuck(player.id), "still flying");
        CameraEntity camera = cameras.byOwner(player.id);
        camera.stuck = true;
        assertEquals(camera, cameras.byOwnerStuck(player.id));
    }
}
