package io.github.skystrike.shared.gadget;

/**
 * Every gadget in the game, plus the explicit empty slot (mechanics §7, structure plan
 * {@code shared/gadget/GadgetType}).
 *
 * <p>Identity and activation behaviour only; the numbers live in {@link GadgetRegistry} and
 * {@link io.github.skystrike.shared.config.GadgetConfig}, the same split guns, melee weapons and
 * throwables use.
 *
 * <p>{@link #NONE} is a real member, not a {@code null}: the structure plan's gadget type is
 * "None, drone, shield, fuel tank, camera", and an empty Q or E slot has to be expressible both
 * in the loadout state and on the wire. That documented order is frozen here.
 *
 * <p><b>On the wire</b> a gadget slot carries the plain ordinal. Should a gadget ever need to
 * appear in the shared weapon-id int (a fuel-tank detonation in the kill feed, say), it is
 * encoded as {@link #WIRE_ID_BASE} plus the ordinal — a fourth non-overlapping range after guns
 * (&lt;1000), melee (1000+) and utilities (2000+).
 *
 * <p>Both encodings are part of the protocol: <b>append only, never reorder, never reuse</b>.
 */
public enum GadgetId {

    /** The empty slot. Inert: no definition, no durability, no effect. */
    NONE("Empty", "none", GadgetBehavior.PASSIVE),

    /** Deployable flying scout with its own vision cone; press again to pilot it. */
    DRONE("Surveillance Drone", "drone", GadgetBehavior.MANUAL),

    /** 150-durability shield: front arc while equipped, rear protection while stowed. */
    SHIELD("Ballistic Shield", "shield", GadgetBehavior.HYBRID),

    /** Jetpack capacity and thrust boost, bought with a rear-mounted explosive weak point. */
    FUEL_TANK("Fuel Tank", "fuel-tank", GadgetBehavior.PASSIVE),

    /** Arc-thrown sticky camera: a permanent fixed observation post, viewed at 1.2× zoom. */
    CAMERA("Throw Camera", "throw-camera", GadgetBehavior.MANUAL);

    /** Wire encoding offset in the shared weapon-id space: {@code wireId = WIRE_ID_BASE + ordinal}. */
    public static final int WIRE_ID_BASE = 3000;

    private final String displayName;
    private final String assetId;
    private final GadgetBehavior behavior;

    GadgetId(String displayName, String assetId, GadgetBehavior behavior) {
        this.displayName = displayName;
        this.assetId = assetId;
        this.behavior = behavior;
    }

    public String displayName() {
        return displayName;
    }

    public String assetId() {
        return assetId;
    }

    /**
     * How this gadget activates. {@link #NONE} reports {@link GadgetBehavior#PASSIVE} — an empty
     * slot is permanently "on" at doing nothing — so callers never meet a {@code null}.
     */
    public GadgetBehavior behavior() {
        return behavior;
    }

    /** True for every real gadget; false only for the empty slot. */
    public boolean isReal() {
        return this != NONE;
    }

    /** This gadget's encoding in the shared weapon-id space. */
    public int wireId() {
        return WIRE_ID_BASE + ordinal();
    }

    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }

    public static GadgetId fromOrdinal(int ordinal) {
        if (!isValidOrdinal(ordinal)) {
            throw new IllegalArgumentException("not a gadget ordinal: " + ordinal);
        }
        return values()[ordinal];
    }

    /**
     * Defensive decode for wire values: an invalid ordinal falls back to {@link #NONE} instead
     * of throwing, so a hostile or stale packet can never crash the receiving side.
     */
    public static GadgetId fromOrdinalOrNone(int ordinal) {
        return isValidOrdinal(ordinal) ? values()[ordinal] : NONE;
    }

    /** True when a wire id names a gadget rather than a gun, melee weapon or utility. */
    public static boolean isGadgetWireId(int wireId) {
        return wireId >= WIRE_ID_BASE && wireId < WIRE_ID_BASE + values().length;
    }

    /** Decodes a wire id, or returns {@code null} when it names something else. */
    public static GadgetId fromWireId(int wireId) {
        return isGadgetWireId(wireId) ? values()[wireId - WIRE_ID_BASE] : null;
    }
}
