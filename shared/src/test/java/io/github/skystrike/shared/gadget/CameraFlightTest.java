package io.github.skystrike.shared.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.ThrowablePhysics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The camera flight's stick decision (mechanics §7.2), built on the one shared throwable
 * integrator: the arc is a throwable's arc, the first surface sticks, and the arena boundary
 * loses the camera.
 */
class CameraFlightTest {

    private static final float DT = 1f / 60f;

    private final ArenaMap map = ArenaMap.standard();

    private static ThrownUtility flightAt(float x, float y, float vx, float vy) {
        ThrownUtility flight = new ThrownUtility(1, 2, 0, 0, x, y, vx, vy, 0f);
        flight.aimAngle = 0f;
        return flight;
    }

    @Test
    @DisplayName("a flight through open air keeps flying")
    void openAirKeepsFlying() {
        ThrownUtility flight = flightAt(400f, 1200f, 300f, 0f);
        assertEquals(CameraFlight.Outcome.FLYING, CameraFlight.step(flight, DT, map));
        assertTrue(flight.x > 400f, "the integrator moved it");
    }

    @Test
    @DisplayName("the first surface sticks the flight flush, with no bounce past it")
    void firstSurfaceSticksFlush() {
        // Straight down at clear ground (x=780 is east of the ramps, west of the mid lane):
        // the ground plane's top is y=100, and the integrator's contact skin puts the centre
        // at 100 + radius + skin.
        ThrownUtility flight = flightAt(780f, 1200f, 0f, -300f);
        CameraFlight.Outcome outcome = CameraFlight.Outcome.FLYING;
        int ticks = 0;
        while (outcome == CameraFlight.Outcome.FLYING && ticks < 300) {
            outcome = CameraFlight.step(flight, DT, map);
            ticks++;
        }

        assertEquals(CameraFlight.Outcome.STUCK, outcome);
        assertEquals(0f, flight.vx, 1e-6f, "the velocity into the surface is spent");
        assertEquals(0f, flight.vy, 1e-6f, "no bounce carries it past the surface");
        assertEquals(1f, flight.contactNormalY, 1e-6f, "it stuck to a floor");
        assertEquals(
            100f + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN,
            flight.y,
            1e-3f,
            "flush with the ground plane's top");
    }

    @Test
    @DisplayName("a wall sticks the flight flush to its face, normal pointing out")
    void wallSticksFlush() {
        // East of the mid lane, flying at the centre room's left wall (x=1120, y=300..860).
        ThrownUtility flight = flightAt(1100f, 600f, 300f, 0f);
        CameraFlight.Outcome outcome = CameraFlight.Outcome.FLYING;
        int ticks = 0;
        while (outcome == CameraFlight.Outcome.FLYING && ticks < 300) {
            outcome = CameraFlight.step(flight, DT, map);
            ticks++;
        }

        assertEquals(CameraFlight.Outcome.STUCK, outcome);
        assertEquals(
            1120f - UtilityConfig.THROWABLE_RADIUS - UtilityConfig.CONTACT_SKIN,
            flight.x,
            1e-3f,
            "flush with the wall's face");
        assertEquals(-1f, flight.contactNormalX, 1e-6f, "the normal points out of the wall");
    }

    @Test
    @DisplayName("the stick decision maps the integrator's contacts: surface sticks, boundary loses")
    void outcomeMapping() {
        assertEquals(CameraFlight.Outcome.FLYING,
            CameraFlight.outcomeOf(ThrowablePhysics.Contact.NONE));
        assertEquals(CameraFlight.Outcome.STUCK,
            CameraFlight.outcomeOf(ThrowablePhysics.Contact.SURFACE));
        assertEquals(CameraFlight.Outcome.OFF_ARENA,
            CameraFlight.outcomeOf(ThrowablePhysics.Contact.BOUNDARY),
            "§7.2: a camera that leaves the arena is destroyed");
        assertEquals(CameraFlight.Outcome.FLYING, CameraFlight.outcomeOf(null));
    }

    @Test
    @DisplayName("degenerate input keeps flying")
    void degenerateInputIsFlying() {
        assertEquals(CameraFlight.Outcome.FLYING, CameraFlight.step(null, DT, map));
        ThrownUtility flight = flightAt(400f, 1200f, 300f, 0f);
        assertEquals(CameraFlight.Outcome.FLYING, CameraFlight.step(flight, 0f, map));
        assertEquals(CameraFlight.Outcome.FLYING, CameraFlight.step(flight, -1f, map));
    }

    @Test
    @DisplayName("the launch helpers are the integrator's own throw math at camera speed")
    void launchHelpersReuseTheIntegrator() {
        assertEquals(ThrowablePhysics.muzzleX(100f, 30f), CameraFlight.launchX(100f, 30f), 1e-6f);
        assertEquals(ThrowablePhysics.muzzleY(200f, 30f), CameraFlight.launchY(200f, 30f), 1e-6f);
        assertEquals(
            ThrowablePhysics.throwVelocityX(GadgetConfig.CAMERA_THROW_SPEED, 30f),
            CameraFlight.launchVelocityX(30f),
            1e-6f);
        assertEquals(
            ThrowablePhysics.throwVelocityY(GadgetConfig.CAMERA_THROW_SPEED, 30f),
            CameraFlight.launchVelocityY(30f),
            1e-6f);
    }
}
