package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import java.util.Locale;

/**
 * The read model behind the surveillance banner (mechanics §7, §9): what the HUD says while the
 * player is looking through a drone or a throw camera.
 *
 * <p>Pure functions of the predicted player, in {@code shared} where CI can test them — the banner
 * must never disagree with the player record it is drawn from. The device's hit points come from
 * its gadget slot, whose durability the server mirrors from the entity, so the banner and the
 * world cannot tell two different stories about how much drone is left.
 *
 * <p>The control reminder names the keys the way {@code LoadoutBar} names Q and E: as fixed
 * labels, matching the factory bindings.
 */
public final class HudSurveillance {

    private HudSurveillance() {
    }

    /** True while the banner should show: alive, and looking through a device. */
    public static boolean visible(Player player) {
        return player != null && player.alive && player.isSurveillanceLocked();
    }

    /** {@code "DRONE"} or {@code "CAMERA"} — the device being viewed; empty on self. */
    public static String title(Player player) {
        if (player == null) {
            return "";
        }
        return switch (player.surveillance()) {
            case DRONE -> "DRONE";
            case CAMERA -> "CAMERA";
            case SELF -> "";
        };
    }

    /**
     * The viewed device's remaining hit points as {@code "HP 21/30"}, read from the gadget slot
     * the server mirrors the entity's health into. Empty on self or with no device carried.
     */
    public static String healthText(Player player) {
        if (player == null || player.loadout == null) {
            return "";
        }
        SurveillanceView view = player.surveillance();
        if (view == SurveillanceView.DRONE) {
            GadgetSlot slot = player.loadout.droneSlot();
            return slot == null
                ? ""
                : String.format(Locale.ROOT, "HP %.0f/%.0f", slot.durability, GadgetConfig.DRONE_HEALTH);
        }
        if (view == SurveillanceView.CAMERA) {
            GadgetSlot slot = player.loadout.cameraSlot();
            return slot == null
                ? ""
                : String.format(Locale.ROOT, "HP %.0f/%.0f", slot.durability, GadgetConfig.CAMERA_HEALTH);
        }
        return "";
    }

    /**
     * The control reminder: what the movement keys drive now, that fire is locked out, and the
     * two ways to leave the view. Fixed labels, matching the factory bindings.
     */
    public static String controlsText() {
        return "WASD steer  LMB locked  6 cycle  Esc exit";
    }
}
