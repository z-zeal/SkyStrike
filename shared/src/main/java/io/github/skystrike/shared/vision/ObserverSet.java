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
 * <p>Putting that rule in {@code shared} is what lets a second consumer trust it. The visibility
 * pass draws the cones; anything that must not contradict the picture those cones produce — today
 * the minimap's blip gate — asks the same set the same question. Two implementations of "what am I
 * looking at" would eventually answer differently, and a HUD that shows an enemy the fog is hiding
 * is a cheat sheet.
 *
 * <p>This is deliberately <b>not</b> the server's effect-culling set
 * ({@code server/fx/EffectBroadcaster}). That one asks a different question — "could this recipient
 * plausibly have seen this detonation" — so it counts the body's eyes even while piloting, samples
 * a disc around the effect rather than a target's hitbox, and clears the peripheral floor instead
 * of {@link io.github.skystrike.shared.config.VisionConfig#VISIBILITY_THRESHOLD}. Sharing a set
 * between the two would quietly change one of them.
 */
public final class ObserverSet {

    private static final ObserverSet EMPTY = new ObserverSet(List.of());

    private final List<Observer> observers;

    private ObserverSet(List<Observer> observers) {
        this.observers = List.copyOf(observers);
    }

    /** A set with no eyes in it: it sees nothing, and says so. */
    public static ObserverSet empty() {
        return EMPTY;
    }

    /** Wraps a caller-assembled list. Null entries are dropped, and the list is copied. */
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
        return kept.isEmpty() ? EMPTY : new ObserverSet(kept);
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
        List<Observer> eyes = new ArrayList<>(4);
        if (!viewer.isSurveillanceLocked()) {
            eyes.add(Observer.body(viewer, viewerReach));
        }
        for (DroneEntity drone : drones == null ? List.<DroneEntity>of() : drones) {
            if (drone == null || drone.ownerId != viewerId) {
                continue;
            }
            if (pilotedDrone != null && drone.id == pilotedDrone.id) {
                continue;
            }
            eyes.add(Observer.drone(drone));
        }
        if (pilotedDrone != null) {
            eyes.add(Observer.drone(pilotedDrone));
        }
        for (CameraEntity camera : cameras == null ? List.<CameraEntity>of() : cameras) {
            if (camera == null || camera.ownerId != viewerId || !camera.stuck) {
                continue;
            }
            eyes.add(Observer.camera(camera));
        }
        return of(eyes);
    }

    /** The observers, in the order they were assembled: body, devices, piloted device, cameras. */
    public List<Observer> observers() {
        return observers;
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
     * narrower 70° cone reveals less than a body's 120°.
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
