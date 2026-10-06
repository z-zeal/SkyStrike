package io.github.skystrike.server.weapons;

import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.DamageService;
import io.github.skystrike.server.combat.MeleeSystem;
import io.github.skystrike.server.gadget.ShieldSystem;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.utility.UtilitySystem;
import io.github.skystrike.shared.combat.SpreadMath;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.FireMode;
import io.github.skystrike.shared.weapons.MeleeDefinition;
import io.github.skystrike.shared.weapons.MeleeRegistry;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.Collection;

/**
 * The per-player, per-tick lifecycle of a loadout: selection, reload timing, the trigger, and
 * mirroring the authoritative result back onto the networked player record.
 *
 * <p>This is the one place the rules of mechanics §8 meet the live simulation, and its order is
 * load-bearing:
 * <ol>
 *   <li><b>Slot press first.</b> A latched key press applies the shared tap rule — including the
 *       quick-swap detour to melee — so this tick's trigger acts on the weapon the player asked
 *       for, not last tick's.</li>
 *   <li><b>Live gun state follows the active slot.</b> Whenever the slot changes the gun's
 *       accumulated spread, recoil and cooldown are wiped ({@link GunInstance#resetTo}), per §8:
 *       switching resets that weapon's state. Reload state is in the loadout and cancelled by the
 *       loadout's own selection rules, so the two can never disagree about it.</li>
 *   <li><b>Reload, then the trigger.</b> A reload that ends this tick frees the trigger this
 *       tick. An empty magazine blocks the trigger and starts the reload instead.</li>
 *   <li><b>Mirror last.</b> {@code weaponId}, spread and gun kick land on the player record after
 *       everything else, so the snapshot reflects this tick exactly.</li>
 * </ol>
 *
 * <p>The trigger edge is consumed once per tick whether or not the player is alive, so holding
 * the mouse through death does not bank a shot for the respawn.
 */
public final class LoadoutSystem {

    private final FireController fireController;
    private final MeleeSystem meleeSystem;
    private final BulletSystem bulletSystem;
    private final UtilitySystem utilitySystem;
    private final ShieldSystem shieldSystem;
    private final FireController.Volley volley = new FireController.Volley();

    /** Legacy construction for gun/melee-only tests; active utilities require the four-arg form. */
    public LoadoutSystem(FireController fireController, MeleeSystem meleeSystem, BulletSystem bulletSystem) {
        this(fireController, meleeSystem, bulletSystem, null, new ShieldSystem());
    }

    public LoadoutSystem(
        FireController fireController,
        MeleeSystem meleeSystem,
        BulletSystem bulletSystem,
        UtilitySystem utilitySystem
    ) {
        this(fireController, meleeSystem, bulletSystem, utilitySystem, new ShieldSystem());
    }

    public LoadoutSystem(
        FireController fireController,
        MeleeSystem meleeSystem,
        BulletSystem bulletSystem,
        UtilitySystem utilitySystem,
        ShieldSystem shieldSystem
    ) {
        this.fireController = fireController;
        this.meleeSystem = meleeSystem;
        this.bulletSystem = bulletSystem;
        this.utilitySystem = utilitySystem;
        this.shieldSystem = shieldSystem == null ? new ShieldSystem() : shieldSystem;
    }

    /**
     * Steps one session's loadout by one tick.
     *
     * @param targets every live player in the match — melee swings resolve against them
     */
    public void tick(PlayerSession session, float dt, Collection<? extends Player> targets, DamageService damage) {
        Player player = session.player();
        PlayerLoadout loadout = player.loadout;
        GunInstance gun = session.gun();
        session.tickUtilityCooldowns(dt);

        // 1. A latched slot press applies the same tap rule the client's prediction ran.
        int slotPress = session.consumeSlotPress();
        if (loadout != null && slotPress != PacketPlayerInput.NO_SLOT_PRESS) {
            loadout.tapSlot(slotPress);
        }

        // 2. Q/E edges are retired before alive/stun checks. A rejected edge is never carried
        // through death or stun. Passive gadgets (the fuel tank) deliberately do nothing here.
        int gadgetPress = session.consumeGadgetPress();
        boolean firePressed = session.consumeFirePressed();
        if (!player.alive || loadout == null) {
            return;
        }
        if (player.isSlowed()) {
            mirror(player, loadout, gun);
            return;
        }
        if (gadgetPress != PacketPlayerInput.NO_GADGET_PRESS) {
            shieldSystem.toggle(player, gadgetPress);
        }

        // 3. An equipped shield forces the handgun when possible. With no handgun every weapon
        // action is blocked below; it is safer than silently granting melee or primary fire.
        boolean shieldEquipped = shieldSystem.enforceHandgunOnly(player);
        if (shieldEquipped && shieldSystem.blocksWeaponAction(player)) {
            syncGunForSlot(session, loadout, gun);
            mirror(player, loadout, gun);
            return;
        }

        // The live gun state belongs to whatever is actually in hand. A slot change wipes
        // spread, recoil and cooldown — even when the same gun comes back out of a melee detour.
        syncGunForSlot(session, loadout, gun);

        if (loadout.utilityActive()) {
            tickUtility(player, session, loadout, firePressed);
            // Consuming the final throwable cycles to a real slot during this tick. Synchronise
            // immediately so the snapshot does not show that new gun with stale recoil/spread.
            syncGunForSlot(session, loadout, gun);
        } else if (loadout.meleeActive()) {
            tickMelee(player, session, loadout, targets, damage, dt);
        } else {
            tickGun(player, session, gun, loadout, dt, firePressed);
        }

        // 4. Mirror the authoritative result onto the networked player record.
        mirror(player, loadout, gun);
    }

    /** Aligns the session's volatile gun state after any active-slot transition. */
    private static void syncGunForSlot(PlayerSession session, PlayerLoadout loadout, GunInstance gun) {
        if (loadout == null || loadout.activeSlot == session.lastMirroredSlot()) {
            return;
        }
        WeaponItem item = loadout.activeItem();
        if (item != null && item.weaponId() != null) {
            gun.resetTo(item.weaponId());
        }
        session.setLastMirroredSlot(loadout.activeSlot);
    }

    /**
     * Utilities throw on the trigger edge, never the held level: an automatic rifle may hose
     * while held, but holding a grenade key cannot empty both slots at tick rate. Inventory is
     * consumed only after the server accepted the spawn and the type-specific cooldown starts.
     */
    private void tickUtility(
        Player player,
        PlayerSession session,
        PlayerLoadout loadout,
        boolean firePressed
    ) {
        if (!firePressed || utilitySystem == null) {
            return;
        }
        UtilityId utility = loadout.activeUtilityId();
        if (utility == null || !session.utilityReady(utility)) {
            return;
        }
        if (utilitySystem.throwUtility(player, utility)) {
            loadout.consumeActiveUtility();
            session.startUtilityCooldown(utility, UtilityRegistry.of(utility).cooldownSeconds());
        }
    }

    private static void mirror(Player player, PlayerLoadout loadout, GunInstance gun) {
        player.weaponId = loadout.heldWeaponWireId();
        if (loadout.meleeActive() || loadout.utilityActive()) {
            player.spread = 0f;
            player.gunKick = 0f;
        } else {
            player.spread = gun.currentSpread();
            player.gunKick = gun.visualKick();
        }
    }

    /** Held-trigger swinging at the weapon's cadence; the knife cannot outrun its own 2.0/s. */
    private void tickMelee(
            Player player,
            PlayerSession session,
            PlayerLoadout loadout,
            Collection<? extends Player> targets,
            DamageService damage,
            float dt) {

        session.tickMeleeCooldown(dt);
        if (!session.triggerHeld() || !session.meleeSwingReady()) {
            return;
        }
        MeleeDefinition melee = MeleeRegistry.of(loadout.meleeId());
        meleeSystem.swing(player, melee, targets, damage);
        session.startMeleeCooldown(melee.swingCooldownSeconds());
    }

    /** Guns: reload first, then the trigger, then the magazine book-keeping. */
    private void tickGun(
            Player player,
            PlayerSession session,
            GunInstance gun,
            PlayerLoadout loadout,
            float dt,
            boolean firePressed) {

        gun.update(dt, SpreadMath.isMoving(player.vx), player.ads);
        loadout.updateReload(dt);

        WeaponItem item = loadout.activeItem();
        if (item == null) {
            return;
        }

        if (!item.hasRounds()) {
            // The trigger on an empty magazine starts the reload instead of firing.
            if (session.triggerHeld() || firePressed) {
                loadout.startReload();
            }
            return;
        }
        if (loadout.reloading) {
            return;
        }

        int produced = fireController.fire(player, gun, session.triggerHeld(), firePressed, volley);
        if (produced == 0) {
            return;
        }

        // A pellet weapon's magazine counts shells: the whole cloud costs one. A burst costs
        // what it fired — less than three when the magazine ran dry mid-burst.
        FireMode fireMode = gun.definition().fireMode();
        int cost = fireMode == FireMode.BURST
            ? Math.min(gun.definition().magazineCostPerTriggerEvent(), item.magazine)
            : 1;
        int roundsLaunched = fireMode == FireMode.BURST ? Math.min(produced, cost) : produced;
        for (int i = 0; i < roundsLaunched; i++) {
            bulletSystem.spawn(player, gun.weaponId(), volley.angle(i));
        }
        // sv_infinite_ammo (build plan M3 §4): the session that fired keeps its rounds.
        if (!session.infiniteAmmo()) {
            item.consume(cost);
        }

        // The magazine hitting zero starts the reload on its own; nobody rations a reload.
        if (!item.hasRounds()) {
            loadout.startReload();
        }
    }

    /**
     * Rebuilds a session's live weapon state for a respawn: fresh gun state for whatever the
     * loadout now holds, no swing cooldown, no banked trigger press.
     */
    public void resetForRespawn(PlayerSession session) {
        PlayerLoadout loadout = session.player().loadout;
        WeaponId held = loadout == null ? null : loadout.heldGunId();
        session.gun().resetTo(held == null ? WeaponId.DEFAULT : held);
        session.resetMeleeCooldown();
        session.resetUtilityCooldowns();
        session.clearTrigger();
        if (loadout != null) {
            session.setLastMirroredSlot(loadout.activeSlot);
        }
    }

    /** The volley buffer, exposed for tests that want to count what would have been fired. */
    public FireController.Volley volley() {
        return volley;
    }
}
