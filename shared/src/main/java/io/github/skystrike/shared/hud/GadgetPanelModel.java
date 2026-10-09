package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.gadget.GadgetBehavior;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.List;
import java.util.Locale;

/**
 * The read model behind the gadget panel — the Phase 7 HUD's durability-and-cooldown readout for
 * the two gadget slots, which the loadout bar's 46-pixel boxes cannot fit.
 *
 * <p>Same contract as {@link HudLoadoutView}: everything is a pure function of the
 * <b>predicted</b> {@link PlayerLoadout}, so the panel cannot disagree with what the player's
 * next input will do. The server already mirrors what the panel needs into the slot itself —
 * {@code DroneSystem} and {@code CameraSystem} copy device health into {@link GadgetSlot#durability}
 * every tick, "so one HUD bar reads both" — so the model never reaches for the drone or camera
 * entity lists.
 *
 * <p>The durability pool follows {@link GadgetRegistry}: 150 for the shield, device health for
 * the drone (30) and camera (20), and no pool at all for the fuel tank — its risk is not wear
 * but detonation, so the panel shows its state word instead of an empty bar. The cooldown reads
 * the slot's {@code cooldownRemaining} hook: no gadget sets it today, so the panel shows
 * {@code READY} everywhere until the authoritative systems start arming cooldowns, at which
 * point the countdown appears with no client change.
 */
public final class GadgetPanelModel {

    /** The panel prints cooldown remaining to one decimal place. */
    private static final String COOLDOWN_READY = "READY";

    private GadgetPanelModel() {
    }

    /**
     * One gadget slot as the panel prints it.
     *
     * @param key               {@code "Q"} or {@code "E"} — the key that works the slot
     * @param gadget            which gadget, {@link GadgetId#NONE} for an empty slot
     * @param name              display name, {@code "Empty"} for an empty slot
     * @param equipped          whether a real gadget is carried at all
     * @param active            the slot's live active flag: shield equipped, device deployed
     * @param broken            destroyed for this life
     * @param behavior          how the key press works, for the panel's vocabulary
     * @param durability        remaining durability (device health for drone and camera)
     * @param maxDurability     pool size, 0 for gadgets without one (fuel tank, empty slot)
     * @param cooldownRemaining seconds until the gadget key works again, 0 when ready
     * @param stateText         the panel's state word: {@code EMPTY}, {@code BROKEN},
     *                          {@code DESTROYED}, {@code DETONATED}, {@code DEPLOYED},
     *                          {@code FRONT}, {@code REAR}, {@code WORN} or {@code READY}
     * @param durabilityText    {@code "112/150"} for a gadget with a pool, otherwise empty
     * @param cooldownText      {@code "1.3s"} while a cooldown runs, otherwise {@code READY}
     *                          for a gadget the key can work, empty for a passive one
     */
    public record SlotPanel(
        String key,
        GadgetId gadget,
        String name,
        boolean equipped,
        boolean active,
        boolean broken,
        GadgetBehavior behavior,
        float durability,
        float maxDurability,
        float cooldownRemaining,
        String stateText,
        String durabilityText,
        String cooldownText
    ) {

        /** True when the gadget has a durability pool the panel draws as a bar. */
        public boolean hasDurabilityPool() {
            return equipped && maxDurability > 0f;
        }

        /** Durability remaining, 0–1. A gadget with no pool reports a full bar. */
        public float durabilityFraction() {
            if (maxDurability <= 0f) {
                return equipped && !broken ? 1f : 0f;
            }
            return Math.min(1f, Math.max(0f, durability / maxDurability));
        }

        /** True while a use cooldown is running. */
        public boolean onCooldown() {
            return equipped && !broken && cooldownRemaining > 0f;
        }
    }

    /** The Q and E slots in that order, or an empty list when there is no loadout. */
    public static List<SlotPanel> slots(PlayerLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        return List.of(slot(loadout, 0), slot(loadout, 1));
    }

    /**
     * The panel draws only when there is something to report: at least one slot holds a real
     * gadget. Two empty slots are already visible as the bar's "Empty" boxes, and an empty panel
     * plate would be furniture.
     */
    public static boolean visible(PlayerLoadout loadout) {
        if (loadout == null) {
            return false;
        }
        return !loadout.gadgetSlot(0).isEmpty() || !loadout.gadgetSlot(1).isEmpty();
    }

    /** One gadget slot by index: 0 = Q, 1 = E. Null for a null loadout or invalid index. */
    public static SlotPanel slot(PlayerLoadout loadout, int index) {
        if (loadout == null || (index != 0 && index != 1)) {
            return null;
        }
        GadgetSlot slot = loadout.gadgetSlot(index);
        GadgetId id = slot.gadgetId();
        boolean equipped = id.isReal();
        float max = equipped ? GadgetRegistry.maxDurability(id) : 0f;
        return new SlotPanel(
            index == 0 ? "Q" : "E",
            id,
            equipped ? id.displayName() : "Empty",
            equipped,
            slot.active,
            slot.broken,
            equipped ? id.behavior() : GadgetBehavior.PASSIVE,
            Math.max(0f, slot.durability),
            max,
            Math.max(0f, slot.cooldownRemaining),
            stateText(slot),
            durabilityText(slot, max),
            cooldownText(slot));
    }

    /**
     * The panel's state word for one slot. Broken gets a per-gadget word — a shield breaks, a
     * device is destroyed, a tank detonates — because the panel has the room the 46-pixel box
     * does not. Intact state names what the gadget is doing: deployed devices, the shield's
     * protected side (§7.3's front/rear trade), a worn passive, or a manual gadget waiting on
     * its key.
     */
    public static String stateText(GadgetSlot slot) {
        if (slot == null || slot.isEmpty()) {
            return "EMPTY";
        }
        if (slot.broken) {
            return switch (slot.gadgetId()) {
                case DRONE, CAMERA -> "DESTROYED";
                case FUEL_TANK -> "DETONATED";
                default -> "BROKEN";
            };
        }
        return switch (slot.gadgetId()) {
            case SHIELD -> slot.active ? "FRONT" : "REAR";
            case DRONE, CAMERA -> slot.active ? "DEPLOYED" : "READY";
            case FUEL_TANK -> "WORN";
            default -> "READY";
        };
    }

    /** {@code "112/150"} for a gadget with a pool; the fuel tank and empty slots print none. */
    public static String durabilityText(GadgetSlot slot, float maxDurability) {
        if (slot == null || slot.isEmpty() || maxDurability <= 0f) {
            return "";
        }
        long current = Math.round(Math.max(0f, Math.min(slot.durability, maxDurability)));
        return current + "/" + Math.round(maxDurability);
    }

    /**
     * The countdown while a use cooldown runs, {@code READY} while a pressable gadget waits,
     * and nothing for a passive one or an empty slot — a fuel tank is never "ready", it just is.
     */
    public static String cooldownText(GadgetSlot slot) {
        if (slot == null || slot.isEmpty() || slot.broken) {
            return "";
        }
        if (slot.cooldownRemaining > 0f) {
            return String.format(Locale.ROOT, "%.1fs", slot.cooldownRemaining);
        }
        return slot.gadgetId().behavior().respondsToPress() ? COOLDOWN_READY : "";
    }
}
