package io.github.skystrike.server.gadget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.shared.config.GadgetConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The authoritative throw-camera lifecycle (mechanics §7.2): throw, flight, stick, view, exit,
 * destruction and the owner-death sweep. The flight itself is the shared throwable integrator —
 * what is asserted here is the stick decision and the lifecycle around it.
 */
class CameraSystemTest {

    private static final float DT = 1f / 60f;

    private final ArenaMap arena = ArenaMap.standard();
    private CameraSystem cameras;
    private Player owner;

    @BeforeEach
    void setUp() {
        cameras = new CameraSystem(arena);
        // Open sky above clear ground: x=780 is east of the ramps (which end at x=720) and
        // west of the mid lane and crate stack (which start at x=820), so a downward throw
        // reaches the ground plane unobstructed.
        owner = new Player(2, "Nova", 0, 780f, 1200f);
        owner.loadout.setGadgets(GadgetId.CAMERA, GadgetId.NONE);
    }

    private static PlayerInput input(float moveX, float aim) {
        return new PlayerInput(1L, moveX, false, false, false, false, false, aim);
    }

    // --- Press ------------------------------------------------------------------------------------

    @Test
    @DisplayName("the first Q press throws the camera along the aim on a throwable arc")
    void firstPressThrows() {
        owner.aimAngle = 0f;
        cameras.press(owner);

        CameraEntity camera = cameras.byOwner(owner.id);
        assertNotNull(camera);
        assertFalse(camera.stuck, "a fresh throw is flying");
        assertTrue(owner.loadout.gadgetQ.active, "the slot reads deployed");
        assertEquals(SurveillanceView.SELF, owner.surveillance());
        assertTrue(camera.vx > 0f, "thrown along +X at speed 12");
        assertEquals(0f, camera.vy, 1e-6f, "a level throw starts level");
        assertEquals(
            GadgetConfig.CAMERA_THROW_SPEED,
            (float) Math.sqrt(camera.vx * camera.vx + camera.vy * camera.vy),
            1e-3f);
        assertEquals(GadgetConfig.CAMERA_HEALTH, camera.health, 1e-6f);
    }

    @Test
    @DisplayName("a throw with no clear release point is refused, not spawned into a wall")
    void blockedThrowIsRefused() {
        // Facing the centre room's left wall (x=1120..1144): the release point is inside it.
        owner.x = 1100f;
        owner.aimAngle = 0f;
        cameras.press(owner);

        assertNull(cameras.byOwner(owner.id), "no camera is spawned");
        assertFalse(owner.loadout.gadgetQ.active, "the slot is not marked deployed");
        assertEquals(SurveillanceView.SELF, owner.surveillance());
    }

    @Test
    @DisplayName("a press while the camera flies does nothing; once stuck it starts the view")
    void pressIgnoredMidFlightThenViews() {
        owner.aimAngle = -90f; // straight down at clear ground
        cameras.press(owner);
        CameraEntity camera = cameras.byOwner(owner.id);

        cameras.press(owner);
        assertEquals(SurveillanceView.SELF, owner.surveillance(),
            "a flying camera cannot be viewed");

        for (int i = 0; i < 200 && !camera.stuck; i++) {
            cameras.stepOwned(owner, input(0f, -90f), DT);
        }
        assertTrue(camera.stuck, "the camera stuck to the ground");

        cameras.press(owner);
        assertEquals(SurveillanceView.CAMERA, owner.surveillance(), "now it can be viewed");

        cameras.press(owner);
        assertEquals(SurveillanceView.SELF, owner.surveillance(), "and the press exits the view");
        assertTrue(cameras.byOwner(owner.id).stuck, "the camera stays in the world");
    }

    @Test
    @DisplayName("a broken slot, a dead owner and a stunned owner refuse the press")
    void refusedPresses() {
        owner.loadout.gadgetQ.applyDurabilityDamage(999f);
        cameras.press(owner);
        assertNull(cameras.byOwner(owner.id));

        owner.loadout.gadgetQ.resetForRespawn();
        owner.alive = false;
        cameras.press(owner);
        assertNull(cameras.byOwner(owner.id), "a dead owner throws nothing");

        owner.alive = true;
        owner.slowRemaining = 1f;
        cameras.press(owner);
        assertNull(cameras.byOwner(owner.id), "a stunned owner throws nothing");
    }

    // --- Flight and stick --------------------------------------------------------------------------

    @Test
    @DisplayName("the flight follows the shared integrator's arc and sticks flush to the ground")
    void flightSticksToTheGround() {
        owner.aimAngle = -90f;
        cameras.press(owner);
        CameraEntity camera = cameras.byOwner(owner.id);

        float previousY = camera.y;
        for (int i = 0; i < 300 && !camera.stuck; i++) {
            cameras.stepOwned(owner, input(0f, -90f), DT);
            assertTrue(camera.y <= previousY + 1e-4f, "a downward throw only falls");
            previousY = camera.y;
        }

        assertTrue(camera.stuck);
        assertEquals(0f, camera.vx, 1e-6f, "a stuck camera is parked");
        assertEquals(0f, camera.vy, 1e-6f);
        assertEquals(1f, camera.contactNormalY, 1e-6f, "it stuck to a floor");
        assertEquals(
            100f + GadgetConfig.CAMERA_RADIUS,
            camera.y,
            0.05f,
            "flush with the ground plane's top, radius out");
    }

    @Test
    @DisplayName("a stuck camera is a fixed post: it does not move, but its cone follows the aim")
    void stuckCameraSwivelsButDoesNotMove() {
        owner.aimAngle = -90f;
        cameras.press(owner);
        CameraEntity camera = cameras.byOwner(owner.id);
        for (int i = 0; i < 200 && !camera.stuck; i++) {
            cameras.stepOwned(owner, input(0f, -90f), DT);
        }
        float stuckX = camera.x;
        float stuckY = camera.y;

        cameras.press(owner); // start viewing
        for (int i = 0; i < 10; i++) {
            cameras.stepOwned(owner, input(1f, 30f), DT);
        }

        assertEquals(stuckX, camera.x, 1e-6f, "a stuck camera never moves");
        assertEquals(stuckY, camera.y, 1e-6f);
        assertEquals(30f, camera.aimAngle, 1e-4f, "but its cone follows the viewer's aim");
    }

    @Test
    @DisplayName("the slot's durability mirrors the camera's health for the HUD")
    void durabilityMirrorsHealth() {
        owner.aimAngle = -90f;
        cameras.press(owner);
        CameraEntity camera = cameras.byOwner(owner.id);
        camera.applyDamage(7f);

        cameras.stepOwned(owner, input(0f, -90f), DT);

        assertEquals(GadgetConfig.CAMERA_HEALTH - 7f, owner.loadout.gadgetQ.durability, 1e-6f);
    }

    // --- Sweep ------------------------------------------------------------------------------------

    @Test
    @DisplayName("a spent camera is destroyed: slot broken for the life, viewer returned to their eyes")
    void sweepDestroysSpentCamera() {
        owner.aimAngle = -90f;
        cameras.press(owner);
        for (int i = 0; i < 200; i++) {
            cameras.stepOwned(owner, input(0f, -90f), DT);
        }
        cameras.press(owner); // view through it
        assertEquals(SurveillanceView.CAMERA, owner.surveillance());

        CameraEntity camera = cameras.byOwner(owner.id);
        camera.applyDamage(GadgetConfig.CAMERA_HEALTH);
        cameras.sweep(List.of(owner));

        assertNull(cameras.byOwner(owner.id));
        GadgetSlot slot = owner.loadout.gadgetQ;
        assertTrue(slot.broken);
        assertFalse(slot.active);
        assertEquals(SurveillanceView.SELF, owner.surveillance());
    }

    @Test
    @DisplayName("a camera whose owner died is removed with them and does not mark the slot broken")
    void sweepRemovesDeadOwnersCamera() {
        owner.aimAngle = -90f;
        cameras.press(owner);
        owner.alive = false;

        cameras.sweep(List.of(owner));

        assertNull(cameras.byOwner(owner.id));
        assertFalse(owner.loadout.gadgetQ.active);
        assertFalse(owner.loadout.gadgetQ.broken);
        assertEquals(SurveillanceView.SELF, owner.surveillance());
    }

    @Test
    @DisplayName("a camera whose owner left the match is removed")
    void sweepRemovesDisconnectedOwnersCamera() {
        owner.aimAngle = 0f;
        cameras.press(owner);
        cameras.sweep(List.of());
        assertNull(cameras.byOwner(owner.id));
    }

    // --- Bullets ----------------------------------------------------------------------------------

    @Test
    @DisplayName("a round lands on a camera and drains its health; the sweep destroys it")
    void bulletsDamageAndDestroyCameras() {
        owner.aimAngle = 0f;
        cameras.press(owner);
        CameraEntity camera = cameras.byOwner(owner.id);
        // Park it immediately so the test does not chase the flight.
        camera.stuck = true;
        camera.vx = 0f;
        camera.vy = 0f;
        camera.health = 5f;

        Player shooter = new Player(1, "Rook", 1, 550f, 1200f);
        shooter.aimAngle = 0f;
        BulletSystem bullets = new BulletSystem(arena);
        bullets.setGadgetSystems(null, cameras);
        assertNotNull(bullets.spawn(shooter, WeaponId.IRON_CARBINE, 0f));

        for (int i = 0; i < 30 && camera.health > 0f; i++) {
            bullets.step(DT, List.of(shooter), null);
        }
        assertTrue(camera.health <= 0f, "the round reached the camera");
        assertEquals(1, bullets.gadgetImpactCount());

        cameras.sweep(List.of(owner, shooter));
        assertNull(cameras.byOwner(owner.id));
        assertTrue(owner.loadout.gadgetQ.broken);
    }
}
