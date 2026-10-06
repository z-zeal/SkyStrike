package io.github.skystrike.server.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.config.UtilityConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Server authority tests for the throwable lifecycle's effect hand-off. */
class UtilitySystemTest {

    private UtilitySystem utilities;
    private DamageService damage;

    @BeforeEach
    void setUp() {
        utilities = new UtilitySystem(ArenaMap.standard());
        damage = new DamageService(new KillFeedService());
    }

    @Test
    @DisplayName("a successful throw starts at the shared muzzle with its registry velocity")
    void throwBuildsAuthoritativeFlightState() {
        Player owner = new Player(1, "Nova", 0, 760f, 1000f);
        owner.aimAngle = 0f;

        assertTrue(utilities.throwUtility(owner, UtilityId.FRAG));
        assertEquals(1, utilities.count());
        ThrownUtility thrown = utilities.active().get(0);
        assertEquals(owner.eyeX() + UtilityConfig.THROW_OFFSET, thrown.x, 0.001f);
        assertEquals(owner.eyeY(), thrown.y, 0.001f);
        assertEquals(UtilityRegistry.of(UtilityId.FRAG).throwForce(), thrown.vx, 0.001f);
        assertEquals(0f, thrown.vy, 0.001f);
        assertEquals(owner.aimAngle, thrown.aimAngle, 0.001f);
    }

    @Test
    @DisplayName("a frag delegates one occluded blast through DamageService and applies impulse")
    void fragDamagesAndPushesOnlyVisibleTargets() {
        Player owner = player(1, 760f, 975f);
        Player target = player(2, 810f, 975f); // centre at the detonation height
        ThrownUtility frag = thrown(UtilityId.FRAG, owner, 760f, 1000f);

        utilities.detonate(frag, UtilityRegistry.of(UtilityId.FRAG), List.of(owner, target), damage);

        assertTrue(target.health < CombatConfig.MAX_HEALTH);
        assertTrue(target.vx > 0f, "blast pushes right, away from the centre");
        assertEquals(2, damage.drain().size(), "owner and target are each hit once");
    }

    @Test
    @DisplayName("a stun stores blindness and slow on the player, while cover reduces it to concussion")
    void stunStatusIsAuthoritative() {
        Player clear = player(2, 810f, 975f);
        clear.aimAngle = 180f; // detonation is to the left
        ThrownUtility stun = thrown(UtilityId.STUN, player(1, 760f, 975f), 760f, 1000f);

        utilities.detonate(stun, UtilityRegistry.of(UtilityId.STUN), List.of(clear), damage);

        assertEquals(UtilityConfig.STUN_INNER_BLIND_SECONDS, clear.blindRemaining, 0.001f);
        assertEquals(UtilityConfig.STUN_INNER_SLOW_SECONDS, clear.slowRemaining, 0.001f);
        assertTrue(clear.isSlowed());

        Player sheltered = player(3, 1100f, 100f);
        sheltered.aimAngle = 180f; // pillar, not facing, causes the reduced effect
        ThrownUtility behindPillar = thrown(UtilityId.STUN, player(1, 980f, 100f), 980f, 142.5f);
        utilities.detonate(behindPillar, UtilityRegistry.of(UtilityId.STUN), List.of(sheltered), damage);

        assertEquals(UtilityConfig.STUN_NO_SIGHT_CONCUSSION_SECONDS, sheltered.blindRemaining, 0.001f);
        assertFalse(sheltered.isSlowed());
    }

    @Test
    @DisplayName("smoke zones feed the exact opaque volume list used by gameplay sight queries")
    void smokeBecomesZoneAndVisionVolume() {
        Player owner = player(1, 760f, 975f);
        ThrownUtility smoke = thrown(UtilityId.SMOKE, owner, 760f, 1000f);

        utilities.detonate(smoke, UtilityRegistry.of(UtilityId.SMOKE), List.of(owner), damage);
        utilities.step(1f / 60f, List.of(owner), damage);

        assertEquals(1, utilities.zoneCount());
        assertEquals(1, utilities.smokeVolumes().size());
        assertEquals(UtilityRegistry.of(UtilityId.SMOKE).radius(), utilities.smokeVolumes().get(0).radius(), 0.001f);
        assertTrue(utilities.zones().get(0).blocksVision());
    }

    @Test
    @DisplayName("molotov spread makes seven surface-tangent zones but damages a player once per DOT clock")
    void molotovZonesDoNotStackOnOverlap() {
        Player owner = player(1, 760f, 475f);
        Player target = player(2, 760f, 475f);
        ThrownUtility molotov = thrown(UtilityId.MOLOTOV, owner, 760f, 500f);
        molotov.contactNormalX = 0f;
        molotov.contactNormalY = 1f; // floor: spread left/right

        utilities.detonate(molotov, UtilityRegistry.of(UtilityId.MOLOTOV), List.of(owner, target), damage);
        utilities.step(UtilityConfig.DOT_TICK_SECONDS, List.of(owner, target), damage);

        assertEquals(1 + UtilityConfig.FIRE_SPREAD_ZONES_PER_SIDE * 2, utilities.zoneCount());
        assertEquals(CombatConfig.MAX_HEALTH - UtilityRegistry.of(UtilityId.MOLOTOV).damage(),
            target.health, 0.001f, "overlapping spread circles extend area rather than seven-stack damage");
    }

    private static Player player(int id, float x, float y) {
        return new Player(id, "P" + id, id == 1 ? 0 : 1, x, y);
    }

    private static ThrownUtility thrown(UtilityId id, Player owner, float x, float y) {
        ThrownUtility thrown = new ThrownUtility(
            10, owner.id, owner.teamIndex, id.ordinal(), x, y, 0f, 0f, 0f);
        thrown.aimAngle = owner.aimAngle;
        return thrown;
    }
}
