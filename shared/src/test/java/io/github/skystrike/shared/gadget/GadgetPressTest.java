package io.github.skystrike.shared.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Q/E press state machine for the manual gadgets (mechanics §7.1, §7.2), shared by the
 * authoritative server and the client's prediction.
 */
class GadgetPressTest {

    private static Player player(SurveillanceView view) {
        Player player = new Player(1, "Nova", 0, 400f, 1200f);
        player.surveillanceView = view.ordinal();
        return player;
    }

    @Test
    @DisplayName("a drone press walks stowed → deployed → piloting → body, and the drone stays deployed")
    void dronePressAdvancesOneStepPerPress() {
        Player player = player(SurveillanceView.SELF);
        GadgetSlot slot = player.loadout.gadgetQ;
        slot.equip(GadgetId.DRONE);

        assertEquals(GadgetPress.Outcome.DRONE_DEPLOYED,
            GadgetPress.resolve(GadgetId.DRONE, player.surveillance(), true, false, false));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_DEPLOYED);
        assertTrue(slot.active, "deployed means active in the world");
        assertEquals(SurveillanceView.SELF, player.surveillance(), "deploying does not switch the view");

        assertEquals(GadgetPress.Outcome.DRONE_PILOT,
            GadgetPress.resolve(GadgetId.DRONE, player.surveillance(), true, true, true));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_PILOT);
        assertEquals(SurveillanceView.DRONE, player.surveillance());
        assertTrue(slot.active, "the drone stays deployed while piloted");

        assertEquals(GadgetPress.Outcome.DRONE_EXIT,
            GadgetPress.resolve(GadgetId.DRONE, player.surveillance(), true, true, true));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_EXIT);
        assertEquals(SurveillanceView.SELF, player.surveillance());
        assertTrue(slot.active, "leaving the drone's view keeps it deployed");
    }

    @Test
    @DisplayName("a camera press walks stowed → thrown → ignored mid-flight → viewing → body")
    void cameraPressAdvancesOneStepPerPress() {
        Player player = player(SurveillanceView.SELF);
        GadgetSlot slot = player.loadout.gadgetQ;
        slot.equip(GadgetId.CAMERA);

        assertEquals(GadgetPress.Outcome.CAMERA_THROWN,
            GadgetPress.resolve(GadgetId.CAMERA, player.surveillance(), true, false, false));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.CAMERA_THROWN);
        assertTrue(slot.active);
        assertEquals(SurveillanceView.SELF, player.surveillance());

        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.CAMERA, player.surveillance(), true, true, false),
            "a flying camera cannot be viewed");
        assertEquals(SurveillanceView.SELF, player.surveillance());

        assertEquals(GadgetPress.Outcome.CAMERA_VIEW,
            GadgetPress.resolve(GadgetId.CAMERA, player.surveillance(), true, true, true));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.CAMERA_VIEW);
        assertEquals(SurveillanceView.CAMERA, player.surveillance());

        assertEquals(GadgetPress.Outcome.CAMERA_EXIT,
            GadgetPress.resolve(GadgetId.CAMERA, player.surveillance(), true, true, true));
        GadgetPress.apply(player, slot, GadgetPress.Outcome.CAMERA_EXIT);
        assertEquals(SurveillanceView.SELF, player.surveillance());
        assertTrue(slot.active, "the camera stays in the world");
    }

    @Test
    @DisplayName("a broken or empty slot refuses every press")
    void unusableSlotIsIgnored() {
        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.DRONE, SurveillanceView.SELF, false, false, false));
        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.CAMERA, SurveillanceView.SELF, false, false, false));
    }

    @Test
    @DisplayName("passive and empty gadgets are not this machine's business")
    void nonManualGadgetsAreIgnored() {
        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.SHIELD, SurveillanceView.SELF, true, false, false));
        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.FUEL_TANK, SurveillanceView.SELF, true, false, false));
        assertEquals(GadgetPress.Outcome.IGNORED,
            GadgetPress.resolve(GadgetId.NONE, SurveillanceView.SELF, true, false, false));
    }

    @Test
    @DisplayName("pressing the other gadget's slot while piloting switches straight to it")
    void pressingTheOtherGadgetSwitchesView() {
        Player player = player(SurveillanceView.DRONE);
        assertEquals(GadgetPress.Outcome.CAMERA_VIEW,
            GadgetPress.resolve(GadgetId.CAMERA, player.surveillance(), true, true, true),
            "gadget keys stay live while piloting (mechanics §9)");
        GadgetPress.apply(player, player.loadout.gadgetQ, GadgetPress.Outcome.CAMERA_VIEW);
        assertEquals(SurveillanceView.CAMERA, player.surveillance());
    }

    @Test
    @DisplayName("the resulting view is the outcome as a view, for prediction bookkeeping")
    void resultingViewMapsOutcomes() {
        assertEquals(SurveillanceView.DRONE,
            GadgetPress.resultingView(GadgetPress.Outcome.DRONE_PILOT, SurveillanceView.SELF));
        assertEquals(SurveillanceView.CAMERA,
            GadgetPress.resultingView(GadgetPress.Outcome.CAMERA_VIEW, SurveillanceView.DRONE));
        assertEquals(SurveillanceView.SELF,
            GadgetPress.resultingView(GadgetPress.Outcome.DRONE_EXIT, SurveillanceView.DRONE));
        assertEquals(SurveillanceView.SELF,
            GadgetPress.resultingView(GadgetPress.Outcome.CAMERA_EXIT, SurveillanceView.CAMERA));
        assertEquals(SurveillanceView.SELF,
            GadgetPress.resultingView(GadgetPress.Outcome.DRONE_DEPLOYED, SurveillanceView.SELF),
            "deploying does not move the view");
        assertEquals(SurveillanceView.SELF,
            GadgetPress.resultingView(GadgetPress.Outcome.CAMERA_THROWN, SurveillanceView.SELF));
        assertEquals(SurveillanceView.DRONE,
            GadgetPress.resultingView(GadgetPress.Outcome.IGNORED, SurveillanceView.DRONE));
        assertEquals(SurveillanceView.DRONE,
            GadgetPress.resultingView(null, SurveillanceView.DRONE));
    }

    @Test
    @DisplayName("applying an outcome is idempotent, which prediction re-application relies on")
    void applyIsIdempotent() {
        Player player = player(SurveillanceView.SELF);
        GadgetSlot slot = player.loadout.gadgetQ;
        slot.equip(GadgetId.DRONE);

        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_DEPLOYED);
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_DEPLOYED);
        assertTrue(slot.active);

        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_PILOT);
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_PILOT);
        assertEquals(SurveillanceView.DRONE, player.surveillance());

        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_EXIT);
        GadgetPress.apply(player, slot, GadgetPress.Outcome.DRONE_EXIT);
        assertEquals(SurveillanceView.SELF, player.surveillance());
        assertTrue(slot.active);

        GadgetPress.apply(null, null, null);
        GadgetPress.apply(player, null, GadgetPress.Outcome.DRONE_PILOT);
        GadgetPress.apply(null, slot, GadgetPress.Outcome.DRONE_PILOT);
        assertEquals(SurveillanceView.SELF, player.surveillance(), "null guards change nothing");
    }
}
