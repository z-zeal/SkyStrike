package io.github.skystrike.server.player;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.weapons.GunInstance;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.physics.PlayerInput;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;

/**
 * Server-side session binding a network {@link Connection} to its authoritative {@link Player}.
 *
 * <p>Also holds the live gun state, the melee swing clock, and the two <b>input edges</b> the
 * tick loop consumes. The edges matter: input arrives unreliably at roughly the frame rate while
 * the simulation consumes one input per tick, so a semi-automatic tap or a slot press that starts
 * and ends between two ticks would otherwise be swallowed. Every arriving packet that raises
 * {@code fire} latches a pending press, and one that carries {@code slotPress} replaces the
 * pending press; the tick consumes them.
 *
 * <p>The requested five-slot loadout composition lives here as ordinals, initialised to the
 * standard loadout. A {@link PacketLoadoutUpdate} edits them; they are applied to the player at
 * the next respawn, the one moment a loadout is legitimately rebuilt.
 */
public final class PlayerSession {

    private final Connection connection;
    private final int playerId;
    private final String name;
    private final Player player;
    private final GunInstance gun;

    private volatile PlayerInput latestInput = new PlayerInput();
    private volatile long lastInputTimeMillis;
    private volatile boolean triggerHeld;
    private volatile boolean firePressedPending;

    /**
     * The slot-press latch. A press is an edge the client retransmits until the server
     * acknowledges it, so the latch deduplicates by the press's birth sequence: each birth is
     * latched at most once before the tick consumes it, and applied at most once ever.
     */
    private volatile int slotPressPending = PacketPlayerInput.NO_SLOT_PRESS;
    private volatile long slotPressSeqPending = -1L;
    private volatile long lastSlotPressSeqApplied = -1L;

    /** Q/E gadget edge latch, deduplicated by the client's first carrying sequence. */
    private volatile int gadgetPressPending = PacketPlayerInput.NO_GADGET_PRESS;
    private volatile long gadgetPressSeqPending = -1L;
    private volatile long lastGadgetPressSeqApplied = -1L;

    /** The slot the gun state was last aligned with, so a change forces a fresh weapon. */
    private int lastMirroredSlot;

    private float meleeCooldownRemaining;

    /** Per-utility cooldowns, keyed by the append-only {@link UtilityId} ordinal. */
    private final float[] utilityCooldowns = new float[UtilityId.values().length];

    private int requestedPrimary;
    private int requestedHandgun;
    private int requestedMelee;
    private int requestedUtilityA;
    private int requestedUtilityB;
    private int requestedGadgetQ;
    private int requestedGadgetE;

    public PlayerSession(Connection connection, int playerId, String name, int teamIndex, float spawnX, float spawnY) {
        this.connection = connection;
        this.playerId = playerId;
        this.name = name;
        this.player = new Player(playerId, name, teamIndex, spawnX, spawnY);

        PlayerLoadout loadout = player.loadout;
        this.requestedPrimary = loadout.primary == null ? PacketLoadoutUpdate.KEEP_CURRENT : loadout.primary.weapon;
        this.requestedHandgun = loadout.handgun == null ? PacketLoadoutUpdate.KEEP_CURRENT : loadout.handgun.weapon;
        this.requestedMelee = loadout.melee;
        this.requestedUtilityA = loadout.utilityA;
        this.requestedUtilityB = loadout.utilityB;
        this.requestedGadgetQ = loadout.gadgetQ.gadget;
        this.requestedGadgetE = loadout.gadgetE.gadget;

        WeaponId held = loadout.heldGunId();
        this.gun = new GunInstance(held == null ? WeaponId.DEFAULT : held);
        this.lastMirroredSlot = loadout.activeSlot;
        this.player.weaponId = loadout.heldWeaponWireId();
        this.lastInputTimeMillis = System.currentTimeMillis();
    }

    public Connection connection() {
        return connection;
    }

    public int playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    public Player player() {
        return player;
    }

    /** Live spread and recoil state for the weapon this player is holding. */
    public GunInstance gun() {
        return gun;
    }

    public PlayerInput latestInput() {
        return latestInput;
    }

    public void setInput(PacketPlayerInput packet) {
        if (packet == null || packet.sequence < latestInput.sequence) {
            return;
        }
        if (packet.fire && !triggerHeld) {
            firePressedPending = true;
        }
        triggerHeld = packet.fire;
        if (packet.slotPress != PacketPlayerInput.NO_SLOT_PRESS
            && packet.slotPressSeq > lastSlotPressSeqApplied
            && packet.slotPressSeq != slotPressSeqPending) {
            slotPressPending = packet.slotPress;
            slotPressSeqPending = packet.slotPressSeq;
        }
        if ((packet.gadgetPress == PacketPlayerInput.GADGET_Q_PRESS
                || packet.gadgetPress == PacketPlayerInput.GADGET_E_PRESS)
            && packet.gadgetPressSeq > lastGadgetPressSeqApplied
            && packet.gadgetPressSeq != gadgetPressSeqPending) {
            gadgetPressPending = packet.gadgetPress;
            gadgetPressSeqPending = packet.gadgetPressSeq;
        }
        this.latestInput = PlayerInput.fromPacket(packet);
        this.lastInputTimeMillis = System.currentTimeMillis();
    }

    /** True while the trigger is down. Drives automatic weapons and melee swinging. */
    public boolean triggerHeld() {
        return triggerHeld;
    }

    /**
     * Returns whether the trigger went down since the last tick, and clears the latch.
     *
     * <p>Tick thread only — one consumer, exactly once per press.
     */
    public boolean consumeFirePressed() {
        if (!firePressedPending) {
            return false;
        }
        firePressedPending = false;
        return true;
    }

    /**
     * Returns the latched slot press and clears it, or {@code NO_SLOT_PRESS} when none is
     * waiting or the latch holds an already-applied retransmission. Tick thread only — the tap
     * rule must run exactly once per press.
     */
    public int consumeSlotPress() {
        int press = slotPressPending;
        long seq = slotPressSeqPending;
        slotPressPending = PacketPlayerInput.NO_SLOT_PRESS;
        slotPressSeqPending = -1L;
        if (press == PacketPlayerInput.NO_SLOT_PRESS || seq <= lastSlotPressSeqApplied) {
            return PacketPlayerInput.NO_SLOT_PRESS;
        }
        lastSlotPressSeqApplied = seq;
        return press;
    }

    /**
     * Returns one Q/E edge and retires its birth sequence. The caller must invoke this even for a
     * dead or stunned player; only the caller decides whether the consumed edge is allowed to act.
     */
    public int consumeGadgetPress() {
        int press = gadgetPressPending;
        long seq = gadgetPressSeqPending;
        gadgetPressPending = PacketPlayerInput.NO_GADGET_PRESS;
        gadgetPressSeqPending = -1L;
        if (press == PacketPlayerInput.NO_GADGET_PRESS || seq <= lastGadgetPressSeqApplied) {
            return PacketPlayerInput.NO_GADGET_PRESS;
        }
        lastGadgetPressSeqApplied = seq;
        return press;
    }

    /** Drops every pending edge, used when the player dies or respawns holding the mouse. */
    public void clearTrigger() {
        firePressedPending = false;
        triggerHeld = false;
        if (slotPressSeqPending > lastSlotPressSeqApplied) {
            lastSlotPressSeqApplied = slotPressSeqPending;
        }
        if (gadgetPressSeqPending > lastGadgetPressSeqApplied) {
            lastGadgetPressSeqApplied = gadgetPressSeqPending;
        }
        slotPressPending = PacketPlayerInput.NO_SLOT_PRESS;
        slotPressSeqPending = -1L;
        gadgetPressPending = PacketPlayerInput.NO_GADGET_PRESS;
        gadgetPressSeqPending = -1L;
    }

    /** Drops only a pending gadget edge, for a death detected after the input was received. */
    public void clearGadgetPress() {
        if (gadgetPressSeqPending > lastGadgetPressSeqApplied) {
            lastGadgetPressSeqApplied = gadgetPressSeqPending;
        }
        gadgetPressPending = PacketPlayerInput.NO_GADGET_PRESS;
        gadgetPressSeqPending = -1L;
    }

    public long lastInputTimeMillis() {
        return lastInputTimeMillis;
    }

    // --- Melee clock ----------------------------------------------------------------------------

    /** Ticks the swing cooldown; swings are legal only when it has elapsed. */
    public void tickMeleeCooldown(float dt) {
        if (dt > 0f) {
            meleeCooldownRemaining = Math.max(0f, meleeCooldownRemaining - dt);
        }
    }

    public boolean meleeSwingReady() {
        return meleeCooldownRemaining <= 0f;
    }

    /** Starts the weapon's swing cooldown after a swing. */
    public void startMeleeCooldown(float seconds) {
        meleeCooldownRemaining = Math.max(0f, seconds);
    }

    public void resetMeleeCooldown() {
        meleeCooldownRemaining = 0f;
    }

    public float meleeCooldownRemaining() {
        return meleeCooldownRemaining;
    }

    // --- Utility cooldowns -----------------------------------------------------------------------

    /** Advances every per-utility throw cooldown. Tick thread only. */
    public void tickUtilityCooldowns(float dt) {
        if (dt <= 0f) {
            return;
        }
        for (int i = 0; i < utilityCooldowns.length; i++) {
            utilityCooldowns[i] = Math.max(0f, utilityCooldowns[i] - dt);
        }
    }

    /** True when this exact utility type may be thrown again. */
    public boolean utilityReady(UtilityId id) {
        return id != null && utilityCooldowns[id.ordinal()] <= 0f;
    }

    /** Starts this utility type's cooldown after a successful authoritative placement/throw. */
    public void startUtilityCooldown(UtilityId id, float seconds) {
        if (id != null) {
            utilityCooldowns[id.ordinal()] = Math.max(0f, seconds);
        }
    }

    /** A respawn refills inventory and clears every per-life utility delay. */
    public void resetUtilityCooldowns() {
        java.util.Arrays.fill(utilityCooldowns, 0f);
    }

    /** Remaining cooldown for HUD/debug tests; invalid types report zero. */
    public float utilityCooldownRemaining(UtilityId id) {
        return id == null ? 0f : utilityCooldowns[id.ordinal()];
    }

    // --- Live-state mirroring --------------------------------------------------------------------

    /** The loadout slot the gun state was last aligned with. */
    public int lastMirroredSlot() {
        return lastMirroredSlot;
    }

    public void setLastMirroredSlot(int slot) {
        this.lastMirroredSlot = slot;
    }

    // --- Loadout composition ----------------------------------------------------------------------

    /**
     * Records a requested composition change. {@code KEEP_CURRENT} components are left alone.
     * The request takes effect at the next respawn — mid-life re-arming is not a thing.
     */
    public void requestLoadout(int primaryOrdinal, int handgunOrdinal, int meleeOrdinal) {
        requestLoadout(primaryOrdinal, handgunOrdinal, meleeOrdinal,
            PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT);
    }

    public void requestLoadout(
        int primaryOrdinal,
        int handgunOrdinal,
        int meleeOrdinal,
        int utilityAOrdinal,
        int utilityBOrdinal
    ) {
        requestLoadout(primaryOrdinal, handgunOrdinal, meleeOrdinal, utilityAOrdinal, utilityBOrdinal,
            PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT);
    }

    public void requestLoadout(
        int primaryOrdinal,
        int handgunOrdinal,
        int meleeOrdinal,
        int utilityAOrdinal,
        int utilityBOrdinal,
        int gadgetQOrdinal,
        int gadgetEOrdinal
    ) {
        if (primaryOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedPrimary = primaryOrdinal;
        }
        if (handgunOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedHandgun = handgunOrdinal;
        }
        if (meleeOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedMelee = meleeOrdinal;
        }
        if (utilityAOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedUtilityA = utilityAOrdinal;
        }
        if (utilityBOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedUtilityB = utilityBOrdinal;
        }
        if (gadgetQOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedGadgetQ = gadgetQOrdinal;
        }
        if (gadgetEOrdinal != PacketLoadoutUpdate.KEEP_CURRENT) {
            this.requestedGadgetE = gadgetEOrdinal;
        }
    }

    /**
     * Rebuilds the player's loadout from the requested composition, with full magazines. Called
     * by the respawn flow, after the position and health reset.
     *
     * @return true when any component actually changed
     */
    public boolean applyRequestedLoadout() {
        PlayerLoadout loadout = player.loadout;
        WeaponId before1 = loadout.primary == null ? null : loadout.primary.weaponId();
        WeaponId before2 = loadout.handgun == null ? null : loadout.handgun.weaponId();
        MeleeId before3 = loadout.meleeId();
        UtilityId before4 = loadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_A);
        UtilityId before5 = loadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_B);
        GadgetId beforeQ = loadout.gadgetQ.gadgetId();
        GadgetId beforeE = loadout.gadgetE.gadgetId();

        WeaponId primaryId = WeaponId.isValidOrdinal(requestedPrimary)
            ? WeaponId.fromOrdinal(requestedPrimary) : null;
        WeaponId handgunId = WeaponId.isValidOrdinal(requestedHandgun)
            ? WeaponId.fromOrdinal(requestedHandgun) : null;
        MeleeId meleeId = MeleeId.isValidOrdinal(requestedMelee)
            ? MeleeId.fromOrdinal(requestedMelee) : null;
        UtilityId utilityAId = UtilityId.isValidOrdinal(requestedUtilityA)
            ? UtilityId.fromOrdinal(requestedUtilityA) : null;
        UtilityId utilityBId = UtilityId.isValidOrdinal(requestedUtilityB)
            ? UtilityId.fromOrdinal(requestedUtilityB) : null;
        GadgetId gadgetQId = GadgetId.isValidOrdinal(requestedGadgetQ)
            ? GadgetId.fromOrdinal(requestedGadgetQ) : null;
        GadgetId gadgetEId = GadgetId.isValidOrdinal(requestedGadgetE)
            ? GadgetId.fromOrdinal(requestedGadgetE) : null;
        loadout.setComposition(primaryId, handgunId, meleeId, utilityAId, utilityBId,
            gadgetQId, gadgetEId);
        loadout.resetForRespawn();

        WeaponId after1 = loadout.primary == null ? null : loadout.primary.weaponId();
        WeaponId after2 = loadout.handgun == null ? null : loadout.handgun.weaponId();
        return before1 != after1
            || before2 != after2
            || before3 != loadout.meleeId()
            || before4 != loadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_A)
            || before5 != loadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_B)
            || beforeQ != loadout.gadgetQ.gadgetId()
            || beforeE != loadout.gadgetE.gadgetId();
    }

    public int requestedPrimary() {
        return requestedPrimary;
    }

    public int requestedHandgun() {
        return requestedHandgun;
    }

    public int requestedMelee() {
        return requestedMelee;
    }

    public int requestedUtilityA() {
        return requestedUtilityA;
    }

    public int requestedUtilityB() {
        return requestedUtilityB;
    }

    public int requestedGadgetQ() {
        return requestedGadgetQ;
    }

    public int requestedGadgetE() {
        return requestedGadgetE;
    }
}
