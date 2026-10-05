package io.github.skystrike.server.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.WeaponConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.MeleeRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The melee arc and the impulse behind it (mechanics §5.2). Every swing is tested through the
 * shared {@link DamageService} death path, because that is the point: melee kills look exactly
 * like bullet kills downstream.
 */
class MeleeSystemTest {

    private static final float EPSILON = 1e-4f;

    private MeleeSystem melee;
    private DamageService damage;
    private KillFeedService killFeed;
    private Player attacker;

    @BeforeEach
    void setUp() {
        melee = new MeleeSystem();
        killFeed = new KillFeedService();
        damage = new DamageService(killFeed);
        attacker = new Player(1, "Bruiser", 0, 500f, 100f);
        attacker.aimAngle = 0f;
    }

    private Player targetAt(float x, float y) {
        Player target = new Player(2, "Victim", 1, x, y);
        return target;
    }

    @Test
    @DisplayName("a swing in reach and in front damages every player in the arc")
    void swingHitsArc() {
        Player front = targetAt(540f, 100f); // 40 units dead ahead
        Player alsoFront = targetAt(500f, 140f); // 40 units straight up
        int hits = melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE),
            List.of(front, alsoFront, attacker), damage);

        assertEquals(1, hits, "the one above is outside the ±60° wedge of a level aim");
        assertEquals(CombatConfig.MAX_HEALTH - 45f, front.health, EPSILON);
        assertEquals(CombatConfig.MAX_HEALTH, alsoFront.health, EPSILON);
        assertEquals(CombatConfig.MAX_HEALTH, attacker.health, EPSILON, "you cannot punch yourself");
    }

    @Test
    @DisplayName("a swing reaches nothing behind the attacker or beyond the weapon's reach")
    void swingMissesBehindAndFar() {
        Player behind = targetAt(460f, 100f); // 40 units dead behind
        Player far = targetAt(500f + 65f, 100f); // one unit past the knuckle's 64
        Player inside = targetAt(500f + 64f, 100f); // exactly at range: the boundary counts

        int hits = melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE),
            List.of(behind, far, inside), damage);

        assertEquals(1, hits);
        assertEquals(CombatConfig.MAX_HEALTH, behind.health, EPSILON);
        assertEquals(CombatConfig.MAX_HEALTH, far.health, EPSILON);
        assertTrue(inside.health < CombatConfig.MAX_HEALTH, "the range boundary is inclusive");
    }

    @Test
    @DisplayName("the wedge boundary is ±half-angle, inclusive")
    void arcBoundary() {
        float half = WeaponConfig.MELEE_ARC_HALF_ANGLE_DEGREES;
        assertTrue(MeleeSystem.inArc(0f, 0f, 0f, 10f, 0f, 20f));
        assertFalse(MeleeSystem.inArc(0f, 0f, 180f, 10f, 0f, 20f), "behind");
        assertFalse(MeleeSystem.inArc(0f, 0f, 0f, 21f, 0f, 20f), "out of range");

        // exactly at the half-angle in both directions
        float radIn = (float) Math.toRadians(half);
        assertTrue(MeleeSystem.inArc(0f, 0f, 0f,
            10f * (float) Math.cos(radIn), 10f * (float) Math.sin(radIn), 20f), "+" + half + "°");
        assertTrue(MeleeSystem.inArc(0f, 0f, 0f,
            10f * (float) Math.cos(radIn), -10f * (float) Math.sin(radIn), 20f), "-" + half + "°");

        // just outside it
        float radOut = (float) Math.toRadians(half + 5f);
        assertFalse(MeleeSystem.inArc(0f, 0f, 0f,
            10f * (float) Math.cos(radOut), 10f * (float) Math.sin(radOut), 20f));
    }

    @Test
    @DisplayName("knockback is a real impulse along attacker → victim, at the table magnitude")
    void knockbackImpulse() {
        Player front = targetAt(540f, 100f); // dead ahead: direction is (1, 0)
        melee.swing(attacker, MeleeRegistry.of(MeleeId.YARD_WRENCH), List.of(front), damage);

        assertEquals(310f, front.vx, EPSILON, "the wrench shoves at 310 units/s");
        assertEquals(0f, front.vy, EPSILON);

        // Diagonal victim: the impulse splits across the axes but keeps its magnitude.
        attacker = new Player(3, "Shover", 0, 0f, 0f);
        attacker.aimAngle = 45f;
        Player diagonal = new Player(4, "Launched", 1, 30f, 30f);
        diagonal.grounded = false; // airborne, the case the mechanics plan calls out
        melee.swing(attacker, MeleeRegistry.of(MeleeId.YARD_WRENCH), List.of(diagonal), damage);

        float magnitude = (float) Math.sqrt(diagonal.vx * diagonal.vx + diagonal.vy * diagonal.vy);
        assertEquals(310f, magnitude, 1e-3f);
        assertTrue(diagonal.vx > 0f && diagonal.vy > 0f,
            "an airborne enemy is genuinely launched, up and away");
    }

    @Test
    @DisplayName("a killing blow does not launch the body")
    void killingBlowDoesNotLaunch() {
        Player dying = targetAt(540f, 100f);
        dying.health = 40f; // the knuckle takes 45
        int hits = melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE), List.of(dying), damage);

        assertEquals(1, hits);
        assertFalse(dying.alive);
        assertEquals(0f, dying.vx, EPSILON, "the dead drop out of the simulation, they do not sail");
        assertEquals(0f, dying.vy, EPSILON);

        // And the kill feed learned it was a knife, as a wire id that cannot be a gun.
        assertEquals(1, killFeed.pendingCount());
        KillFeedService.KillEvent event = killFeed.drain().get(0);
        assertEquals(MeleeId.TRENCH_KNUCKLE.wireId(), event.weaponId());
        assertFalse(event.headshot(), "melee has no headshots");
    }

    @Test
    @DisplayName("damage events carry the melee wire id and the body zone")
    void damageEventShape() {
        Player front = targetAt(540f, 100f);
        melee.swing(attacker, MeleeRegistry.of(MeleeId.ASH_MACHETE), List.of(front), damage);

        List<DamageService.DamageResult> results = damage.drain();
        assertEquals(1, results.size());
        DamageService.DamageResult result = results.get(0);
        assertEquals(MeleeId.ASH_MACHETE.wireId(), result.weaponId());
        assertEquals(HitZone.BODY, result.zone());
        assertEquals(56f, result.amount(), EPSILON);
        assertFalse(result.killed());
    }

    @Test
    @DisplayName("friendly fire is respected: teammates are valid targets while it is on")
    void friendlyFireIsRespected() {
        Player teammates = new Player(5, "Mate", 0, 540f, 100f); // same team as the attacker
        int hits = melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE),
            List.of(teammates), damage);

        assertTrue(CombatConfig.FRIENDLY_FIRE, "this test only means anything with FF on");
        assertEquals(1, hits);
        assertEquals(CombatConfig.MAX_HEALTH - 45f, teammates.health, EPSILON);
    }

    @Test
    @DisplayName("the dead do not swing, and the dead are not hit")
    void theDeadAreOut() {
        Player corpse = targetAt(540f, 100f);
        corpse.alive = false;
        assertEquals(0, melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE),
            List.of(corpse), damage));

        attacker.alive = false;
        Player freshVictim = targetAt(540f, 100f);
        freshVictim.health = CombatConfig.MAX_HEALTH;
        assertEquals(0, melee.swing(attacker, MeleeRegistry.of(MeleeId.TRENCH_KNUCKLE),
            List.of(freshVictim), damage));
        assertEquals(CombatConfig.MAX_HEALTH, freshVictim.health, EPSILON);
    }

    @Test
    @DisplayName("a swing against nobody and against nothing is a quiet no-op")
    void degenerateSwings() {
        assertEquals(0, melee.swing(attacker, MeleeRegistry.of(MeleeId.WINTER_KATANA), null, damage));
        assertEquals(0, melee.swing(attacker, null, List.of(), damage));
        assertEquals(0, damage.pendingCount(), "no damage events either");
    }
}
