package io.github.skystrike.shared.vision;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * Every eye one viewer looks through, and the sight query that answers for all of them at once.
 *
 * <p>The rule this class owns is M10's, and until now it lived inline in the client's render loop:
 * a body projects its own cone <b>unless</b> the view has moved into a device — while piloting, the
 * body's cone is genuinely gone from the composite — and every device the viewer owns projects its
 * own cone whether or not anyone is looking through it. A piloted drone is described by the
 * caller's predicted copy rather than the interpolated one, so the cone and the camera never
 * disagree about where the device is.
 *
 * <p>M14 adds a vision <b>bubble</b> to each of those eyes: a full circle of close, all-round sight
 * ({@link Observer#bodyBubble}, {@link Observer#droneBubble}, {@link Observer#cameraBubble}). A
 * bubble follows its eye exactly as the cone does: it is gone while piloting with the body, and a
 * device's bubble exists whenever the device does. Bubbles are ordinary observers in
 * {@link #observers()}, so the visibility pass draws them with the cones and the minimap's blip
 * gate sees them without a second rule.
 *
 * <p>Putting that rule in {@code shared} is what lets a second consumer trust it. The visibility
 * pass draws the cones; anything that must not contradict the picture those cones produce — today
 * the minimap's blip gate and the player-light admission in {@code FxPipeline} — asks the same set
 * the same question. Two implementations of "what am I looking at" would eventually answer
 * differently, and a HUD that shows an enemy the fog is hiding is a cheat sheet.
 *
 * <p>This is deliberately <b>not</b> the server's effect-culling set
 * ({@code server/fx/EffectBroadcaster}). That one asks a different question — "could this recipient
 * plausibly have seen this detonation" — so it counts the body's eyes even while piloting, samples
 * a disc around the effect rather than a target's hitbox, and clears the peripheral floor instead
 * of {@link io.github.skystrike.shared.config.VisionConfig#VISIBILITY_THRESHOLD}. Sharing a set
 * between the two would quietly change one of them.
 */
public final class ObserverSet {

    private static final ObserverSet EMPTY = new ObserverSet(List.of(), List.of());

    private final List<Observer> observers;
    private final List<Observer> deviceBubbles;

    private ObserverSet(List<Observer> observers, List<Observer> deviceBubbles) {
        this.observers = List.copyOf(observers);
        this.deviceBubbles = List.copyOf(deviceBubbles);
    }

    /** A set with no eyes in it: it sees nothing, and says so. */
    public static ObserverSet empty() {
        return EMPTY;
    }

    /**
     * Wraps a caller-assembled list. Null entries are dropped, and the list is copied. A set built
     * this way has no device bubbles to light: {@link #deviceBubbles()} is empty.
     */
    public static ObserverSet of(List<Observer> observers) {
        if (observers == null || observers.isEmpty()) {
            return EMPTY;
        }
        List<Observer> kept = new ArrayList<>(observers.size());
        for (Observer observer : observers) {
            if (observer != null) {
                kept.add(observer);
            }
        }
        return kept.isEmpty() ? EMPTY : new ObserverSet(kept, List.of());
    }

    /**
     * The set a viewer is looking through right now.
     *
     * <p>Both device lists are the whole world's devices; ownership is filtered here, so a caller
     * cannot widen a viewer's sight by handing over someone else's drone.
     *
     * @param viewer        the viewer, or {@code null} before a spawn — an empty set
     * @param viewerReach   the viewer's own cone reach, eased across the ADS transition on the
     *                      client and discrete on the server
     * @param drones        every drone in the world; only {@code viewer}'s are used
     * @param pilotedDrone  the viewer's predicted drone while they pilot one, otherwise
     *                      {@code null}. Its interpolated copy in {@code drones} is skipped so the
     *                      device is counted once, at the position the camera is following.
     * @param cameras       every throw camera in the world; only {@code viewer}'s stuck ones are used
     */
    public static ObserverSet forViewer(
            Player viewer,
            float viewerReach,
            List<DroneEntity> drones,
            DroneEntity pilotedDrone,
            List<CameraEntity> cameras) {
        if (viewer == null) {
            return EMPTY;
        }
        int viewerId = viewer.id;
        List<Observer> eyes = new ArrayList<>(8);
        List<Observer> devices = new ArrayList<>(4);
        if (!viewer.isSurveillanceLocked()) {
            eyes.add(Observer.body(viewer, viewerReach));
            eyes.add(Observer.bodyBubble(viewer));
        }
        for (DroneEntity drone : drones == null ? List.<DroneEntity>of() : drones) {
            if (drone == null || drone.ownerId != viewerId) {
                continue;
            }
            if (pilotedDrone != null && drone.id == pilotedDrone.id) {
                continue;
            }
            addDevice(eyes, devices, Observer.drone(drone), Observer.droneBubble(drone));
        }
        if (pilotedDrone != null) {
            addDevice(eyes, devices, Observer.drone(pilotedDrone), Observer.droneBubble(pilotedDrone));
        }
        for (CameraEntity camera : cameras == null ? List.<CameraEntity>of() : cameras) {
            if (camera == null || camera.ownerId != viewerId || !camera.stuck) {
                continue;
            }
            addDevice(eyes, devices, Observer.camera(camera), Observer.cameraBubble(camera));
        }
        return eyes.isEmpty() ? EMPTY : new ObserverSet(eyes, devices);
    }

    /** A device's cone and bubble go into the draw list together; the bubble also joins the lights. */
    private static void addDevice(
            List<Observer> eyes, List<Observer> devices, Observer cone, Observer bubble) {
        eyes.add(cone);
        eyes.add(bubble);
        devices.add(bubble);
    }

    /**
     * The observers, in the order they were assembled: the body's cone and bubble, then each drone
     * as its cone and bubble, then the piloted drone, then each stuck camera. A bubble always
     * directly follows the cone of the same eye.
     */
    public List<Observer> observers() {
        return observers;
    }

    /**
     * The bubbles of the viewer's devices: one per owned drone and per stuck camera, and the piloted
     * drone's. The player's own bubble is not in this list, because the body's light is drawn from
     * the player's own light settings, not from a device's.
     */
    public List<Observer> deviceBubbles() {
        return deviceBubbles;
    }

    public int size() {
        return observers.size();
    }

    public boolean isEmpty() {
        return observers.isEmpty();
    }

    /**
     * Whether any eye in this set has {@code targetBox} genuinely lit — above the peripheral floor,
     * on the presentation bar, and judged with each observer's own cone angle, so a device's
     * narrower 70° cone reveals less than a body's 120°. A bubble is judged as a full circle, so a
     * target right behind a body is lit if it is inside the body's bubble.
     *
     * <p>This is the question a screen-facing consumer has to ask, and it is deliberately stricter
     * than {@link VisionMath#canObserverSeeTarget}: that one reports a body standing behind the
     * observer as seen, because 6% of peripheral floor really is non-zero. A HUD agreeing with it
     * would mark enemies the screen is drawing as black. The bar itself is
     * {@link io.github.skystrike.shared.config.VisionConfig#LIT_VISIBILITY_THRESHOLD}, the same one
     * M6's player lights and M7's effect culler use.
     *
     * <p>An empty set sees nothing. That is the correct answer for a viewer with no body and no
     * devices, not an error to guard against at every call site.
     */
    public boolean isLit(Rect targetBox, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        if (targetBox == null) {
            return false;
        }
        for (Observer observer : observers) {
            if (VisionMath.isTargetLit(
                    observer.eyeX(),
                    observer.eyeY(),
                    observer.aimAngleDeg(),
                    observer.reach(),
                    observer.coneHalfAngleDeg(),
                    targetBox,
                    map,
                    smokeVolumes)) {
                return true;
            }
        }
        return false;
    }

    /** {@link #isLit(Rect, ArenaMap, List)} against a player's hitbox. */
    public boolean isLit(Player target, ArenaMap map, List<SmokeVolume> smokeVolumes) {
        return target != null && isLit(target.hitbox(), map, smokeVolumes);
    }
}
