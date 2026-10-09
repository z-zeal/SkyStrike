package io.github.skystrike.shared.gadget;

/**
 * Which "eyes" a player is looking through (mechanics §7, §9): their own body, a deployed drone,
 * or a stuck throw camera.
 *
 * <p>This is authoritative server state, carried on the player record so every snapshot mirrors it
 * to clients. The server owns every transition — the client predicts them for feel and is
 * corrected by the next snapshot — and the same enum drives the server's input lock: while the
 * view is anything but {@link #SELF} the body cannot move, fire or change slots
 * ({@code PlayerMotion} and the loadout tick both read it).
 *
 * <p>The cycle order is fixed by the controls table: <b>self → drone → camera → self</b>, skipping
 * anything the player does not currently have available. {@link #cycle} is the one implementation
 * of that order, in {@code shared}, so the client's prediction and the server's authority cannot
 * disagree about what the view-cycle key does.
 */
public enum SurveillanceView {

    /** The player's own eyes: the body moves, fires and switches normally. */
    SELF,

    /** Piloting a deployed drone: the body is locked and vulnerable, the drone flies. */
    DRONE,

    /** Viewing through a stuck throw camera: the body is locked, the camera is fixed. */
    CAMERA;

    /** Decodes a wire ordinal; an unknown value (a newer protocol) reads as the player's own eyes. */
    public static SurveillanceView fromOrdinal(int ordinal) {
        return isValidOrdinal(ordinal) ? values()[ordinal] : SELF;
    }

    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }

    /**
     * The view-cycle state machine (mechanics §9, key {@code 6}): advance
     * self → drone → camera → self, skipping whatever is unavailable. With nothing available the
     * cycle is a no-op, and cycling always lands somewhere the player can actually be.
     *
     * @param current          the view being left; {@code null} reads as {@link #SELF}
     * @param droneAvailable   a live drone owned by the player is deployed
     * @param cameraAvailable  a stuck camera owned by the player is in the world
     */
    public static SurveillanceView cycle(
            SurveillanceView current,
            boolean droneAvailable,
            boolean cameraAvailable) {
        return switch (current == null ? SELF : current) {
            case SELF -> droneAvailable ? DRONE : cameraAvailable ? CAMERA : SELF;
            case DRONE -> cameraAvailable ? CAMERA : SELF;
            case CAMERA -> SELF;
        };
    }

    /** True while the player is looking through a device rather than their own eyes. */
    public boolean isSurveillance() {
        return this != SELF;
    }
}
