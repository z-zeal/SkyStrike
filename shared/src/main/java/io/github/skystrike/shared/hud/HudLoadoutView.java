package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.gadget.GadgetBehavior;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.WeaponDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The read model behind the in-match loadout bar (playable build plan M4 §5).
 *
 * <p>This exists to make the M4 gate structural rather than aspirational: <i>ammo, reload,
 * quick-swap and utility counts on the HUD never disagree with the predicted
 * {@link PlayerLoadout}</i>. The bar does not compute any of those numbers — it cannot, because
 * it is handed them, already formatted, by this class. There is one derivation, it lives in
 * {@code shared} where CI can test it, and both the predicted loadout and (should it ever be
 * drawn) an authoritative one go through it unchanged.
 *
 * <p>Everything here is a pure function of the loadout: no timers of its own, no cached frame
 * state, no fallbacks that invent a number the loadout does not have. A {@code null} loadout
 * yields an empty view rather than a default one — "not connected yet" must not look like
 * "full magazine".
 */
public final class HudLoadoutView {

    /** Count text for a slot with nothing countable in it: an empty gun slot, or melee. */
    public static final String NO_COUNT = "-";

    private HudLoadoutView() {
    }

    /**
     * One of the five main slots, as the bar draws it.
     *
     * @param slot           1–5, the slot number the player presses
     * @param label          what is in the slot: weapon, melee or throwable display name
     * @param filled         whether the slot holds something usable right now
     * @param active         whether it is the slot in the hands
     * @param quickSwapOrigin whether a tap-swap to melee is remembering this slot as its origin
     * @param utility        true for the two throwable slots
     * @param melee          true for slot 3
     * @param magazine       rounds in the magazine, 0 for a non-gun slot
     * @param magazineSize   magazine capacity, 0 for a non-gun slot
     * @param reserve        rounds in reserve, 0 for a non-gun slot
     * @param utilityCount   throwables carried, 0 for a non-utility slot
     * @param countText      the string the bar prints: {@code "24/90"}, {@code "x2"} or {@code "-"}
     */
    public record SlotView(
        int slot,
        String label,
        boolean filled,
        boolean active,
        boolean quickSwapOrigin,
        boolean utility,
        boolean melee,
        int magazine,
        int magazineSize,
        int reserve,
        int utilityCount,
        String countText
    ) {

        /** Magazine fullness, 0–1. Non-gun slots report 0. */
        public float magazineFraction() {
            return magazineSize <= 0 ? 0f : Math.min(1f, Math.max(0f, magazine / (float) magazineSize));
        }

        /** True when a gun slot is dry: the magazine is empty and nothing is left to load. */
        public boolean dry() {
            return magazineSize > 0 && magazine == 0 && reserve == 0;
        }
    }

    /**
     * One of the two gadget slots, as the bar draws it.
     *
     * @param key          {@code "Q"} or {@code "E"} — the key that works it
     * @param gadget       which gadget, {@link GadgetId#NONE} for an empty slot
     * @param label        the gadget's display name, {@code "Empty"} for an empty slot
     * @param equipped     whether a real gadget is carried at all
     * @param active       the slot's live active flag: shield equipped, drone deployed
     * @param broken       destroyed for this life
     * @param durability   remaining durability
     * @param maxDurability durability pool size, 0 for gadgets without one (the fuel tank)
     * @param statusText   the string the bar prints: {@code "-"}, {@code "BROKEN"},
     *                     {@code "WORN"}, {@code "ON"} or {@code "OFF"}
     */
    public record GadgetView(
        String key,
        GadgetId gadget,
        String label,
        boolean equipped,
        boolean active,
        boolean broken,
        float durability,
        float maxDurability,
        String statusText
    ) {

        /** Durability remaining, 0–1. A gadget with no pool reports a full bar. */
        public float durabilityFraction() {
            if (maxDurability <= 0f) {
                return equipped && !broken ? 1f : 0f;
            }
            return Math.min(1f, Math.max(0f, durability / maxDurability));
        }
    }

    /** The five main slots in order, or an empty list when there is no loadout to show. */
    public static List<SlotView> slots(PlayerLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        List<SlotView> views = new ArrayList<>(PlayerLoadout.SLOT_COUNT);
        for (int slot = 1; slot <= PlayerLoadout.SLOT_COUNT; slot++) {
            views.add(slot(loadout, slot));
        }
        return views;
    }

    /** One slot's view. Never null for a valid slot number of a non-null loadout. */
    public static SlotView slot(PlayerLoadout loadout, int slot) {
        if (loadout == null || !PlayerLoadout.isValidSlot(slot)) {
            return null;
        }
        boolean active = loadout.activeSlot == slot;
        boolean origin = loadout.quickSwapOrigin == slot;
        boolean filled = loadout.isFilled(slot);

        if (slot == PlayerLoadout.SLOT_MELEE) {
            return new SlotView(slot, loadout.meleeId().displayName(), true, active, origin,
                false, true, 0, 0, 0, 0, NO_COUNT);
        }
        if (slot == PlayerLoadout.SLOT_UTILITY_A || slot == PlayerLoadout.SLOT_UTILITY_B) {
            UtilityId utility = loadout.utilityIdForSlot(slot);
            int count = loadout.utilityCountForSlot(slot);
            String label = utility == null ? "Empty" : utility.displayName();
            return new SlotView(slot, label, filled, active, origin,
                true, false, 0, 0, 0, count, "x" + count);
        }

        WeaponItem item = slot == PlayerLoadout.SLOT_PRIMARY ? loadout.primary : loadout.handgun;
        if (item == null || !item.hasWeapon()) {
            return new SlotView(slot, "Empty", false, active, origin,
                false, false, 0, 0, 0, 0, NO_COUNT);
        }
        return new SlotView(
            slot,
            item.weaponId().displayName(),
            filled,
            active,
            origin,
            false,
            false,
            item.magazine,
            item.magazineSize(),
            item.reserve,
            0,
            item.magazine + "/" + item.reserve);
    }

    /** The Q and E gadget slots, in that order. Empty list when there is no loadout. */
    public static List<GadgetView> gadgets(PlayerLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        return List.of(gadget(loadout, 0), gadget(loadout, 1));
    }

    /** One gadget slot by index: 0 = Q, 1 = E. */
    public static GadgetView gadget(PlayerLoadout loadout, int index) {
        if (loadout == null || (index != 0 && index != 1)) {
            return null;
        }
        GadgetSlot slot = loadout.gadgetSlot(index);
        GadgetId id = slot.gadgetId();
        boolean equipped = id.isReal();
        float max = equipped ? GadgetRegistry.maxDurability(id) : 0f;
        String status;
        if (!equipped) {
            status = NO_COUNT;
        } else if (slot.broken) {
            status = "BROKEN";
        } else if (id.behavior() == GadgetBehavior.PASSIVE) {
            status = "WORN";
        } else {
            status = slot.active ? "ON" : "OFF";
        }
        return new GadgetView(
            index == 0 ? "Q" : "E",
            id,
            equipped ? id.displayName() : "Empty",
            equipped,
            slot.active,
            slot.broken,
            slot.durability,
            max,
            status);
    }

    /**
     * Reload sweep, 0 at the moment the reload starts and 1 as it completes. Not reloading, or
     * reloading a weapon with no definition to time it against, reads 0 — the bar then draws no
     * sweep at all rather than a full one.
     */
    public static float reloadProgress(PlayerLoadout loadout) {
        if (loadout == null || !loadout.reloading) {
            return 0f;
        }
        float duration = loadout.reloadDuration();
        if (duration <= 0f) {
            return 0f;
        }
        float elapsed = duration - Math.max(0f, loadout.reloadTimer);
        return Math.min(1f, Math.max(0f, elapsed / duration));
    }

    /** {@code "RELOADING 1.2s"} while a reload is in flight, otherwise the empty string. */
    public static String reloadText(PlayerLoadout loadout) {
        if (loadout == null || !loadout.reloading) {
            return "";
        }
        return String.format(Locale.ROOT, "RELOADING %.1fs", Math.max(0f, loadout.reloadTimer));
    }

    /**
     * {@code "SWAP 1"} while a tap-swap to melee is remembering where it came from, otherwise
     * the empty string. The number is the slot a second tap returns to.
     */
    public static String quickSwapText(PlayerLoadout loadout) {
        if (loadout == null || loadout.quickSwapOrigin == PlayerLoadout.NO_QUICK_SWAP) {
            return "";
        }
        return "SWAP " + loadout.quickSwapOrigin;
    }

    /**
     * The display name of what is in the hands, whatever family it belongs to. Delegates to the
     * loadout's own wire-id resolution so the HUD and the snapshot can never name two things.
     */
    public static String heldLabel(PlayerLoadout loadout) {
        if (loadout == null) {
            return "";
        }
        SlotView active = slot(loadout, loadout.activeSlot);
        return active == null ? "" : active.label();
    }

    /** Rounds per magazine of the gun in hand, 0 when the hands hold something else. */
    public static int activeMagazineSize(PlayerLoadout loadout) {
        if (loadout == null) {
            return 0;
        }
        WeaponItem item = loadout.activeItem();
        if (item == null) {
            return 0;
        }
        WeaponDefinition definition = item.definition();
        return definition == null ? 0 : definition.magazineSize();
    }
}
