package io.github.skystrike.server.weapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.KillFeedService;
import io.github.skystrike.server.combat.MeleeSystem;
import io.github.skystrike.server.gadget.CameraSystem;
import io.github.skystrike.server.gadget.DroneSystem;
import io.github.skystrike.server.gadget.ShieldSystem;
import io.github.skystrike.server.gadget.SurveillanceService;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.utility.UtilitySystem;
import io.github.skystrike.shared.config.CombatConfig;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.SurveillanceView;
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
    private UtilitySystem utilitySystem;
    private DamageService damage;
    private PlayerSession session;
    private Player player;

    @BeforeEach
    void setUp() {
        ArenaMap arena = ArenaMap.standard();
        bulletSystem = new BulletSystem(arena);
        utilitySystem = new UtilitySystem(arena);
        damage = new DamageService(new KillFeedService());
        system = new LoadoutSystem(
            new FireController(new Random(20261005L), new RecoilService()), new MeleeSystem(), bulletSystem, utilitySystem);
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
    @DisplayName("firing spends magazine rounds; a shotgun spends one shell for eight pellets")
    void firingConsumesAmmo() {
        holdTrigger(1);
        tick(); // the Iron Carbine is AUTO: one round
        assertEquals(29, player.loadout.primary.magazine);

        releaseTrigger(2);

        // Swap in a shotgun: eight pellets leave, one shell is spent.
        int roundsAlreadyLive = bulletSystem.active().size(); // the carbine round from above
        session.player().loadout.setComposition(WeaponId.SCATTER_BENCH, null, null);
        system.resetForRespawn(session);
        holdTrigger(10);
        tick();
        assertEquals(WeaponId.SCATTER_BENCH, player.loadout.primary.weaponId());
        assertEquals(7, player.loadout.primary.magazine, "one shell for the whole volley");
        assertEquals(8, bulletSystem.active().size() - roundsAlreadyLive,
            "eight new pellets are in the air for that one shell");
    }

    @Test
    @DisplayName("sv_infinite_ammo leaves the magazine and reserve untouched while firing")
    void infiniteAmmoSkipsConsumption() {
        session.setInfiniteAmmo(true);
        int startingMagazine = player.loadout.primary.magazine;
        int startingReserve = player.loadout.primary.reserve;

        holdTrigger(1);
        tick();
        assertEquals(startingMagazine, player.loadout.primary.magazine,
            "infinite ammo must not spend the magazine");
        assertEquals(startingReserve, player.loadout.primary.reserve,
            "infinite ammo must not spend the reserve");
        assertTrue(bulletSystem.active().size() > 0, "the round still fires");
        assertFalse(player.loadout.reloading, "a full magazine never starts a reload");
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
        assertEquals(30, player.loadout.primary.magazine);
        // One round left the magazine before the reload started: a full 30 is topped from 120.
        assertEquals(90, player.loadout.primary.reserve);

        // And the gun can speak again at its own cadence afterwards.
        holdTrigger(88);
        tick();
        assertEquals(29, player.loadout.primary.magazine);
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
        player.loadout.setComposition(WeaponId.HALCYON_16, null, null);
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

        // Tap into melee and back: the gun that comes out is fresh. Zero the recoil-induced
        // velocity first so the stance check below is not pulled toward the moving target.
        pressSlot(200, PlayerLoadout.SLOT_PRIMARY, 200L);
        tick(); // now melee
        player.vx = 0f;
        player.vy = 0f;
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
        tick(); // primary: 29/120

        pressSlot(2, PlayerLoadout.SLOT_HANDGUN, 100L);
        tick();
        assertEquals(WeaponId.DEFAULT_SIDEARM, session.gun().weaponId());

        releaseTrigger(3);
        holdTrigger(4);
        tick();
        assertEquals(14, player.loadout.handgun.magazine, "the sidearm spends its own rounds");
        assertEquals(29, player.loadout.primary.magazine,
            "the holstered primary keeps its magazine as it was");
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(), player.weaponId);
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
    @DisplayName("depleted utility slots cannot be selected, even by packet")
    void emptySlotsAreRejected() {
        player.loadout.utilityACount = 0;
        pressSlot(1, PlayerLoadout.SLOT_UTILITY_A, 100L);
        tick();
        assertEquals(PlayerLoadout.SLOT_PRIMARY, player.loadout.activeSlot,
            "slot 4 is depleted: the press is ignored");
    }

    @Test
    @DisplayName("an equipped utility consumes one item only after a successful trigger-edge throw")
    void utilityThrowUsesEdgeInventoryAndCooldown() {
        pressSlot(1, PlayerLoadout.SLOT_UTILITY_A, 100L);
        tick();
        assertEquals(PlayerLoadout.SLOT_UTILITY_A, player.loadout.activeSlot);

        holdTrigger(2);
        tick();
        assertEquals(1, player.loadout.utilityACount);
        assertEquals(1, utilitySystem.count());
        assertTrue(session.utilityCooldownRemaining(player.loadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_A)) > 0f);

        // Holding does not empty the slot at simulation rate, and a fresh edge during cooldown
        // is still rejected by the type-specific timer.
        tick();
        assertEquals(1, player.loadout.utilityACount);
        releaseTrigger(3);
        holdTrigger(4);
        tick();
        assertEquals(1, player.loadout.utilityACount);
        assertEquals(1, utilitySystem.count());
    }

    @Test
    @DisplayName("stun slow consumes trigger edges and prevents a gun shot")
    void stunLocksWeapons() {
        player.applyStatus(0f, 1f);
        holdTrigger(1);
        tick();

        assertEquals(30, player.loadout.primary.magazine);
        assertTrue(bulletSystem.active().isEmpty());
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
        assertEquals(CombatConfig.MAX_HEALTH - 45f, victim.health, 1e-4f);

        system.tick(session, DT, targets, damage); // too soon for the knuckle's 1.5/s
        assertEquals(CombatConfig.MAX_HEALTH - 45f, victim.health, 1e-4f,
            "the swing cooldown gates the cadence");

        for (int i = 0; i < 45; i++) {
            system.tick(session, DT, targets, damage);
        }
        assertEquals(CombatConfig.MAX_HEALTH - 90f, victim.health, 1e-4f,
            "two thirds of a second later, the second swing");
    }

    @Test
    @DisplayName("melee kills credit the melee weapon on the wire")
    void meleeKillShowsUpAsMelee() {
        player.loadout.tapSlot(PlayerLoadout.SLOT_PRIMARY);
        Player victim = new Player(2, "Doomed", 1, 430f, 1200f);
        victim.health = 40f; // the knuckle's 45 finishes it

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

    // --- Surveillance lock (mechanics §7, §9) ------------------------------------------------------

    private LoadoutSystem gadgetSystem;
    private DroneSystem drones;
    private CameraSystem cameras;
    private SurveillanceService surveillance;

    private void buildGadgetLoadout() {
        ArenaMap arena = ArenaMap.standard();
        drones = new DroneSystem(arena);
        cameras = new CameraSystem(arena);
        surveillance = new SurveillanceService(drones, cameras);
        gadgetSystem = new LoadoutSystem(
            new FireController(new Random(20261005L), new RecoilService()),
            new MeleeSystem(),
            bulletSystem,
            utilitySystem,
            new ShieldSystem(),
            surveillance,
            drones,
            cameras);
    }

    private void pressGadget(long sequence, int gadgetPress, long birthSeq) {
        PacketPlayerInput packet = new PacketPlayerInput(
            sequence, 0f, false, false, false, false, false, 0f, PacketPlayerInput.NO_SLOT_PRESS);
        packet.gadgetPress = gadgetPress;
        packet.gadgetPressSeq = birthSeq;
        session.setInput(packet);
    }

    private void sendViewAction(long sequence, int viewAction, long birthSeq) {
        PacketPlayerInput packet = new PacketPlayerInput(
            sequence, 0f, false, false, false, false, false, 0f);
        packet.viewAction = viewAction;
        packet.viewActionSeq = birthSeq;
        session.setInput(packet);
    }

    @Test
    @DisplayName("a Q press on the drone slot deploys through the loadout tick's dispatch")
    void gadgetPressDeploysDrone() {
        buildGadgetLoadout();
        player.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);

        pressGadget(1, PacketPlayerInput.GADGET_Q_PRESS, 1);
        gadgetSystem.tick(session, DT, List.of(), damage);

        assertNotNull(drones.byOwner(player.id), "the drone entity exists");
        assertTrue(player.loadout.gadgetQ.active);
        assertEquals(SurveillanceView.SELF, player.surveillance());
    }

    @Test
    @DisplayName("while piloting, the body cannot fire or change slots, but gadget keys stay live")
    void surveillanceLockBlocksWeaponsAndSlots() {
        buildGadgetLoadout();
        player.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        pressGadget(1, PacketPlayerInput.GADGET_Q_PRESS, 1);
        gadgetSystem.tick(session, DT, List.of(), damage);
        pressGadget(2, PacketPlayerInput.GADGET_Q_PRESS, 2);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.DRONE, player.surveillance());

        // Fire is locked out.
        int magazine = player.loadout.primary.magazine;
        holdTrigger(3);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(magazine, player.loadout.primary.magazine, "no round leaves a piloting body");
        assertTrue(bulletSystem.active().isEmpty());

        // Slot selection is locked out.
        pressSlot(4, PlayerLoadout.SLOT_MELEE, 4);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, player.loadout.activeSlot,
            "a piloting body cannot switch weapons");

        // The gadget key is still live: Q exits the pilot view.
        pressGadget(5, PacketPlayerInput.GADGET_Q_PRESS, 5);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.SELF, player.surveillance(),
            "Q still works under the lock — mechanics §9 keeps gadget keys live");

        // And now the body can fire again.
        holdTrigger(6);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(magazine - 1, player.loadout.primary.magazine);
    }

    @Test
    @DisplayName("the view-cycle and exit edges apply under the lock, and are consumed even when dead")
    void viewActionsApplyUnderEveryLock() {
        buildGadgetLoadout();
        player.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        pressGadget(1, PacketPlayerInput.GADGET_Q_PRESS, 1);
        gadgetSystem.tick(session, DT, List.of(), damage);
        pressGadget(2, PacketPlayerInput.GADGET_Q_PRESS, 2);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.DRONE, player.surveillance());

        sendViewAction(3, PacketPlayerInput.VIEW_EXIT, 3);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.SELF, player.surveillance(), "Escape exits the pilot view");

        // Cycle back in, then a stunned pilot still gets the exit edge.
        sendViewAction(4, PacketPlayerInput.VIEW_CYCLE, 4);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.DRONE, player.surveillance());
        player.slowRemaining = 1f;
        sendViewAction(5, PacketPlayerInput.VIEW_EXIT, 5);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(SurveillanceView.SELF, player.surveillance(),
            "a stunned pilot can still escape");

        // A dead player's view edge is consumed, never banked.
        player.alive = false;
        sendViewAction(6, PacketPlayerInput.VIEW_EXIT, 6);
        gadgetSystem.tick(session, DT, List.of(), damage);
        assertEquals(PacketPlayerInput.NO_VIEW_ACTION, session.consumeViewAction(),
            "the edge was consumed by the tick");
    }

    @Test
    @DisplayName("a stunned pilot's gadget press is refused, but the drone press still deploys when fresh")
    void stunnedGadgetPressIsRefused() {
        buildGadgetLoadout();
        player.loadout.setGadgets(GadgetId.DRONE, GadgetId.NONE);
        player.slowRemaining = 1f;

        pressGadget(1, PacketPlayerInput.GADGET_Q_PRESS, 1);
        gadgetSystem.tick(session, DT, List.of(), damage);

        assertNull(drones.byOwner(player.id), "a stunned player cannot deploy");
        assertFalse(player.loadout.gadgetQ.active);
    }
}
