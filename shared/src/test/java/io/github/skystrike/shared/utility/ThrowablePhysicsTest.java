package io.github.skystrike.shared.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.config.WorldConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.ThrownUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The shared throwable integrator — the one the server and the trajectory preview both use. */
class ThrowablePhysicsTest {

    private static final float DT = WorldConfig.TICK_SECONDS;

    /** An open column: past the top ramp step (ends x=720) and short of the crates (x=820). */
    private static final float OPEN_X = 760f;

    private final ArenaMap map = ArenaMap.standard();

    @Test
    @DisplayName("gravity is the throwable's own, far gentler than bullet drop")
    void gravityPullsDownAtTheConfiguredRate() {
        ThrownUtility grenade = grenade(OPEN_X, 1000f, 0f, 0f);
        ThrowablePhysics.stepInPlace(grenade, DT, map);

        assertEquals(UtilityConfig.GRAVITY * DT, grenade.vy, 0.01f);
        assertTrue(grenade.y < 1000f, "it should have fallen");
        assertEquals(1000f, grenade.prevY, 0.001f, "prevY is where the tick started");
    }

    @Test
    @DisplayName("step() leaves its argument alone; stepInPlace() is the allocation-free twin")
    void pureStepDoesNotMutate() {
        ThrownUtility original = grenade(OPEN_X, 1000f, 100f, 0f);
        ThrownUtility stepped = ThrowablePhysics.step(original, DT, map);

        assertEquals(1000f, original.y, 0.001f);
        assertEquals(0f, original.vy, 0.001f);
        assertTrue(stepped.y < 1000f);
        assertTrue(stepped.x > OPEN_X);
    }

    @Test
    @DisplayName("a dropped grenade settles on the ground within a couple of seconds")
    void dropSettlesOnTheGround() {
        ThrownUtility grenade = grenade(OPEN_X, 600f, 0f, 0f);

        float elapsed = 0f;
        for (int i = 0; i < 1200 && !grenade.resting; i++) {
            ThrowablePhysics.stepInPlace(grenade, DT, map);
            elapsed += DT;
        }

        assertTrue(grenade.resting, "a 0.35 restitution bounce must bleed out quickly");
        assertTrue(elapsed < UtilityConfig.MAX_LIFETIME_SECONDS,
            "it settled at " + elapsed + "s, after the lifetime cap");
        assertEquals(
            WorldConfig.GROUND_HEIGHT + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN,
            grenade.y, 0.05f);
        assertEquals(0f, grenade.vx, 0.001f);
        assertEquals(0f, grenade.vy, 0.001f);
        assertTrue(grenade.bounces > 0);
    }

    @Test
    @DisplayName("a resting throwable stays put but keeps counting down")
    void restingStillBurnsItsFuse() {
        ThrownUtility grenade = grenade(OPEN_X, 110f, 0f, 0f);
        grenade.resting = true;
        grenade.fuseRemaining = 1f;

        ThrowablePhysics.stepInPlace(grenade, DT, map);

        assertEquals(110f, grenade.y, 0.001f);
        assertEquals(1f - DT, grenade.fuseRemaining, 0.001f);
        assertEquals(DT, grenade.age, 0.001f);
    }

    @Test
    @DisplayName("bouncing keeps 0.35 of the normal component and 0.70 of the tangential one")
    void bounceSplitsNormalAndTangent() {
        ThrownUtility grenade = grenade(OPEN_X, 140f, 400f, -300f);

        float vxBefore = 0f;
        float vyBefore = 0f;
        for (int i = 0; i < 60; i++) {
            vxBefore = grenade.vx;
            vyBefore = grenade.vy;
            ThrowablePhysics.stepInPlace(grenade, DT, map);
            if (grenade.vy > 0f) {
                break;
            }
        }

        assertTrue(grenade.vy > 0f, "it should have bounced off the floor");
        // Gravity is applied inside the substep before the collision, so the ratio is measured
        // loosely around the configured 0.35 rather than exactly on it.
        float restitution = grenade.vy / -vyBefore;
        assertTrue(restitution > 0.30f && restitution < 0.40f,
            "normal restitution should sit near 0.35, was " + restitution);
        assertEquals(UtilityConfig.BOUNCE_TANGENT_FRICTION, grenade.vx / vxBefore, 0.001f);
        assertEquals(0f, grenade.contactNormalX, 0.001f);
        assertEquals(1f, grenade.contactNormalY, 0.001f, "a floor's normal points up");
    }

    @Test
    @DisplayName("hitting a wall reflects X and scrubs Y, with a sideways contact normal")
    void wallBounceUsesTheHorizontalNormal() {
        // The left boundary wall spans x = 0..40 above the ground.
        ThrownUtility grenade = grenade(200f, 400f, -600f, 0f);

        for (int i = 0; i < 120 && grenade.contactNormalX == 0f; i++) {
            ThrowablePhysics.stepInPlace(grenade, DT, map);
        }

        assertEquals(1f, grenade.contactNormalX, 0.001f, "a left-hand wall's normal points right");
        assertTrue(grenade.vx > 0f, "it should have been sent back the other way");
        assertTrue(grenade.x >= 40f + UtilityConfig.THROWABLE_RADIUS,
            "it must be pushed clear of the wall, was at x=" + grenade.x);
    }

    @Test
    @DisplayName("a fast throwable is substepped so it cannot pass through the 14-unit tunnel roof")
    void substeppingPreventsTunnelling() {
        // Thrown hard straight down at the left tunnel roof segment (x 1120..1195, y 190..204)
        // from the gap under the room floor. The column is deliberately clear of the hatch
        // opening (x 1195..1285): the hatch step's surface is higher than the roof, so dropping
        // through the opening would land on the step and prove nothing about tunnelling. At
        // 1500 u/s a whole tick covers 25 units — nearly twice the roof's thickness — so without
        // substepping this lands on the ground underneath.
        ThrownUtility grenade = grenade(1150f, 270f, 0f, -1500f);
        assertTrue(ThrowablePhysics.substepsFor(grenade, DT) > 1, "this throw needs substepping");

        for (int i = 0; i < 1200 && !grenade.resting; i++) {
            ThrowablePhysics.stepInPlace(grenade, DT, map);
        }

        assertTrue(grenade.resting);
        assertEquals(204f + UtilityConfig.THROWABLE_RADIUS + UtilityConfig.CONTACT_SKIN,
            grenade.y, 0.05f, "it fell through the tunnel roof instead of landing on it");
    }

    @Test
    @DisplayName("substep count tracks speed and is capped so a tick can never stall")
    void substepCountIsBoundedBySpeed() {
        ThrownUtility slow = grenade(OPEN_X, 500f, 60f, 0f);
        assertEquals(1, ThrowablePhysics.substepsFor(slow, DT), "1 unit of travel needs no split");

        ThrownUtility fast = grenade(OPEN_X, 500f, 850f, 0f);
        int substeps = ThrowablePhysics.substepsFor(fast, DT);
        assertTrue(substeps >= 3, "a full-force throw travels 14 units a tick, was " + substeps);
        assertTrue(850f * DT / substeps <= UtilityConfig.MAX_SUBSTEP_TRAVEL + 1e-4f);

        ThrownUtility absurd = grenade(OPEN_X, 500f, 100000f, 0f);
        assertEquals(UtilityConfig.MAX_SUBSTEPS, ThrowablePhysics.substepsFor(absurd, DT));
    }

    @Test
    @DisplayName("a hard throw stays inside the arena instead of escaping through the ceiling")
    void arenaBoundsAreNeverLeft() {
        ThrownUtility grenade = grenade(1500f, 1900f, 1200f, 1500f);

        for (int i = 0; i < 1200; i++) {
            ThrowablePhysics.stepInPlace(grenade, DT, map);
            assertTrue(grenade.x >= 0f && grenade.x <= WorldConfig.ARENA_WIDTH,
                "left the arena horizontally at x=" + grenade.x);
            assertTrue(grenade.y >= 0f && grenade.y <= WorldConfig.ARENA_HEIGHT,
                "left the arena vertically at y=" + grenade.y);
        }
    }

    @Test
    @DisplayName("throw velocity and muzzle offset follow the aim angle")
    void throwKinematicsFollowAim() {
        float force = UtilityRegistry.of(UtilityId.FRAG).throwForce();

        assertEquals(force, ThrowablePhysics.throwVelocityX(force, 0f), 0.01f);
        assertEquals(0f, ThrowablePhysics.throwVelocityY(force, 0f), 0.01f);
        assertEquals(force, ThrowablePhysics.throwVelocityY(force, 90f), 0.01f);

        float diagonalX = ThrowablePhysics.throwVelocityX(force, 45f);
        float diagonalY = ThrowablePhysics.throwVelocityY(force, 45f);
        assertEquals(force, (float) Math.hypot(diagonalX, diagonalY), 0.01f);

        assertEquals(100f + UtilityConfig.THROW_OFFSET, ThrowablePhysics.muzzleX(100f, 0f), 0.01f);
        assertEquals(200f, ThrowablePhysics.muzzleY(200f, 0f), 0.01f);
    }

    @Test
    @DisplayName("solidAt finds the surface a throwable is touching, and nothing in open air")
    void solidLookup() {
        assertNotNull(ThrowablePhysics.solidAt(map, OPEN_X, WorldConfig.GROUND_HEIGHT - 1f));
        assertNull(ThrowablePhysics.solidAt(map, OPEN_X, 800f));
        assertNull(ThrowablePhysics.solidAt(null, OPEN_X, 0f), "no map means nothing is solid");
    }

    @Test
    @DisplayName("state copies are independent, and the wire id lands in the utility range")
    void stateCopySemantics() {
        ThrownUtility original = grenade(OPEN_X, 500f, 10f, 20f);
        original.bounces = 3;
        ThrownUtility copy = original.copy();
        copy.x = 9999f;

        assertEquals(OPEN_X, original.x, 0.001f);
        assertEquals(3, copy.bounces);
        assertEquals(UtilityId.FRAG, copy.utility());
        assertEquals(UtilityId.FRAG.wireId(), copy.weaponWireId());
        assertFalse(original.hasContactNormal());
    }

    private static ThrownUtility grenade(float x, float y, float vx, float vy) {
        return new ThrownUtility(1, 7, 0, UtilityId.FRAG.ordinal(), x, y, vx, vy, 2.5f);
    }
}
