package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server request to change the five-slot loadout composition: primary, sidearm, melee
 * and two throwable utilities.
 *
 * <p>The server validates every choice for its slot and applies it at the next respawn, which is
 * the one moment a loadout is rebuilt anyway (mechanics §10). Each field is independent:
 * {@link #KEEP_CURRENT} leaves that slot as it is.
 */
public final class PacketLoadoutUpdate implements Packet {

    /** Field value meaning "do not change this slot". */
    public static final int KEEP_CURRENT = -1;

    /** New primary, as a {@link io.github.skystrike.shared.weapons.WeaponId} ordinal. */
    public int primary = KEEP_CURRENT;

    /** New sidearm, as a {@link io.github.skystrike.shared.weapons.WeaponId} ordinal. */
    public int handgun = KEEP_CURRENT;

    /** New melee weapon, as a {@link io.github.skystrike.shared.weapons.MeleeId} ordinal. */
    public int melee = KEEP_CURRENT;

    /** New slot 4 utility, as a {@link io.github.skystrike.shared.utility.UtilityId} ordinal. */
    public int utilityA = KEEP_CURRENT;

    /** New slot 5 utility, as a {@link io.github.skystrike.shared.utility.UtilityId} ordinal. */
    public int utilityB = KEEP_CURRENT;

    public PacketLoadoutUpdate() {
    }

    /** Compatibility constructor for callers changing only gun and melee slots. */
    public PacketLoadoutUpdate(int primary, int handgun, int melee) {
        this(primary, handgun, melee, KEEP_CURRENT, KEEP_CURRENT);
    }

    public PacketLoadoutUpdate(int primary, int handgun, int melee, int utilityA, int utilityB) {
        this.primary = primary;
        this.handgun = handgun;
        this.melee = melee;
        this.utilityA = utilityA;
        this.utilityB = utilityB;
    }

    /** True when every field asks to keep the current choice. */
    public boolean isEmpty() {
        return primary == KEEP_CURRENT
            && handgun == KEEP_CURRENT
            && melee == KEEP_CURRENT
            && utilityA == KEEP_CURRENT
            && utilityB == KEEP_CURRENT;
    }

    @Override
    public String toString() {
        return "PacketLoadoutUpdate[primary=" + primary
            + ", handgun=" + handgun
            + ", melee=" + melee
            + ", utilityA=" + utilityA
            + ", utilityB=" + utilityB + "]";
    }
}
