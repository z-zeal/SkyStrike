package io.github.skystrike.shared.model;

import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What each loadout slot may legally hold (playable build plan M4 §5).
 *
 * <p>One table, two readers. The server's {@code LoadoutUpdateHandler} validates an incoming
 * {@link io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate} with these predicates, and the
 * client's loadout picker builds its option grid from the same lists — so the picker can never
 * offer a choice the server would silently drop, which is the one way a picker reads as broken
 * while being perfectly correct.
 *
 * <p>The rules themselves are mechanics §8: slot 1 takes any gun, slot 2 takes sidearms only
 * (pistols and revolvers, as {@code WeaponClass.isSidearm} defines them), slot 3 takes any melee
 * weapon and can never be empty, slots 4–5 take throwables, and the Q/E gadget slots take any
 * gadget <i>including</i> {@link GadgetId#NONE} — "carry nothing there" is a real choice.
 *
 * <p>Every list is in ordinal order, which is wire order, so an index into one of them is stable
 * for as long as the protocol is.
 */
public final class LoadoutOptions {

    private static final List<WeaponId> PRIMARIES = List.of(WeaponId.values());
    private static final List<WeaponId> SIDEARMS = buildSidearms();
    private static final List<MeleeId> MELEE_WEAPONS = List.of(MeleeId.values());
    private static final List<UtilityId> UTILITIES = List.of(UtilityId.values());
    private static final List<GadgetId> GADGETS = List.of(GadgetId.values());

    private LoadoutOptions() {
    }

    /** Every gun, in ordinal order. Slot 1 accepts all of them. */
    public static List<WeaponId> primaries() {
        return PRIMARIES;
    }

    /** The guns slot 2 accepts: pistols and revolvers, in ordinal order. */
    public static List<WeaponId> sidearms() {
        return SIDEARMS;
    }

    public static List<MeleeId> meleeWeapons() {
        return MELEE_WEAPONS;
    }

    public static List<UtilityId> utilities() {
        return UTILITIES;
    }

    /** Every gadget slot choice, {@link GadgetId#NONE} first — the explicit empty slot. */
    public static List<GadgetId> gadgets() {
        return GADGETS;
    }

    public static boolean isLegalPrimary(int weaponOrdinal) {
        return WeaponId.isValidOrdinal(weaponOrdinal);
    }

    /** Slot 2 is sidearms only; the same predicate the server validates with. */
    public static boolean isLegalHandgun(int weaponOrdinal) {
        return WeaponId.isValidOrdinal(weaponOrdinal)
            && WeaponRegistry.ofOrdinal(weaponOrdinal).ballistics().weaponClass().isSidearm();
    }

    public static boolean isLegalMelee(int meleeOrdinal) {
        return MeleeId.isValidOrdinal(meleeOrdinal);
    }

    public static boolean isLegalUtility(int utilityOrdinal) {
        return UtilityId.isValidOrdinal(utilityOrdinal);
    }

    /** Gadget slots accept every gadget plus the explicit empty slot (ordinal 0). */
    public static boolean isLegalGadget(int gadgetOrdinal) {
        return GadgetId.isValidOrdinal(gadgetOrdinal);
    }

    private static List<WeaponId> buildSidearms() {
        List<WeaponId> sidearms = new ArrayList<>();
        for (WeaponId id : WeaponId.values()) {
            if (WeaponRegistry.of(id).ballistics().weaponClass().isSidearm()) {
                sidearms.add(id);
            }
        }
        return Collections.unmodifiableList(sidearms);
    }
}
