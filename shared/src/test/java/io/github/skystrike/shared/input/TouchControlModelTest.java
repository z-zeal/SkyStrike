package io.github.skystrike.shared.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.FireMode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pointer isolation, deadzones, aim retention and the drone flight stick. Coordinates are HUD
 * y-up pixels. The fixture screen is 800 by 400: movement owns the left half, aim the right.
 */
class TouchControlModelTest {

    private static final float RADIUS = 80f;

    @Test
    @DisplayName("a left-thumb drag never changes aim, even when it crosses the midline")
    void leftThumbDoesNotSteerAim() {
        TouchControlModel model = aimed(180f);
        assertTrue(model.touchDown(0, 100f, 100f));
        assertTrue(model.touchDragged(0, 500f, 140f));
        assertEquals(180f, model.aimAngle(), 1e-4f);
        assertEquals(0, model.movePointer());
        assertEquals(VirtualStickState.NO_POINTER, model.aimPointer());
        assertTrue(model.sample().moveX() > 0f);
        assertEquals(180f, model.aimAngle(), 1e-4f);
    }

    @Test
    @DisplayName("a button inside a stick zone claims the pointer and leaves both sticks alone")
    void buttonBeatsEitherStick() {
        TouchControlModel model = aimed(45f);
        model.touchDown(0, 100f, 100f);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 760f, 140f);
        float aim = model.aimAngle();
        float move = model.moveStick().outputX();
        assertTrue(model.touchDown(2, 30f, 30f));
        TouchSample sample = model.sample();
        assertTrue(sample.fire());
        assertEquals(aim, model.aimAngle(), 1e-4f);
        assertEquals(move, model.moveStick().outputX(), 1e-4f);
        assertEquals(0, model.movePointer());
        assertEquals(1, model.aimPointer());
    }

    @Test
    @DisplayName("releasing the aim stick keeps the last angle outside the deadzone")
    void releasingAimRetainsAngle() {
        TouchControlModel model = aimed(180f);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 700f, 180f);
        assertEquals(90f, model.aimAngle(), 1e-3f);
        assertTrue(model.touchUp(1, 700f, 180f));
        assertEquals(VirtualStickState.NO_POINTER, model.aimPointer());
        assertEquals(90f, model.sample().aimAngle(), 1e-3f);
        assertEquals(90f, model.sample().aimAngle(), 1e-3f);
    }

    @Test
    @DisplayName("aim inside the deadzone does not snap, and a later correction waits until release")
    void deadzoneRetainsAndCorrectionWaits() {
        TouchControlModel model = aimed(180f);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 707f, 100f);
        assertEquals(180f, model.aimAngle(), 1e-3f);
        model.touchDragged(1, 780f, 100f);
        assertEquals(0f, model.aimAngle(), 1e-3f);
        model.setAimAngle(90f);
        assertEquals(0f, model.aimAngle(), 1e-3f);
        model.touchUp(1, 780f, 100f);
        model.setAimAngle(90f);
        assertEquals(90f, model.aimAngle(), 1e-3f);
        model.setAimAngle(Float.NaN);
        assertEquals(0f, model.aimAngle(), 1e-4f);
    }

    @Test
    @DisplayName("movement uses dx/radius past a radial 0.12 deadzone and does not rescale it")
    void movementDeadzoneIsRadialAndUnscaled() {
        TouchControlModel model = aimed(0f);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 109f, 100f);
        assertEquals(0f, model.sample().moveX(), 1e-4f);
        model.touchDragged(0, 110f, 100f);
        assertEquals(10f / RADIUS, model.sample().moveX(), 1e-4f);
        model.touchDragged(0, 100f, 180f);
        assertEquals(0f, model.sample().moveX(), 1e-4f);
        model.touchDragged(0, 260f, 100f);
        assertEquals(1f, model.sample().moveX(), 1e-4f);
    }

    @Test
    @DisplayName("on foot a downward stick does not crouch; the crouch button does, including a fast tap")
    void crouchIsAButtonOnFoot() {
        TouchControlModel model = aimed(0f);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 100f, 20f);
        TouchSample held = model.sample();
        assertFalse(held.crouch());
        assertFalse(held.jump());
        assertTrue(model.touchDown(3, 30f, 120f));
        assertTrue(model.touchUp(3, 30f, 120f));
        assertTrue(model.sample().crouch());
        assertFalse(model.sample().crouch());
    }

    @Test
    @DisplayName("drone flight maps stick Y past 0.20 to jump and crouch; camera view and on-foot do not")
    void droneFlightStickDoesNotLeakOntoTheBody() {
        TouchControlModel model = aimed(0f);
        model.setSurveillanceView(SurveillanceView.DRONE);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 100f, 116f);
        assertFalse(model.sample().jump(), "exactly 0.20 does not climb");
        model.touchDragged(0, 100f, 117f);
        TouchSample climb = model.sample();
        assertTrue(climb.jump());
        assertFalse(climb.jetpack());
        assertFalse(climb.crouch());
        model.touchDragged(0, 100f, 80f);
        TouchSample descend = model.sample();
        assertTrue(descend.crouch());
        assertFalse(descend.jump());

        model.setSurveillanceView(SurveillanceView.SELF);
        assertFalse(model.sample().jump());
        assertFalse(model.sample().crouch());

        model.setSurveillanceView(SurveillanceView.CAMERA);
        model.touchDragged(0, 100f, 180f);
        assertFalse(model.sample().jump());
        assertFalse(model.sample().crouch());
    }

    @Test
    @DisplayName("a drone climb that ends between samples still latches one jump, then stops")
    void droneClimbTapLatchesOneSample() {
        TouchControlModel model = aimed(0f);
        model.setSurveillanceView(SurveillanceView.DRONE);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 100f, 180f);
        model.touchUp(0, 100f, 180f);
        assertTrue(model.sample().jump());
        assertFalse(model.sample().jump());
    }

    @Test
    @DisplayName("leaving the drone before the sample drops a flight pulse so the body does not jump")
    void leavingDroneDropsTheFlightPulse() {
        TouchControlModel model = aimed(0f);
        model.setSurveillanceView(SurveillanceView.DRONE);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 100f, 180f);
        model.touchUp(0, 100f, 180f);
        model.setSurveillanceView(SurveillanceView.SELF);
        assertFalse(model.sample().jump());
        assertFalse(model.sample().crouch());
    }

    @Test
    @DisplayName("modal focus clears every pointer and zeroes movement and fire without snapping aim")
    void modalFocusResetsPointers() {
        TouchControlModel model = aimed(90f);
        model.setCombatItem(FireMode.AUTO, TouchControlModel.CombatItem.GUN);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 180f, 100f);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 780f, 160f);
        model.touchDown(2, 30f, 30f);
        float retained = model.aimAngle();
        model.setModalFocus(true);
        assertEquals(VirtualStickState.NO_POINTER, model.movePointer());
        assertEquals(VirtualStickState.NO_POINTER, model.aimPointer());
        TouchSample sample = model.sample();
        assertEquals(0f, sample.moveX(), 1e-4f);
        assertFalse(sample.fire());
        assertFalse(sample.jump());
        assertEquals(retained, sample.aimAngle(), 1e-3f);
        assertTrue(Math.abs(retained - 90f) > 1f, "the stick had already turned; modal must not snap back");
        assertFalse(model.touchDown(0, 100f, 100f));
        model.setModalFocus(false);
        assertTrue(model.touchDown(0, 100f, 100f));
    }

    @Test
    @DisplayName("touchCancelled drops every pointer, not only the named one")
    void touchCancelledClearsAllPointers() {
        TouchControlModel model = aimed(0f);
        model.touchDown(0, 100f, 100f);
        model.touchDown(1, 700f, 100f);
        assertTrue(model.touchCancelled(1, 700f, 100f));
        assertEquals(VirtualStickState.NO_POINTER, model.movePointer());
        assertEquals(VirtualStickState.NO_POINTER, model.aimPointer());
        assertFalse(model.touchDragged(0, 140f, 100f));
    }

    @Test
    @DisplayName("releasing the movement thumb leaves the aim thumb owning its angle")
    void releasingMoveLeavesAim() {
        TouchControlModel model = aimed(10f);
        model.touchDown(0, 100f, 100f);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 760f, 160f);
        float aim = model.aimAngle();
        assertTrue(model.touchUp(0, 140f, 100f));
        assertEquals(VirtualStickState.NO_POINTER, model.movePointer());
        assertEquals(1, model.aimPointer());
        assertEquals(aim, model.aimAngle(), 1e-4f);
    }

    @Test
    @DisplayName("a fast fire tap between samples latches one trigger sample and then clears")
    void fastFireTapLatchesOneSample() {
        TouchControlModel model = aimed(0f);
        assertTrue(model.touchDown(2, 30f, 30f));
        assertTrue(model.touchUp(2, 30f, 30f));
        assertTrue(model.sample().fire());
        assertFalse(model.sample().fire());
    }

    @Test
    @DisplayName("scheme A outer ring fires guns only, and a dedicated Fire tap still throws")
    void outerRingIsGunsOnly() {
        TouchControlModel guns = aimed(0f);
        guns.setCombatItem(FireMode.SEMI, TouchControlModel.CombatItem.GUN);
        guns.touchDown(1, 700f, 100f);
        guns.touchDragged(1, 700f + RADIUS * 0.5f, 100f);
        assertFalse(guns.sample().fire());
        guns.touchDragged(1, 700f + RADIUS * 0.9f, 100f);
        assertTrue(guns.sample().fire());

        TouchControlModel throwable = aimed(0f);
        throwable.setCombatItem(FireMode.SEMI, TouchControlModel.CombatItem.THROWABLE);
        throwable.touchDown(1, 700f, 100f);
        throwable.touchDragged(1, 700f + RADIUS * 0.9f, 100f);
        assertFalse(throwable.sample().fire());
        assertTrue(throwable.touchDown(2, 30f, 30f));
        assertTrue(throwable.sample().fire());

        TouchPreferences noRing = new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1f,
            0.72f,
            false,
            false,
            TouchPreferences.HudPreset.AUTO);
        guns.setPreferences(noRing);
        assertFalse(guns.sample().fire());
    }

    @Test
    @DisplayName("scheme B holds fire for automatic guns and releases one shot outside the cancel ring")
    void schemeBFiresOnHoldOrRelease() {
        TouchControlModel auto = flicking(FireMode.AUTO, TouchControlModel.CombatItem.GUN);
        auto.touchDown(1, 700f, 100f);
        auto.touchDragged(1, 700f + RADIUS * 0.30f, 100f);
        assertFalse(auto.sample().fire());
        auto.touchDragged(1, 700f + RADIUS * 0.40f, 100f);
        assertTrue(auto.sample().fire());
        auto.touchUp(1, 700f + RADIUS * 0.40f, 100f);
        assertFalse(auto.sample().fire());

        TouchControlModel semi = flicking(FireMode.SEMI, TouchControlModel.CombatItem.GUN);
        semi.touchDown(1, 700f, 100f);
        semi.touchDragged(1, 700f + RADIUS * 0.50f, 100f);
        assertFalse(semi.sample().fire());
        float aimed = semi.aimAngle();
        semi.touchDragged(1, 700f + RADIUS * 0.15f, 100f);
        semi.touchUp(1, 700f + RADIUS * 0.15f, 100f);
        TouchSample cancelled = semi.sample();
        assertFalse(cancelled.fire());
        assertEquals(aimed, cancelled.aimAngle(), 1e-3f);

        semi.touchDown(1, 700f, 100f);
        semi.touchDragged(1, 700f, 100f + RADIUS * 0.50f);
        semi.touchUp(1, 700f, 100f + RADIUS * 0.50f);
        assertTrue(semi.sample().fire());
        assertFalse(semi.sample().fire());
    }

    @Test
    @DisplayName("melee and throwables in scheme B fire on release outside the cancel ring, not while aiming")
    void schemeBReleaseCoversMeleeAndThrowables() {
        TouchControlModel melee = flicking(FireMode.SEMI, TouchControlModel.CombatItem.MELEE);
        melee.touchDown(1, 700f, 100f);
        melee.touchDragged(1, 760f, 100f);
        assertFalse(melee.sample().fire());
        melee.touchUp(1, 760f, 100f);
        assertTrue(melee.sample().fire());
    }

    @Test
    @DisplayName("switching off scheme A while the ring is held does not keep firing")
    void schemeChangeDropsStickFire() {
        TouchControlModel model = aimed(0f);
        model.setCombatItem(FireMode.AUTO, TouchControlModel.CombatItem.GUN);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 700f + RADIUS * 0.9f, 100f);
        assertTrue(model.sample().fire());
        model.setPreferences(new TouchPreferences(
            TouchPreferences.AimScheme.FLICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1f,
            0.72f,
            false,
            true,
            TouchPreferences.HudPreset.AUTO));
        model.setCombatItem(FireMode.SEMI, TouchControlModel.CombatItem.GUN);
        assertFalse(model.sample().fire());
    }

    @Test
    @DisplayName("ADS toggles by default and holds when the preference says so")
    void adsToggleAndHold() {
        TouchControlModel toggle = aimed(0f);
        toggle.touchDown(4, 200f, 40f);
        toggle.touchUp(4, 200f, 40f);
        assertTrue(toggle.adsEngaged());
        assertTrue(toggle.sample().ads());
        toggle.touchDown(4, 200f, 40f);
        assertFalse(toggle.adsEngaged());
        assertFalse(toggle.sample().ads());

        TouchControlModel hold = aimed(0f);
        hold.setPreferences(new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.HOLD,
            true,
            1f,
            0.72f,
            false,
            true,
            TouchPreferences.HudPreset.AUTO));
        hold.touchDown(4, 200f, 40f);
        assertTrue(hold.sample().ads());
        hold.touchUp(4, 200f, 40f);
        assertFalse(hold.sample().ads());
        assertFalse(hold.adsEngaged());
    }

    @Test
    @DisplayName("an ADS toggle survives modal focus; a held ADS finger does not")
    void adsToggleSurvivesModal() {
        TouchControlModel toggle = aimed(0f);
        toggle.touchDown(4, 200f, 40f);
        toggle.touchUp(4, 200f, 40f);
        toggle.setModalFocus(true);
        assertFalse(toggle.sample().ads());
        toggle.setModalFocus(false);
        assertTrue(toggle.sample().ads());
    }

    @Test
    @DisplayName("slot, gadget, use, view and chat edges survive until sample and do not disturb sticks")
    void edgesQueueWithoutMovingSticks() {
        TouchControlModel model = aimed(15f);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 150f, 100f);
        float move = model.moveStick().outputX();
        model.touchDown(2, 300f, 40f);
        model.touchUp(2, 300f, 40f);
        model.touchDown(3, 340f, 40f);
        model.touchUp(3, 340f, 40f);
        model.touchDown(4, 380f, 40f);
        model.touchDown(5, 420f, 40f);
        model.touchDown(6, 460f, 40f);
        model.touchDown(7, 500f, 40f);
        TouchSample first = model.sample();
        assertEquals(1, first.slotPress());
        assertEquals(PacketPlayerInput.GADGET_Q_PRESS, first.gadgetPress());
        assertEquals(PacketPlayerInput.USE_PRESS, first.usePress());
        assertEquals(PacketPlayerInput.VIEW_CYCLE, first.viewAction());
        assertEquals(TouchAction.CHAT, first.uiAction());
        assertEquals(move, model.moveStick().outputX(), 1e-4f);
        assertEquals(15f, first.aimAngle(), 1e-3f);
        TouchSample second = model.sample();
        assertEquals(4, second.slotPress());
        assertEquals(PacketPlayerInput.NO_GADGET_PRESS, second.gadgetPress());
        assertNull(second.uiAction());
    }

    @Test
    @DisplayName("the jetpack button is the jetpack control; the stick never burns fuel on foot")
    void jetpackIsTheDedicatedButton() {
        TouchControlModel model = aimed(0f);
        model.touchDown(0, 100f, 100f);
        model.touchDragged(0, 100f, 180f);
        assertFalse(model.sample().jetpack());
        model.touchDown(8, 80f, 40f);
        model.touchUp(8, 80f, 40f);
        assertTrue(model.sample().jetpack(), "a tap between samples still thrusts once");
        assertFalse(model.sample().jetpack());
        model.touchDown(8, 80f, 40f);
        assertTrue(model.sample().jetpack());
        model.touchUp(8, 80f, 40f);
        assertFalse(model.sample().jetpack(), "a hold already sampled does not thrust again after release");
    }

    @Test
    @DisplayName("a fixed movement stick deflects from the anchor; the aim stick still floats")
    void fixedMoveStickDoesNotPinAim() {
        TouchControlModel model = aimed(0f);
        model.setPreferences(new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.TOGGLE,
            false,
            1f,
            0.72f,
            false,
            true,
            TouchPreferences.HudPreset.AUTO));
        model.touchDown(0, 160f, 100f);
        assertEquals(100f, model.moveStick().originX(), 1e-3f);
        assertEquals(60f / RADIUS, model.moveStick().outputX(), 1e-3f);
        model.touchDown(1, 640f, 140f);
        assertEquals(640f, model.aimStick().originX(), 1e-3f);
        assertEquals(140f, model.aimStick().originY(), 1e-3f);
    }

    @Test
    @DisplayName("a lifted pointer id can be claimed by the other stick")
    void pointerIdCanBeReused() {
        TouchControlModel model = aimed(0f);
        model.touchDown(0, 100f, 100f);
        model.touchUp(0, 100f, 100f);
        assertTrue(model.touchDown(0, 700f, 100f));
        assertEquals(VirtualStickState.NO_POINTER, model.movePointer());
        assertEquals(0, model.aimPointer());
    }

    @Test
    @DisplayName("out-of-range pointers and non-finite points are ignored")
    void invalidPointersAreIgnored() {
        TouchControlModel model = aimed(12f);
        assertFalse(model.touchDown(-1, 100f, 100f));
        assertFalse(model.touchDown(10, 100f, 100f));
        assertFalse(model.touchDown(0, Float.NaN, 100f));
        assertFalse(model.touchDragged(3, 100f, 100f));
        assertFalse(model.touchUp(3, 100f, 100f));
        assertEquals(12f, model.sample().aimAngle(), 1e-4f);
        assertEquals(0f, model.sample().moveX(), 1e-4f);
    }

    @Test
    @DisplayName("spawn facing matches the server: team A at 0, team B at 180")
    void spawnFacingMatchesTheServer() {
        assertEquals(0f, TouchControlModel.spawnAimDegrees(0), 1e-4f);
        assertEquals(180f, TouchControlModel.spawnAimDegrees(1), 1e-4f);
        assertEquals(0f, TouchControlModel.spawnAimDegrees(2), 1e-4f);
        TouchControlModel model = new TouchControlModel(TouchControlModel.spawnAimDegrees(1));
        assertEquals(180f, model.aimAngle(), 1e-4f);
    }

    @Test
    @DisplayName("an empty hand does not stick-fire, and a null preference falls back to scheme A")
    void emptyHandAndNullPreferencesAreSafe() {
        TouchControlModel model = aimed(0f);
        model.setPreferences(null);
        model.setCombatItem(null, null);
        model.touchDown(1, 700f, 100f);
        model.touchDragged(1, 780f, 100f);
        assertFalse(model.sample().fire());
        assertEquals(TouchPreferences.AimScheme.STICK, model.preferences().aimScheme());
    }

    private static TouchControlModel aimed(float aim) {
        TouchControlModel model = new TouchControlModel(aim);
        model.setZones(zones());
        return model;
    }

    private static TouchControlModel flicking(FireMode mode, TouchControlModel.CombatItem item) {
        TouchControlModel model = aimed(0f);
        model.setPreferences(new TouchPreferences(
            TouchPreferences.AimScheme.FLICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1f,
            0.72f,
            false,
            true,
            TouchPreferences.HudPreset.AUTO));
        model.setCombatItem(mode, item);
        return model;
    }

    private static List<TouchZone> zones() {
        return List.of(
            TouchZone.button(TouchAction.FIRE, new Rect(10f, 10f, 60f, 60f)),
            TouchZone.button(TouchAction.CROUCH, new Rect(10f, 100f, 48f, 48f)),
            TouchZone.button(TouchAction.ADS, new Rect(180f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.SLOT_1, new Rect(280f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.SLOT_4, new Rect(320f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.GADGET_Q, new Rect(360f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.USE, new Rect(400f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.VIEW_CYCLE, new Rect(440f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.CHAT, new Rect(480f, 20f, 48f, 48f)),
            TouchZone.button(TouchAction.JETPACK, new Rect(60f, 20f, 48f, 48f)),
            TouchZone.stick(TouchZone.Kind.MOVE, new Rect(0f, 0f, 400f, 400f), 100f, 100f, RADIUS),
            TouchZone.stick(TouchZone.Kind.AIM, new Rect(400f, 0f, 400f, 400f), 700f, 100f, RADIUS));
    }
}
