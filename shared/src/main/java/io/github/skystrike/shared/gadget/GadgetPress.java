package io.github.skystrike.shared.gadget;

import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;

/**
 * The Q/E press state machine for the two manual gadgets (mechanics §7.1, §7.2), shared verbatim
 * by the authoritative server and the client's prediction.
 *
 * <p>One press on a drone or camera slot advances that gadget's engagement by exactly one step:
 * <ul>
 *   <li><b>Drone:</b> stowed → deployed (the entity spawns above the owner) → piloting (the view
 *       switches to the drone) → back to the body. The drone stays deployed when the player stops
 *       piloting it — {@code GadgetSlot.active} means "deployed in the world" for a drone, not
 *       "being piloted", so the press machine never clears it on the way out.</li>
 *   <li><b>Camera:</b> stowed → thrown (the entity spawns in flight) → ignored while still flying
 *       → viewing (once stuck) → back to the body. A flying camera has no cone and cannot be
 *       viewed, so its press does nothing until it has stuck to a surface.</li>
 * </ul>
 *
 * <p>Keeping this a pure function of {@code (gadget, view, slot usable, device live, device
 * ready)} is what lets the client predict a press exactly — the server resolves the same function
 * against its own entity lists and the two cannot diverge about what a press means. The shield is
 * deliberately <b>not</b> here: it is a hybrid gadget whose press is a plain active-flag toggle,
 * owned by {@code ShieldSystem}.
 */
public final class GadgetPress {

    private GadgetPress() {
    }

    /** What one Q/E press on a manual-gadget slot did. */
    public enum Outcome {
        /** The press was refused: empty/broken slot, wrong gadget, or a device not ready. */
        IGNORED,
        /** The drone spawned above the owner; the view is unchanged. */
        DRONE_DEPLOYED,
        /** The view switched to the drone; the body is now locked. */
        DRONE_PILOT,
        /** The view returned to the body; the drone stays deployed. */
        DRONE_EXIT,
        /** The camera was thrown; the view is unchanged. */
        CAMERA_THROWN,
        /** The view switched to the stuck camera. */
        CAMERA_VIEW,
        /** The view returned to the body; the camera stays in the world. */
        CAMERA_EXIT
    }

    /**
     * Resolves one press. {@code deviceLive} is "an entity owned by this player exists";
     * {@code deviceReady} is "that entity can currently be engaged" — always true for a drone,
     * true for a camera only once it has stuck to a surface.
     */
    public static Outcome resolve(
            GadgetId gadget,
            SurveillanceView view,
            boolean slotUsable,
            boolean deviceLive,
            boolean deviceReady) {
        if (gadget != GadgetId.DRONE && gadget != GadgetId.CAMERA) {
            return Outcome.IGNORED;
        }
        if (!slotUsable) {
            return Outcome.IGNORED;
        }
        if (!deviceLive) {
            return gadget == GadgetId.DRONE ? Outcome.DRONE_DEPLOYED : Outcome.CAMERA_THROWN;
        }
        if (!deviceReady) {
            return Outcome.IGNORED;
        }
        boolean engaged = view == (gadget == GadgetId.DRONE
            ? SurveillanceView.DRONE : SurveillanceView.CAMERA);
        if (engaged) {
            return gadget == GadgetId.DRONE ? Outcome.DRONE_EXIT : Outcome.CAMERA_EXIT;
        }
        return gadget == GadgetId.DRONE ? Outcome.DRONE_PILOT : Outcome.CAMERA_VIEW;
    }

    /**
     * Applies a resolved outcome to the player and its gadget slot. Deploy/throw mark the slot
     * active (the device is in the world); pilot/view switch the view; exit returns it to the
     * body. Idempotent by construction — re-applying an outcome lands in the same state, which
     * is what the client's prediction re-application relies on.
     */
    public static void apply(Player player, GadgetSlot slot, Outcome outcome) {
        if (player == null || slot == null || outcome == null) {
            return;
        }
        switch (outcome) {
            case DRONE_DEPLOYED, CAMERA_THROWN -> slot.active = true;
            case DRONE_PILOT -> player.surveillanceView = SurveillanceView.DRONE.ordinal();
            case DRONE_EXIT, CAMERA_EXIT -> player.surveillanceView = SurveillanceView.SELF.ordinal();
            case CAMERA_VIEW -> player.surveillanceView = SurveillanceView.CAMERA.ordinal();
            case IGNORED -> {
                // nothing to apply
            }
        }
    }

    /**
     * The view a press leaves the player in — the resolved outcome as a view, for prediction
     * bookkeeping. Deploy and throw leave the view alone; {@code IGNORED} changes nothing.
     */
    public static SurveillanceView resultingView(Outcome outcome, SurveillanceView current) {
        if (outcome == null) {
            return current;
        }
        return switch (outcome) {
            case DRONE_PILOT -> SurveillanceView.DRONE;
            case CAMERA_VIEW -> SurveillanceView.CAMERA;
            case DRONE_EXIT, CAMERA_EXIT -> SurveillanceView.SELF;
            case DRONE_DEPLOYED, CAMERA_THROWN, IGNORED -> current;
        };
    }
}
