package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.TouchConfig;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.input.TouchPreferences;
import io.github.skystrike.shared.map.Rect;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Phone and tablet touch geometry. The reference sizes are the ones in the Android plan. */
class TouchHudLayoutTest {

    @Test
    @DisplayName("shortest width classifies at 600 dp, matching the Android device info")
    void classifiesAtSixHundredDp() {
        assertEquals(TouchHudLayout.TouchFormFactor.TABLET, TouchHudLayout.classify(1200f, 600f, 1f));
        assertEquals(TouchHudLayout.TouchFormFactor.PHONE, TouchHudLayout.classify(1198f, 599f, 1f));
        assertEquals(TouchHudLayout.TouchFormFactor.TABLET, TouchHudLayout.classify(1800f, 1200f, 2f));
        assertEquals(TouchHudLayout.TouchFormFactor.PHONE, TouchHudLayout.classify(1798f, 1198f, 2f));
        assertEquals(TouchHudLayout.TouchFormFactor.PHONE, TouchHudLayout.classify(2400f, 1080f, 3f));
    }

    @Test
    @DisplayName("the reference phone keeps plan sizes, a bottom-center dock and top-center vitals")
    void referencePhoneMatchesThePlan() {
        TouchHudLayout layout = TouchHudLayout.layout(2400f, 1080f, 3f, TouchPreferences.defaults());
        assertEquals(TouchHudLayout.TouchFormFactor.PHONE, layout.formFactor());
        assertEquals(1f, layout.appliedButtonScale(), 1e-4f);
        assertEquals(192f, layout.stickRadius(), 1e-3f);
        assertEquals(216f, layout.moveAnchorX(), 1e-3f);
        assertEquals(216f, layout.moveAnchorY(), 1e-3f);
        assertEquals(2184f, layout.aimAnchorX(), 1e-3f);
        assertEquals(216f, layout.aimAnchorY(), 1e-3f);
        assertEquals(426f, layout.hit(TouchAction.CROUCH).x(), 1e-3f);
        assertEquals(156f, layout.hit(TouchAction.SLOT_1).width(), 1e-3f);
        assertEquals(144f, layout.hit(TouchAction.SLOT_1).height(), 1e-3f);
        assertEquals(192f, layout.hit(TouchAction.FIRE).width(), 1e-3f);
        assertEquals(480f, layout.minimap().width(), 1e-3f);
        assertEquals(318f, layout.minimap().height(), 1e-3f);
        assertEquals(738f, layout.minimap().y(), 1e-3f);
        assertEquals(666f, layout.vitals().x(), 1e-3f);
        assertEquals(948f, layout.vitals().y(), 1e-3f);
        assertNull(layout.hit(TouchAction.USE));
        assertNull(layout.hit(TouchAction.EXIT_VIEW));
        assertNull(layout.hit(TouchAction.VIEW_CYCLE));
        assertDockBetweenThumbs(layout);
        assertInteractiveRules(layout);
    }

    @Test
    @DisplayName("the reference tablet pins sticks at 130/125 dp and parks vitals above the left thumb")
    void referenceTabletMatchesThePlan() {
        TouchHudLayout layout = TouchHudLayout.layout(2560f, 1600f, 2f, TouchPreferences.defaults());
        assertEquals(TouchHudLayout.TouchFormFactor.TABLET, layout.formFactor());
        assertEquals(1f, layout.appliedButtonScale(), 1e-4f);
        assertEquals(128f, layout.stickRadius(), 1e-3f);
        assertEquals(260f, layout.moveAnchorX(), 1e-3f);
        assertEquals(250f, layout.moveAnchorY(), 1e-3f);
        assertEquals(2300f, layout.aimAnchorX(), 1e-3f);
        assertEquals(250f, layout.aimAnchorY(), 1e-3f);
        assertEquals(128f, layout.hit(TouchAction.SLOT_1).width(), 1e-3f);
        assertEquals(104f, layout.hit(TouchAction.SLOT_1).height(), 1e-3f);
        assertEquals(420f, layout.minimap().width(), 1e-3f);
        assertEquals(280f, layout.minimap().height(), 1e-3f);
        assertEquals(500f, layout.vitals().y(), 1e-3f);
        assertEquals(440f, layout.vitals().width(), 1e-3f);
        assertInteractiveRules(layout);
    }

    @Test
    @DisplayName("phone, tablet, cutout, narrow and fully armed layouts keep 48 dp targets and 6 dp gaps")
    void interactiveBoxesFitOnTheReferenceDevices() {
        assertInteractiveRules(TouchHudLayout.layout(2400f, 1080f, 3f, TouchPreferences.defaults()));
        assertInteractiveRules(TouchHudLayout.layout(2560f, 1600f, 2f, TouchPreferences.defaults()));
        assertInteractiveRules(TouchHudLayout.layout(
            2400f, 1080f, 3f, 90, 48, 0, 36, TouchPreferences.defaults(), armed()));
        assertInteractiveRules(TouchHudLayout.layout(1280f, 720f, 2f, TouchPreferences.defaults()));
        assertInteractiveRules(TouchHudLayout.layout(
            1280f, 720f, 2f, 0, 0, 0, 0, TouchPreferences.defaults(), armed()));
        assertInteractiveRules(TouchHudLayout.layout(
            2400f, 1080f, 3f, 0, 0, 0, 0, leftHanded(), armed()));
        assertInteractiveRules(TouchHudLayout.layout(
            2560f, 1600f, 2f, 40, 40, 20, 24, TouchPreferences.defaults(), armed()));
    }

    @Test
    @DisplayName("safe insets push the phone movement stick in from the cutout")
    void safeInsetsPushTheStick() {
        TouchHudLayout layout = TouchHudLayout.layout(2400f, 1080f, 3f, 90, 0, 0, 0, TouchPreferences.defaults(),
            TouchHudLayout.TouchHudContext.none());
        assertEquals(306f, layout.moveAnchorX(), 1e-3f);
        assertTrue(layout.moveStickBounds().x() >= 90f);
        assertInteractiveRules(layout);
    }

    @Test
    @DisplayName("left-handed mode mirrors the thumbs, keeps reading order, and leaves the top bar put")
    void leftHandedMirrorsThumbsOnly() {
        TouchHudLayout right = TouchHudLayout.layout(2400f, 1080f, 3f, TouchPreferences.defaults());
        TouchHudLayout left = TouchHudLayout.layout(2400f, 1080f, 3f, leftHanded());
        assertEquals(2400f - right.aimAnchorX(), left.aimAnchorX(), 1e-3f);
        assertEquals(2400f - right.moveAnchorX(), left.moveAnchorX(), 1e-3f);
        assertEquals(right.hit(TouchAction.CHAT).x(), left.hit(TouchAction.CHAT).x(), 1e-3f);
        assertEquals(right.minimap().x(), left.minimap().x(), 1e-3f);
        assertTrue(left.hit(TouchAction.SLOT_1).x() < left.hit(TouchAction.SLOT_5).x());
        assertTrue(left.aimAnchorX() < left.moveAnchorX());
        assertInteractiveRules(left);
    }

    @Test
    @DisplayName("contextual use, exit and view-cycle buttons appear only when asked, and exit wins")
    void contextualButtonsFollowTheMatch() {
        TouchHudLayout armed = TouchHudLayout.layout(
            2400f, 1080f, 3f, 0, 0, 0, 0, TouchPreferences.defaults(), armed());
        assertNotNull(armed.hit(TouchAction.USE));
        assertNull(armed.hit(TouchAction.EXIT_VIEW));
        assertNotNull(armed.hit(TouchAction.VIEW_CYCLE));
        assertInteractiveRules(armed);

        TouchHudLayout.TouchHudContext exit = new TouchHudLayout.TouchHudContext(
            true, true, true, true, false);
        TouchHudLayout piloting = TouchHudLayout.layout(
            2400f, 1080f, 3f, 0, 0, 0, 0, TouchPreferences.defaults(), exit);
        assertNull(piloting.hit(TouchAction.USE));
        assertNotNull(piloting.hit(TouchAction.EXIT_VIEW));
        assertNotNull(piloting.surveillanceBanner());
        assertInteractiveRules(piloting);
    }

    @Test
    @DisplayName("a phone preset on a tablet-sized screen uses the phone dock, not the tablet one")
    void phonePresetOverridesClassification() {
        TouchPreferences preset = new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1f,
            0.72f,
            false,
            true,
            TouchPreferences.HudPreset.PHONE);
        TouchHudLayout layout = TouchHudLayout.layout(2560f, 1600f, 2f, preset);
        assertEquals(TouchHudLayout.TouchFormFactor.PHONE, layout.formFactor());
        assertEquals(104f, layout.hit(TouchAction.SLOT_1).width(), 1e-3f);
        assertTrue(layout.vitals().y() > 1000f, "phone vitals sit on the top edge, not above the stick");
        assertInteractiveRules(layout);
    }

    @Test
    @DisplayName("a scale that would overlap is reduced until the 48 dp floor and the gaps hold")
    void oversizedScaleIsClampedToFit() {
        TouchPreferences big = new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1.35f,
            0.4f,
            false,
            true,
            TouchPreferences.HudPreset.AUTO);
        TouchHudLayout layout = TouchHudLayout.layout(
            2400f, 1080f, 3f, 0, 0, 0, 0, big, armed());
        assertTrue(layout.fitsTouchRules());
        assertTrue(layout.appliedButtonScale() < 1.35f);
        assertTrue(layout.appliedButtonScale() >= TouchConfig.BUTTON_SCALE_MIN);
        assertEquals(0.4f, layout.buttonOpacity(), 1e-4f);
        assertInteractiveRules(layout);
    }

    @Test
    @DisplayName("a collapsed viewport does not throw and does not pretend to fit")
    void collapsedViewportIsEmpty() {
        TouchHudLayout collapsed = TouchHudLayout.layout(0f, 0f, 1f, TouchPreferences.defaults());
        assertFalse(collapsed.fitsTouchRules());
        assertNull(collapsed.hit(TouchAction.FIRE));
        assertTrue(collapsed.zones().isEmpty());
        TouchHudLayout tiny = TouchHudLayout.layout(100f, 100f, 1f, TouchPreferences.defaults());
        assertFalse(tiny.fitsTouchRules(), "100 px cannot hold 48 dp targets and 6 dp gaps");
        TouchHudLayout nonsense = TouchHudLayout.layout(Float.NaN, -1f, 0f, null);
        assertFalse(nonsense.fitsTouchRules());
        assertEquals(TouchConfig.BUTTON_OPACITY_DEFAULT, nonsense.buttonOpacity(), 1e-4f);
    }

    @Test
    @DisplayName("the 6 dp gap is measured on the diagonal as well as on each axis")
    void sixDpGapIncludesTheDiagonal() {
        float gap = TouchConfig.MIN_TOUCH_GAP_DP;
        Rect origin = new Rect(0f, 0f, 48f, 48f);
        assertTrue(TouchHudLayout.separated(origin, new Rect(54f, 0f, 48f, 48f), gap));
        assertFalse(TouchHudLayout.separated(origin, new Rect(53.9f, 0f, 48f, 48f), gap));
        assertTrue(TouchHudLayout.separated(origin, new Rect(52.3f, 52.3f, 48f, 48f), gap));
        assertFalse(TouchHudLayout.separated(origin, new Rect(51f, 51f, 48f, 48f), gap));
    }

    @Test
    @DisplayName("movement and aim zones contain their sticks and do not swallow the other thumb")
    void zonesContainTheirSticks() {
        TouchHudLayout layout = TouchHudLayout.layout(2400f, 1080f, 3f, TouchPreferences.defaults());
        assertTrue(layout.moveZone().contains(layout.moveAnchorX(), layout.moveAnchorY()));
        assertTrue(layout.aimZone().contains(layout.aimAnchorX(), layout.aimAnchorY()));
        assertFalse(layout.moveZone().contains(layout.aimAnchorX(), layout.aimAnchorY()));
        assertFalse(layout.aimZone().contains(layout.moveAnchorX(), layout.moveAnchorY()));
        assertTrue(layout.zones().stream().anyMatch(zone -> zone.action() == TouchAction.FIRE));
    }

    private static void assertDockBetweenThumbs(TouchHudLayout layout) {
        Rect first = layout.hit(TouchAction.SLOT_1);
        Rect last = layout.hit(TouchAction.GADGET_E);
        assertNotNull(first);
        assertNotNull(last);
        assertTrue(first.x() > layout.hit(TouchAction.CROUCH).right());
        assertTrue(last.right() < layout.aimStickBounds().left());
        float dockCenter = (first.x() + last.right()) / 2f;
        assertTrue(Math.abs(dockCenter - layout.width() / 2f) < layout.width() * 0.15f);
    }

    private static void assertInteractiveRules(TouchHudLayout layout) {
        assertTrue(layout.fitsTouchRules(), "layout reported itself unfit");
        float min = TouchConfig.MIN_TOUCH_TARGET_DP * layout.density() - 0.5f;
        float gap = TouchConfig.MIN_TOUCH_GAP_DP * layout.density();
        List<Rect> boxes = layout.interactiveBounds();
        assertFalse(boxes.isEmpty());
        for (Rect box : boxes) {
            assertTrue(box.width() >= min && box.height() >= min, box.toString());
            assertTrue(box.x() >= -0.5f && box.y() >= -0.5f, box.toString());
            assertTrue(box.right() <= layout.width() + 0.5f, box.toString());
            assertTrue(box.top() <= layout.height() + 0.5f, box.toString());
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Rect a = boxes.get(i);
                Rect b = boxes.get(j);
                assertFalse(areaOverlap(a, b), a + " overlaps " + b);
                if (xOverlap(a, b)) {
                    assertTrue(yGap(a, b) + 0.05f >= gap, a + " gap " + b);
                }
                if (yOverlap(a, b)) {
                    assertTrue(xGap(a, b) + 0.05f >= gap, a + " gap " + b);
                }
            }
        }
    }

    private static boolean areaOverlap(Rect a, Rect b) {
        return a.left() < b.right() && b.left() < a.right()
            && a.bottom() < b.top() && b.bottom() < a.top();
    }

    private static boolean xOverlap(Rect a, Rect b) {
        return a.left() < b.right() && b.left() < a.right();
    }

    private static boolean yOverlap(Rect a, Rect b) {
        return a.bottom() < b.top() && b.bottom() < a.top();
    }

    private static float xGap(Rect a, Rect b) {
        if (a.right() <= b.left()) {
            return b.left() - a.right();
        }
        if (b.right() <= a.left()) {
            return a.left() - b.right();
        }
        return -1f;
    }

    private static float yGap(Rect a, Rect b) {
        if (a.top() <= b.bottom()) {
            return b.bottom() - a.top();
        }
        if (b.top() <= a.bottom()) {
            return a.bottom() - b.top();
        }
        return -1f;
    }

    private static TouchPreferences leftHanded() {
        return new TouchPreferences(
            TouchPreferences.AimScheme.STICK,
            TouchPreferences.AdsMode.TOGGLE,
            true,
            1f,
            TouchConfig.BUTTON_OPACITY_DEFAULT,
            true,
            true,
            TouchPreferences.HudPreset.AUTO);
    }

    private static TouchHudLayout.TouchHudContext armed() {
        return new TouchHudLayout.TouchHudContext(true, false, true, false, false);
    }
}
