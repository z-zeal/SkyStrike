package io.github.skystrike.shared.weapons;

/**
 * The four melee weapons of mechanics §5.2, as a stable identity both sides can name.
 *
 * <p>Melee has slot 3 to itself and can never be removed, so a player is never defenceless.
 * Only identity and the display name live here; the numbers are in {@link MeleeRegistry}.
 *
 * <p><b>On the wire</b> a melee weapon is encoded as {@link #WIRE_ID_BASE} plus the ordinal, so
 * the single {@code weaponId} int carried by players, damage events and kill events can name
 * either family: values below the base are {@link WeaponId} ordinals, values at or above it are
 * melee. The encoding is part of the protocol and therefore append-only in exactly the way
 * {@code NetworkRegistration} is.
 */
public enum MeleeId {

    COMBAT_KNIFE("Combat Knife"),
    SHOVEL("Shovel"),
    BASEBALL_BAT("Baseball Bat"),
    KATANA("Katana");

    /** Wire encoding offset: {@code wireId = WIRE_ID_BASE + ordinal}. Never reuse or renumber. */
    public static final int WIRE_ID_BASE = 1000;

    /** The melee weapon a player carries before any loadout choice exists. */
    public static final MeleeId DEFAULT = COMBAT_KNIFE;

    private final String displayName;

    MeleeId(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The value this weapon takes in a {@code weaponId} field on the wire. */
    public int wireId() {
        return WIRE_ID_BASE + ordinal();
    }

    /** True when {@code wireId} encodes a melee weapon rather than a gun. */
    public static boolean isMeleeWireId(int wireId) {
        return wireId >= WIRE_ID_BASE && wireId < WIRE_ID_BASE + values().length;
    }

    /** Inverse of {@link #wireId()}. Out-of-range values fall back to {@link #DEFAULT}. */
    public static MeleeId fromWireId(int wireId) {
        return fromOrdinal(wireId - WIRE_ID_BASE);
    }

    /** Lookup by ordinal. Out-of-range values fall back to {@link #DEFAULT}. */
    public static MeleeId fromOrdinal(int ordinal) {
        MeleeId[] ids = values();
        if (ordinal < 0 || ordinal >= ids.length) {
            return DEFAULT;
        }
        return ids[ordinal];
    }

    /** True when {@code ordinal} names a real melee weapon. */
    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }
}
