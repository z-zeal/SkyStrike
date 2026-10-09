package io.github.skystrike.gameplay;

import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.DroneMotion;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetPress;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.math.Lerp;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Client-side surveillance: the predicted view, the piloted drone's local motion, and the
 * retransmission bookkeeping that keeps the input edges alive until the server acknowledges them.
 *
 * <p>The server owns every transition — the view rides on the player record and the lock is
 * enforced there — so this controller is prediction, in the exact shape {@link LoadoutController}
 * already uses: a key press applies to the predicted player immediately (the camera switches at
 * once), the edge rides every input packet with its birth sequence until the server's
 * last-processed sequence passes it, and each snapshot replays the unacknowledged edges on top
 * of the authoritative state. The state machines themselves are the shared ones
 * ({@link SurveillanceView#cycle}, {@link GadgetPress}), so prediction and authority cannot
 * disagree about what a press means — only about whether it has arrived yet.
 *
 * <p>While the local player pilots a drone, this controller also predicts that drone's motion with
 * the shared {@link DroneMotion}, so the view the camera follows and the cone the visibility pass
 * draws stay crisp at the frame rate instead of stepping at the snapshot rate.
 */
public final class SurveillanceController {

    /**
     * What the prediction resolves presses against: which of the player's own devices are in the
     * world. Built from the latest snapshot each time one arrives.
     */
    public record OwnedDevices(boolean droneLive, boolean cameraLive, boolean cameraReady) {

        public static final OwnedDevices NONE = new OwnedDevices(false, false, false);

        /** Scans snapshot device lists for the ones owned by {@code ownerId}. */
        public static OwnedDevices of(List<DroneEntity> drones, List<CameraEntity> cameras, int ownerId) {
            boolean droneLive = false;
            boolean cameraLive = false;
            boolean cameraReady = false;
            if (drones != null) {
                for (DroneEntity drone : drones) {
                    if (drone != null && drone.ownerId == ownerId) {
                        droneLive = true;
                        break;
                    }
                }
            }
            if (cameras != null) {
                for (CameraEntity camera : cameras) {
                    if (camera != null && camera.ownerId == ownerId) {
                        cameraLive = true;
                        if (camera.stuck) {
                            cameraReady = true;
                            break;
                        }
                    }
                }
            }
            return new OwnedDevices(droneLive, cameraLive, cameraReady);
        }
    }

    /** Where the camera looks while surveilling: the device's position, and the view's zoom. */
    public record ViewTarget(float x, float y, float zoom) {
    }

    /**
     * One view action awaiting its server acknowledgement. The result is stored, not re-resolved,
     * because the press machine is not idempotent — re-running it against the already-advanced
     * state would resolve the opposite transition.
     */
    private record PendingViewAction(int action, SurveillanceView result, long birthSequence) {
    }

    /** Presses waiting for acknowledgements, capped like the loadout controller's. */
    private static final int MAX_OUTSTANDING_VIEW_ACTIONS = 4;

    /** The predicted drone snaps to the authoritative one beyond this distance (squared). */
    private static final float DRONE_SNAP_DISTANCE_SQ = 16f;

    private final KeyBindings bindings;
    private final InputRouter router;
    private final Deque<PendingViewAction> outstanding = new ArrayDeque<>();

    private OwnedDevices devices = OwnedDevices.NONE;
    private DroneEntity predictedDrone;
    private CameraEntity ownedCamera;

    public SurveillanceController(KeyBindings bindings, InputRouter router) {
        this.bindings = bindings;
        this.router = router;
    }

    /**
     * Feeds the controller the devices the latest snapshot says this player owns. Called from the
     * snapshot listener, on the render thread — never from the network thread.
     */
    public void setDevices(List<DroneEntity> drones, List<CameraEntity> cameras, int ownerId) {
        this.devices = OwnedDevices.of(drones, cameras, ownerId);
        this.ownedCamera = findCamera(cameras, ownerId);
        DroneEntity drone = findDrone(drones, ownerId);
        if (drone == null) {
            predictedDrone = null;
        } else if (predictedDrone == null || predictedDrone.id != drone.id) {
            predictedDrone = drone.copy();
        } else {
            reconcileDrone(drone);
        }
    }

    /**
     * Polls the view-cycle key (mechanics §9, key {@code 6}) and applies it to the predicted
     * player at once. Call once per frame, before the input packet is sampled, so the edge rides
     * this frame's packet.
     */
    public void update(Player localPlayer) {
        if (!router.isGameplayActive() || localPlayer == null || !localPlayer.alive) {
            return;
        }
        if (bindings.isViewCycleJustPressed()) {
            SurveillanceView next = SurveillanceView.cycle(
                localPlayer.surveillance(), devices.droneLive(), devices.cameraReady());
            localPlayer.surveillanceView = next.ordinal();
            outstanding.addLast(new PendingViewAction(PacketPlayerInput.VIEW_CYCLE, next, -1L));
            trimOutstanding();
        }
    }

    /**
     * The Escape path (mechanics §9): returns the view to the body immediately and queues the
     * exit edge. Called from the screen's routed Escape handling, so the console and the pause
     * menu keep their own claim on the key.
     */
    public void exitSurveillance(Player localPlayer) {
        if (localPlayer == null || !localPlayer.isSurveillanceLocked()) {
            return;
        }
        localPlayer.surveillanceView = SurveillanceView.SELF.ordinal();
        outstanding.addLast(new PendingViewAction(PacketPlayerInput.VIEW_EXIT, SurveillanceView.SELF, -1L));
        trimOutstanding();
    }

    /**
     * Predicts one Q/E press on a drone or camera slot for the loadout controller, which owns the
     * key polling. Returns the resolved outcome (already applied to the predicted player), or
     * {@code null} when the press is ignored.
     */
    public GadgetPress.Outcome predictGadgetPress(Player localPlayer, int slotIndex) {
        if (localPlayer == null || localPlayer.loadout == null) {
            return null;
        }
        GadgetSlot slot = localPlayer.loadout.gadgetSlot(slotIndex);
        if (slot == null) {
            return null;
        }
        GadgetId id = slot.gadgetId();
        boolean deviceLive = id == GadgetId.DRONE ? devices.droneLive() : devices.cameraLive();
        boolean deviceReady = id == GadgetId.DRONE ? devices.droneLive() : devices.cameraReady();
        GadgetPress.Outcome outcome = GadgetPress.resolve(
            id, localPlayer.surveillance(), slot.isUsable(), deviceLive, deviceReady);
        if (outcome == GadgetPress.Outcome.IGNORED) {
            return null;
        }
        GadgetPress.apply(localPlayer, slot, outcome);
        return outcome;
    }

    /**
     * Re-applies a resolved gadget-press outcome after a snapshot stomped the predicted loadout
     * and view. Idempotent by construction; a view result whose device has since gone away is
     * skipped, so a destroyed drone cannot resurrect the pilot view.
     */
    public void reapplyGadgetPress(Player predicted, int slotIndex, GadgetPress.Outcome outcome) {
        if (predicted == null || predicted.loadout == null || outcome == null) {
            return;
        }
        GadgetSlot slot = predicted.loadout.gadgetSlot(slotIndex);
        if (slot == null) {
            return;
        }
        switch (outcome) {
            case DRONE_DEPLOYED, CAMERA_THROWN -> slot.active = true;
            case DRONE_PILOT -> {
                if (devices.droneLive()) {
                    predicted.surveillanceView = SurveillanceView.DRONE.ordinal();
                }
            }
            case CAMERA_VIEW -> {
                if (devices.cameraReady()) {
                    predicted.surveillanceView = SurveillanceView.CAMERA.ordinal();
                }
            }
            case DRONE_EXIT, CAMERA_EXIT -> predicted.surveillanceView = SurveillanceView.SELF.ordinal();
            case IGNORED -> {
                // nothing to re-apply
            }
        }
    }

    /**
     * Puts the oldest unacknowledged view action on the packet about to be sent — every packet,
     * until the server acknowledges it, so UDP loss cannot eat an Escape.
     */
    public void stampPacket(PacketPlayerInput packet) {
        packet.viewAction = PacketPlayerInput.NO_VIEW_ACTION;
        packet.viewActionSeq = -1L;
        PendingViewAction oldest = outstanding.peekFirst();
        if (oldest != null) {
            long birth = oldest.birthSequence();
            if (birth < 0L) {
                birth = packet.sequence;
                outstanding.pollFirst();
                outstanding.addFirst(new PendingViewAction(oldest.action(), oldest.result(), birth));
            }
            packet.viewAction = oldest.action();
            packet.viewActionSeq = birth;
        }
    }

    /**
     * Reconciles the prediction against an authoritative local-player state. Presses the server
     * has processed are retired; the rest are re-applied in order on top of the snapshot, because
     * the reconciliation otherwise stomps the predicted view with the older server state.
     */
    public void onAuthoritativePlayer(Player authoritative, Player predicted) {
        if (authoritative == null) {
            return;
        }
        long acked = authoritative.lastProcessedInputSequence;
        while (!outstanding.isEmpty()) {
            PendingViewAction oldest = outstanding.peekFirst();
            if (oldest.birthSequence() < 0L || oldest.birthSequence() > acked) {
                break;
            }
            outstanding.pollFirst();
        }
        if (predicted == null) {
            return;
        }
        if (!authoritative.alive) {
            // Authority consumed and rejected these edges; never replay them across death.
            outstanding.clear();
            predicted.surveillanceView = SurveillanceView.SELF.ordinal();
            return;
        }
        for (PendingViewAction action : outstanding) {
            if (action.result() == SurveillanceView.DRONE && !devices.droneLive()) {
                continue;
            }
            if (action.result() == SurveillanceView.CAMERA && !devices.cameraReady()) {
                continue;
            }
            predicted.surveillanceView = action.result().ordinal();
        }
    }

    /**
     * Steps the predicted drone with this frame's sampled input, so the piloted device stays
     * responsive between snapshots. Only the piloted drone is predicted; everyone else's rides
     * the interpolator.
     */
    public void predictDrone(Player localPlayer, PacketPlayerInput input, float dt, ArenaMap arena) {
        if (localPlayer == null || input == null || dt <= 0f || predictedDrone == null) {
            return;
        }
        if (localPlayer.surveillance() != SurveillanceView.DRONE) {
            return;
        }
        DroneMotion.stepInPlace(
            predictedDrone, input.moveX, input.jump || input.jetpack, input.crouch, dt, arena);
        predictedDrone.aimAngle = Angles.wrap(input.aimAngle);
    }

    /**
     * Where the camera should look this frame: the piloted device's position and the view's zoom,
     * or the player's own centre when not surveilling (the caller only follows the target while
     * the view is locked to a device, and the body-follow path centres on the body as before).
     */
    public ViewTarget viewTarget(Player localPlayer) {
        if (localPlayer == null) {
            return null;
        }
        SurveillanceView view = localPlayer.surveillance();
        if (view == SurveillanceView.DRONE && predictedDrone != null) {
            return new ViewTarget(predictedDrone.x, predictedDrone.y, 1f);
        }
        if (view == SurveillanceView.CAMERA && ownedCamera != null && ownedCamera.stuck) {
            return new ViewTarget(ownedCamera.x, ownedCamera.y, GadgetConfig.CAMERA_ZOOM);
        }
        return new ViewTarget(localPlayer.centerX(), localPlayer.centerY(), 1f);
    }

    /**
     * The position the aim is measured from: the device's position while surveilling, the
     * player's eye otherwise. The eye is the anchor the input sampler has always used, so an
     * unsupervised player's aim is byte-identical to before; a surveilling player's aim is what
     * steers the device's vision cone.
     */
    public ViewTarget aimAnchor(Player localPlayer) {
        if (localPlayer == null) {
            return null;
        }
        SurveillanceView view = localPlayer.surveillance();
        if (view == SurveillanceView.DRONE && predictedDrone != null) {
            return new ViewTarget(predictedDrone.x, predictedDrone.y, 1f);
        }
        if (view == SurveillanceView.CAMERA && ownedCamera != null && ownedCamera.stuck) {
            return new ViewTarget(ownedCamera.x, ownedCamera.y, 1f);
        }
        return new ViewTarget(localPlayer.eyeX(), localPlayer.eyeY(), 1f);
    }

    /** The locally predicted drone, reconciled against snapshots; {@code null} when none is owned. */
    public DroneEntity predictedDrone() {
        return predictedDrone;
    }

    /** Forgets every predicted device and pending edge — a disconnect or a fresh match. */
    public void reset() {
        outstanding.clear();
        predictedDrone = null;
        ownedCamera = null;
        devices = OwnedDevices.NONE;
    }

    private void reconcileDrone(DroneEntity authoritative) {
        float dx = predictedDrone.x - authoritative.x;
        float dy = predictedDrone.y - authoritative.y;
        if (dx * dx + dy * dy > DRONE_SNAP_DISTANCE_SQ) {
            predictedDrone.set(authoritative);
            return;
        }
        predictedDrone.x = Lerp.smooth(predictedDrone.x, authoritative.x, 25f, 1f / 60f);
        predictedDrone.y = Lerp.smooth(predictedDrone.y, authoritative.y, 25f, 1f / 60f);
        predictedDrone.vx = authoritative.vx;
        predictedDrone.vy = authoritative.vy;
        predictedDrone.aimAngle = authoritative.aimAngle;
        predictedDrone.health = authoritative.health;
    }

    private void trimOutstanding() {
        while (outstanding.size() > MAX_OUTSTANDING_VIEW_ACTIONS) {
            outstanding.pollFirst();
        }
    }

    private static DroneEntity findDrone(List<DroneEntity> drones, int ownerId) {
        if (drones == null) {
            return null;
        }
        for (DroneEntity drone : drones) {
            if (drone != null && drone.ownerId == ownerId) {
                return drone;
            }
        }
        return null;
    }

    private static CameraEntity findCamera(List<CameraEntity> cameras, int ownerId) {
        if (cameras == null) {
            return null;
        }
        for (CameraEntity camera : cameras) {
            if (camera != null && camera.ownerId == ownerId) {
                return camera;
            }
        }
        return null;
    }
}
