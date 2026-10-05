package io.github.skystrike.shared.weapons;

/**
 * The thirteen guns of mechanics §5.1, as a stable identity both sides can name.
 *
 * <p>Only the identity and the display name live here. Ballistics are in
 * {@link WeaponBallistics}; the rest of the weapon table (including the ammunition numbers the
 * loadout needs) is in {@link WeaponRegistry}, keyed off this enum.
 *
 * <p><b>Ordinals cross the wire</b> (the input packet carries a selection index), so this list is
 * append-only in exactly the way {@code NetworkRegistration} is.
 */
public enum WeaponId {

    DESERT_EAGLE("Desert Eagle"),
    FAMAS("FAMAS"),
    SCAR_L("SCAR-L"),
    P90("P90"),
    KAR98K("Karabiner 98k"),
    AWP("AWP"),
    HK417("HK417"),
    SAWED_OFF("Sawed-Off Shotgun"),
    BURST_RIFLE("Burst Rifle"),
    ASSAULT_RIFLE("Assault Rifle"),
    SHOTGUN("Shotgun"),
    SNIPER_RIFLE("Sniper Rifle"),
    SMG("SMG");

    /** The primary of the default loadout — a player is never defenceless. */
    public static final WeaponId DEFAULT = SCAR_L;

    private final String displayName;

    WeaponId(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Lookup by wire ordinal. Out-of-range values fall back to {@link #DEFAULT}. */
    public static WeaponId fromOrdinal(int ordinal) {
        WeaponId[] ids = values();
        if (ordinal < 0 || ordinal >= ids.length) {
            return DEFAULT;
        }
        return ids[ordinal];
    }

    /** True when {@code ordinal} names a real weapon. */
    public static boolean isValidOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length;
    }
}
