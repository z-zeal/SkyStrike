package io.github.skystrike.shared.utility;

/**
 * Every throwable in the game (mechanics §6).
 *
 * <p>Identity only; the numbers live in {@link UtilityRegistry}, the same split guns and melee
 * weapons use.
 *
 * <p><b>On the wire</b> a utility is encoded as {@link #WIRE_ID_BASE} plus the ordinal, giving a
 * third non-overlapping range in the single {@code weaponId} int that players, damage events and
 * kill events already carry: below 1000 is a {@link io.github.skystrike.shared.weapons.WeaponId}
 * ordinal, 1000+ is {@link io.github.skystrike.shared.weapons.MeleeId}, 2000+ is a utility. A
 * grenade kill therefore needs no new field anywhere.
 *
 * <p>The encoding is part of the protocol: <b>append only, never reorder, never reuse</b>.
 */
public enum UtilityId {

    FRAG("Frag Grenade", "frag-grenade"),
    IMPACT("Impact Grenade", "impact-grenade"),
    SMOKE("Smoke Grenade", "smoke-grenade"),
    STUN("Stun Grenade", "stun-grenade"),
    MOLOTOV("Molotov", "molotov"),
    POISON_SMOKE("Poison Smoke", "poison-smoke"),
    FLASHBANG("Flashbang", "flashbang"),
    CLAYMORE("Claymore", "claymore");

    /** Wire encoding offset: {@code wireId = WIRE_ID_BASE + ordinal}. Never renumber. */
    public static final int WIRE_ID_BASE = 2000;

    /** What a player carries in utility slot 1 before any loadout choice exists. */
    public static final UtilityId DEFAULT_PRIMARY = FRAG;

    /** What a player carries in utility slot 2 before any loadout choice exists. */
    public static final UtilityId DEFAULT_SECONDARY = SMOKE;

    private final String displayName;
    private final String assetId;

    UtilityId(String displayName, String assetId) {
        this.displayName = displayName;
        this.assetId = assetId;
    }

    public String displayName() {
        return displayName;
    }

    public String assetId() {
        return assetId;
    }

    /** This utility's encoding in the shared weapon-id space. */
    public int wireId() {
        return WIRE_ID_BASE + ordinal();
    }

    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }

    public static UtilityId fromOrdinal(int ordinal) {
        if (!isValidOrdinal(ordinal)) {
            throw new IllegalArgumentException("not a utility ordinal: " + ordinal);
        }
        return values()[ordinal];
    }

    /** True when a wire id names a utility rather than a gun or a melee weapon. */
    public static boolean isUtilityWireId(int wireId) {
        return wireId >= WIRE_ID_BASE && wireId < WIRE_ID_BASE + values().length;
    }

    /** Decodes a wire id, or returns {@code null} when it names something else. */
    public static UtilityId fromWireId(int wireId) {
        return isUtilityWireId(wireId) ? values()[wireId - WIRE_ID_BASE] : null;
    }
}
