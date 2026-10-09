package io.github.skystrike.shared.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The view-cycle state machine (mechanics §9, key 6) and the surveillance lock flag. */
class SurveillanceViewTest {

    @Test
    @DisplayName("the cycle runs self → drone → camera → self")
    void cycleOrderIsSelfDroneCameraSelf() {
        assertEquals(SurveillanceView.DRONE,
            SurveillanceView.cycle(SurveillanceView.SELF, true, true));
        assertEquals(SurveillanceView.CAMERA,
            SurveillanceView.cycle(SurveillanceView.DRONE, true, true));
        assertEquals(SurveillanceView.SELF,
            SurveillanceView.cycle(SurveillanceView.CAMERA, true, true));
    }

    @Test
    @DisplayName("the cycle skips whatever is unavailable")
    void cycleSkipsUnavailableViews() {
        assertEquals(SurveillanceView.CAMERA,
            SurveillanceView.cycle(SurveillanceView.SELF, false, true),
            "no drone: self goes straight to the camera");
        assertEquals(SurveillanceView.SELF,
            SurveillanceView.cycle(SurveillanceView.DRONE, true, false),
            "no camera: the drone drops back to self");
        assertEquals(SurveillanceView.SELF,
            SurveillanceView.cycle(SurveillanceView.SELF, false, false),
            "nothing deployed: the cycle is a no-op");
        assertEquals(SurveillanceView.SELF,
            SurveillanceView.cycle(SurveillanceView.CAMERA, false, false));
    }

    @Test
    @DisplayName("a null current view reads as self")
    void nullCurrentReadsAsSelf() {
        assertEquals(SurveillanceView.DRONE, SurveillanceView.cycle(null, true, false));
        assertEquals(SurveillanceView.SELF, SurveillanceView.cycle(null, false, false));
    }

    @Test
    @DisplayName("only the device views lock the body")
    void onlyDevicesAreSurveillance() {
        assertFalse(SurveillanceView.SELF.isSurveillance());
        assertTrue(SurveillanceView.DRONE.isSurveillance());
        assertTrue(SurveillanceView.CAMERA.isSurveillance());
    }

    @Test
    @DisplayName("ordinal decoding is defensive against a newer protocol")
    void ordinalDecodingIsDefensive() {
        assertEquals(SurveillanceView.SELF, SurveillanceView.fromOrdinal(0));
        assertEquals(SurveillanceView.DRONE, SurveillanceView.fromOrdinal(1));
        assertEquals(SurveillanceView.CAMERA, SurveillanceView.fromOrdinal(2));
        assertEquals(SurveillanceView.SELF, SurveillanceView.fromOrdinal(3));
        assertEquals(SurveillanceView.SELF, SurveillanceView.fromOrdinal(-1));
        assertTrue(SurveillanceView.isValidOrdinal(2));
        assertFalse(SurveillanceView.isValidOrdinal(3));
    }
}
