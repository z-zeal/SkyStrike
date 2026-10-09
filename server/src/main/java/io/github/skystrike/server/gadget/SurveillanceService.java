package io.github.skystrike.server.gadget;

import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * Server-side ownership of the surveillance view (mechanics §7, §9): applies the view-cycle and
 * exit-surveillance edges to the player's authoritative {@code surveillanceView}, and answers the
 * lock question the movement and loadout systems enforce.
 *
 * <p>The lock itself is deliberately enforced in the shared motion and loadout code
 * ({@code PlayerMotion} ignores the body's movement intent while locked; the loadout tick blocks
 * weapons and slots), so client prediction and server authority freeze the body identically.
 * What this service owns is the <b>transition</b>: every edge the client sends is applied here,
 * on the tick thread, against the devices the player actually has — cycling skips whatever is
 * not deployed, and exit always lands on the player's own eyes.
 */
public final class SurveillanceService {

    private final DroneSystem drones;
    private final CameraSystem cameras;

    public SurveillanceService(DroneSystem drones, CameraSystem cameras) {
        this.drones = drones;
        this.cameras = cameras;
    }

    /**
     * Applies one view-action edge. {@link PacketPlayerInput#VIEW_CYCLE} advances
     * self → drone → camera → self, skipping devices the player does not have;
     * {@link PacketPlayerInput#VIEW_EXIT} returns to the body unconditionally.
     */
    public void applyViewAction(Player player, int viewAction) {
        if (player == null) {
            return;
        }
        switch (viewAction) {
            case PacketPlayerInput.VIEW_CYCLE -> {
                boolean droneAvailable = drones != null && drones.byOwner(player.id) != null;
                boolean cameraAvailable = cameras != null && cameras.byOwnerStuck(player.id) != null;
                player.surveillanceView = SurveillanceView.cycle(
                        player.surveillance(), droneAvailable, cameraAvailable)
                    .ordinal();
            }
            case PacketPlayerInput.VIEW_EXIT -> player.surveillanceView = SurveillanceView.SELF.ordinal();
            default -> {
                // NO_VIEW_ACTION and anything a newer protocol invents: nothing to apply.
            }
        }
    }

    /** True while the player's body is locked to a drone or camera. */
    public static boolean isLocked(Player player) {
        return player != null && player.isSurveillanceLocked();
    }
}
