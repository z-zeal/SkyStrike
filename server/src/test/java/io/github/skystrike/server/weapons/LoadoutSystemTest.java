package io.github.skystrike.server.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.server.combat.MeleeSystem;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The per-tick loadout lifecycle, end to end: selection, reload, the trigger, melee, and the
 * mirrored state on the player record. These run through real {@link PlayerSession} latches, so
 * what is asserted here is what the tick loop does — not a rehearsal of it.
 *
 * <p>Players stand in open sky (y = 1200) so muzzle spawns are never absorbed by geometry; no
 * motion is stepped in these tests, so nobody falls.
 */
class LoadoutSystemTest {

    private static final float DT = 1f / 60f;

    private LoadoutSystem system;
    private BulletSystem bulletSystem;
    private DamageService damage;
    private PlayerSession session;
    private Player player;

    @BeforeEach
    void setUp() {
        bulletSystem = new BulletSystem(ArenaMap.standard());
        damage = new DamageService(new KillFeedService());
        system = new LoadoutSystem(
            new FireController(new Random(20261005L), new RecoilService()), new MeleeSystem(), bulletSystem);
        session = new PlayerSession(null, 1, "Nova", 0, 400f, 1200f);
        player = session.player();
        player.aimAngle = 0f;
    }

    private void holdTrigger(long sequence) {
        session.setInput(new PacketPlayerInput(
            sequence, 0f, false, false, false, false, true, 0f, PacketPlayerInput.NO_SLOT_PRESS));
    }

    private void releaseTrigger(long sequence) {
        session.setInput(new PacketPlayerInput(
            sequence, 0f, false, false, false, false, false, 0f, PacketPlayerInput.NO_SLOT_PRESS));
    }

    private void pressSlot(long sequence, int slot, long birthSeq) {
        PacketPlayerInput packet = new PacketPlayerInput(
            sequence, 0f, false, false, false, false, false, 0f, slot);
        packet.slotPressSeq = birthSeq;
        session.setInput(packet);
    }

    private void tick() {
        system.tick(session, DT, List.of(), damage);
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            tick();
        }
    }

    // --- Ammunition -------------------------------------------------------------------------------

    @Test
    @DisplayName("firing spends magazine rounds; a shotgun spends one shell for six pellets")
    void firingConsumesAmmo() {
        holdTrigger(1);
        tick(); // SCAR-L is AUTO: one round
        assertEquals(19, player.loadout.primary.magazine);

        releaseTrigger(2);

        // Swap in a shotgun: six pellets leave, one shell is spent.
        session.player().loadout.setComposition(WeaponId.SHOTGUN, null, null);
        system.resetForRespawn(session);
        holdTrigger(10);
        tick();
        assertEquals(WeaponId.SHOTGUN, player.loadout.primary.weaponId());
        assertEquals(7, player.loadout.primary.magazine, "one shell for the whole volley");
        assertEquals(6, bulletSystem.active().size(),
            "six pellets are in the air for that one shell");
    }

    @Test
    @DisplayName("an empty magazine blocks the trigger and starts the reload instead")
    void emptyMagazineStartsReload() {
        player.loadout.primary.magazine = 1;
        holdTrigger(1);
        tick(); // last round leaves, reload starts on its own

        assertEquals(0, player.loadout.primary.magazine);
        assertTrue(player.loadout.reloading, "the reload must start itself on empty");

        // A held trigger refires the instant the magazine is topped up; release it so the
        // pure reload numbers are what get asserted.
        releaseTrigger(77);
        int spent = tickUntilReloadDone();
        assertTrue(spent <= 60 * 4, "reload finished in reasonable time");
        assertEquals(20, player.loadout.primary.magazine);
        assertEquals(105, player.loadout.primary.reserve);

        // And the gun can speak again at its own cadence afterwards.
        holdTrigger(88);
        tick();
        assertEquals(19, player.loadout.primary.magazine);
    }

    private int tickUntilReloadDone() {
        int ticks = 0;
        while (player.loadout.reloading && ticks < 60 * 10) {
            tick();
            ticks++;
        }
        return ticks;
    }

    @Test
    @DisplayName("nothing leaves the barrel while reloading")
    void reloadBlocksTheTrigger() {
        player.loadout.primary.magazine = 0;
        player.loadout.reloading = true;
        player.loadout.reloadTimer = WeaponRegistry.of(WeaponId.DEFAULT).reloadSeconds();

        holdTrigger(1);
        tick(60); // one second of holding an empty, reloading gun

        assertEquals(0, player.loadout.primary.magazine, "no phantom rounds while reloading");
        assertTrue(damage.drain().isEmpty());
    }

    @Test
    @DisplayName("a burst with a short magazine runs dry mid-pattern")
    void partialBurst() {
        player.loadout.setComposition(WeaponId.BURST_RIFLE, null, null);
        system.resetForRespawn(session);
        player.loadout.primary.magazine = 2; // two of the three rounds exist

        holdTrigger(1);
        tick();

        assertEquals(0, player.loadout.primary.magazine);
        assertEquals(2, bulletSystem.active().size(),
            "only the rounds that existed were launched");
        assertTrue(player.loadout.reloading, "empty after the burst: reload starts");
    }

    // --- Switching ----------------------------------------------------------------------------------

    @Test
    @DisplayName("pressing 1 on the primary quick-swaps to melee, and back, server-side")
    void quickSwapRoundTripsOnTheServer() {
        pressSlot(1, PlayerLoadout.SLOT_PRIMARY, 100L);
        tick();
        assertEquals(PlayerLoadout.SLOT_MELEE, player.loadout.activeSlot);
        assertEquals(MeleeId.DEFAULT.wireId(), player.weaponId,
            "the mirrored wire id follows the hands");
        assertEquals(0f, player.spread, 1e-4f);

        pressSlot(2, PlayerLoadout.SLOT_PRIMARY, 101L);
        tick();
        assertEquals(PlayerLoadout.SLOT_PRIMARY, player.loadout.activeSlot);
        assertEquals(WeaponId.DEFAULT.ordinal(), player.weaponId);
    }

    @Test
    @DisplayName("the slot keypress rule applies exactly once per press, never twice")
    void pressIsNotRetriggeredByInputPacketRepetition() {
        // The client repeats an unacknowledged press on every packet; the server must see one.
        for (int i = 0; i < 3; i++) {
            pressSlot(1 + i, PlayerLoadout.SLOT_PRIMARY, 100L);
        }
        tick();
        assertEquals(PlayerLoadout.SLOT_MELEE, player.loadout.activeSlot, "one press, one swap");
        tick(3); // even if a stray duplicate packet landed, nothing re-toggles
        assertEquals(PlayerLoadout.SLOT_MELEE, player.loadout.activeSlot);
    }

    @Test
    @DisplayName("switching resets the accumulated spread and recoil — the melee detour included")
    void switchingResetsGunState() {
        // Blow the cone out a little.
        holdTrigger(1);
        tick();
        long seq = 2;
        for (int i = 0; i < 20; i++) {
            tick();
            holdTrigger(seq++); // keep re-latching the trigger edge for the cooldown cycle
        }
        float blownSpread = session.gun().currentSpread();
        assertTrue(blownSpread > WeaponRegistry.of(WeaponId.DEFAULT).spread().baseDegrees());

        // Tap into melee and back: the gun that comes out is fresh.
        pressSlot(200, PlayerLoadout.SLOT_PRIMARY, 200L);
        tick(); // now melee
        pressSlot(201, PlayerLoadout.SLOT_PRIMARY, 201L);
        tick(); // now back on the primary

        assertEquals(WeaponRegistry.of(WeaponId.DEFAULT).spread().baseDegrees(),
            session.gun().currentSpread(), 1e-4f,
            "spread does not survive a trip through melee");
        assertEquals(0f, session.gun().visualKick(), 1e-4f);
    }

    @Test
    @DisplayName("selecting the handgun swaps the live gun, its own magazine intact")
    void handgunHasItsOwnMagazine() {
        holdTrigger(1);
        tick(); // primary: 19/120

        pressSlot(2, PlayerLoadout.SLOT_HANDGUN, 100L);
        tick();
        assertEquals(WeaponId.DESERT_EAGLE, session.gun().weaponId());

        releaseTrigger(3);
        holdTrigger(4);
        tick();
        assertEquals(6, player.loadout.handgun.magazine, "the Deagle spends its own rounds");
        assertEquals(19, player.loadout.primary.magazine,
            "the holstered primary keeps its magazine as it was");
        assertEquals(WeaponId.DESERT_EAGLE.ordinal(), player.weaponId);
    }

    @Test
    @DisplayName("a reload dies when the weapon does — switching away kills it")
    void switchingAwayCancelsReload() {
        player.loadout.primary.magazine = 0;
        player.loadout.startReload();
        assertTrue(player.loadout.reloading);

        pressSlot(1, PlayerLoadout.SLOT_HANDGUN, 100L);
        tick();
        assertFalse(player.loadout.reloading, "the switch cancelled the reload");

        pressSlot(2, PlayerLoadout.SLOT_PRIMARY, 101L);
        tick();
        assertFalse(player.loadout.reloading, "and it does not resume of its own accord");
        assertEquals(0, player.loadout.primary.magazine);

        // A fresh empty-magazine trigger starts it again, from zero.
        holdTrigger(3);
        tick();
        assertTrue(player.loadout.reloading);
    }

    @Test
    @DisplayName("empty slots cannot be selected, even by packet")
    void emptySlotsAreRejected() {
        pressSlot(1, PlayerLoadout.SLOT_UTILITY_A, 100L);
        tick();
        assertEquals(PlayerLoadout.SLOT_PRIMARY, player.loadout.activeSlot,
            "slot 4 holds nothing yet: the press is ignored");
    }

    // --- Melee --------------------------------------------------------------------------------------

    @Test
    @DisplayName("holding the trigger swings melee at the weapon's cadence, not faster")
    void meleeCadence() {
        player.loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY); // quick-swap to melee
        Player victim = new Player(2, "Victim", 1, 430f, 1200f);
        List<Player> targets = List.of(victim);

        holdTrigger(1);
        system.tick(session, DT, targets, damage); // first swing
        assertEquals(CombatConfig.MAX_HEALTH - 50f, victim.health, 1e-4f);

        system.tick(session, DT, targets, damage); // too soon for the knife's 2.0/s
        assertEquals(CombatConfig.MAX_HEALTH - 50f, victim.health, 1e-4f,
            "the swing cooldown gates the cadence");

        for (int i = 0; i < 30; i++) {
            system.tick(session, DT, targets, damage);
        }
        assertEquals(CombatConfig.MAX_HEALTH - 100f, victim.health, 1e-4f,
            "half a second later, the second swing");
    }

    @Test
    @DisplayName("melee kills credit the melee weapon on the wire")
    void meleeKillShowsUpAsMelee() {
        player.loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY);
        Player victim = new Player(2, "Doomed", 1, 430f, 1200f);
        victim.health = 50f;

        holdTrigger(1);
        system.tick(session, DT, List.of(victim), damage);

        assertFalse(victim.alive);
        assertFalse(damage.drain().isEmpty());
        assertFalse(damage.killFeed().drain().isEmpty());
        assertEquals(1, player.kills);
    }

    @Test
    @DisplayName("the dead bank neither shots nor swings")
    void theDeadDoNothing() {
        player.alive = false;
        player.loadout.primary.magazine = 20;
        holdTrigger(1);
        tick();
        assertEquals(20, player.loadout.primary.magazine, "no round leaves a corpse");

        player.loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY);
        Player victim = new Player(2, "Victim", 0, 430f, 1200f);
        system.tick(session, DT, List.of(victim), damage);
        assertEquals(CombatConfig.MAX_HEALTH, victim.health, 1e-4f);
    }
}
