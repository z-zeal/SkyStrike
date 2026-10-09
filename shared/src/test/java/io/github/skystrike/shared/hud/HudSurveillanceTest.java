package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The surveillance banner's read model: pure functions of the predicted player. */
class HudSurveillanceTest {

    private static Player player(SurveillanceView view) {
        Player player = new Player(1, "Nova", 0, 400f, 1200f);
        player.surveillanceView = view.ordinal();
        return player;
    }

    @Test
    @DisplayName("the banner shows only while alive and looking through a device")
    void visibilityFollowsTheView() {
        assertFalse(HudSurveillance.visible(null));
        assertFalse(HudSurveillance.visible(player(SurveillanceView.SELF)),
            "the body's own eyes need no banner");

        Player piloting = player(SurveillanceView.DRONE);
        assertTrue(HudSurveillance.visible(piloting));

        Player viewing = player(SurveillanceView.CAMERA);
        assertTrue(HudSurveillance.visible(viewing));

        piloting.alive = false;
        assertFalse(HudSurveillance.visible(piloting), "a dead pilot is not surveilling");
    }

    @Test
    @DisplayName("the title names the device being viewed")
    void titleNamesTheDevice() {
        assertEquals("", HudSurveillance.title(null));
        assertEquals("", HudSurveillance.title(player(SurveillanceView.SELF)));
        assertEquals("DRONE", HudSurveillance.title(player(SurveillanceView.DRONE)));
        assertEquals("CAMERA", HudSurveillance.title(player(SurveillanceView.CAMERA)));
    }

    @Test
    @DisplayName("the health text reads the gadget slot the server mirrors the device health into")
    void healthTextReadsTheSlot() {
        Player drone = player(SurveillanceView.DRONE);
        assertEquals("", HudSurveillance.healthText(drone), "no drone carried: nothing to report");

        drone.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        drone.loadout.gadgetQ.durability = 21f;
        assertEquals("HP 21/30", HudSurveillance.healthText(drone));

        Player camera = player(SurveillanceView.CAMERA);
        camera.loadout.setGadgets(GadgetId.NONE, GadgetId.CAMERA);
        camera.loadout.gadgetE.durability = 7.5f;
        assertEquals("HP 8/20", HudSurveillance.healthText(camera),
            "rounded to whole hit points, against the camera's 20");

        assertEquals("", HudSurveillance.healthText(player(SurveillanceView.SELF)));
        assertEquals("", HudSurveillance.healthText(null));
    }

    @Test
    @DisplayName("the health denominator is the config value, not a second copy of it")
    void healthDenominatorIsTheConfig() {
        Player drone = player(SurveillanceView.DRONE);
        drone.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        drone.loadout.gadgetQ.durability = GadgetConfig.DRONE_HEALTH;
        assertEquals("HP 30/30", HudSurveillance.healthText(drone));

        Player camera = player(SurveillanceView.CAMERA);
        camera.loadout.setGadgets(GadgetId.CAMERA, GadgetId.NONE);
        camera.loadout.gadgetQ.durability = GadgetConfig.CAMERA_HEALTH;
        assertEquals("HP 20/20", HudSurveillance.healthText(camera));
    }

    @Test
    @DisplayName("the control reminder names the keys that still work under the lock")
    void controlsTextIsStable() {
        String controls = HudSurveillance.controlsText();
        assertTrue(controls.contains("WASD"), "the movement keys steer the device");
        assertTrue(controls.contains("LMB locked"), "fire is locked out");
        assertTrue(controls.contains("6 cycle"), "the view-cycle key stays live");
        assertTrue(controls.contains("Esc exit"), "Escape leaves the view");
    }
}
