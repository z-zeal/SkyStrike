package io.github.skystrike.shared.net.c2s;

import io.github.skystrike.shared.net.Packet;

/**
 * Client-to-server request to change the loadout composition: primary, sidearm, melee, two
 * throwable utilities, and the two Q/E gadget slots.
 *
 * <p>The server validates every choice for its slot and applies it at the next respawn, which is
 * the one moment a loadout is rebuilt anyway (mechanics §10). Each field is independent:
 * {@link #KEEP_CURRENT} leaves that slot as it is. For the gadget fields, the
 * {@link io.github.skystrike.shared.gadget.GadgetId#NONE} ordinal (0) is a valid request —
 * "carry nothing there" — which is why the keep-sentinel is −1 and not 0.
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

    /** New Q gadget, as a {@link io.github.skystrike.shared.gadget.GadgetId} ordinal (0 = empty). */
    public int gadgetQ = KEEP_CURRENT;

    /** New E gadget, as a {@link io.github.skystrike.shared.gadget.GadgetId} ordinal (0 = empty). */
    public int gadgetE = KEEP_CURRENT;

    public PacketLoadoutUpdate() {
    }

    /** Compatibility constructor for callers changing only gun and melee slots. */
    public PacketLoadoutUpdate(int primary, int handgun, int melee) {
        this(primary, handgun, melee, KEEP_CURRENT, KEEP_CURRENT);
    }

    /** Compatibility constructor for callers changing only the five main slots. */
    public PacketLoadoutUpdate(int primary, int handgun, int melee, int utilityA, int utilityB) {
        this(primary, handgun, melee, utilityA, utilityB, KEEP_CURRENT, KEEP_CURRENT);
    }

    public PacketLoadoutUpdate(
            int primary, int handgun, int melee, int utilityA, int utilityB, int gadgetQ, int gadgetE) {
        this.primary = primary;
        this.handgun = handgun;
        this.melee = melee;
        this.utilityA = utilityA;
        this.utilityB = utilityB;
        this.gadgetQ = gadgetQ;
        this.gadgetE = gadgetE;
    }

    /** True when every field asks to keep the current choice. */
    public boolean isEmpty() {
        return primary == KEEP_CURRENT
            && handgun == KEEP_CURRENT
            && melee == KEEP_CURRENT
            && utilityA == KEEP_CURRENT
            && utilityB == KEEP_CURRENT
            && gadgetQ == KEEP_CURRENT
            && gadgetE == KEEP_CURRENT;
    }

    @Override
    public String toString() {
        return "PacketLoadoutUpdate[primary=" + primary
            + ", handgun=" + handgun
            + ", melee=" + melee
            + ", utilityA=" + utilityA
            + ", utilityB=" + utilityB
            + ", gadgetQ=" + gadgetQ
            + ", gadgetE=" + gadgetE + "]";
    }
}
