package io.github.skystrike.gameplay;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import io.github.skystrike.input.InputRouter;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Client-side loadout input mapping: slot keys 1–5, the wheel (and its bracket-key stand-ins),
 * the tap-swap rule, and the loadout-edit debug keys, plus the prediction bookkeeping that keeps
 * the local view honest until the server acknowledges a press.
 *
 * <p>The flow for one press is:
 * <ol>
 *   <li>apply the shared rule ({@link PlayerLoadout#tapSlot}) to the predicted player
 *       immediately, so the switch reads instantly;</li>
 *   <li>carry the press on every input packet — with the <i>birth sequence</i> of the packet it
 *       first rode on — until the server's last-processed sequence passes that birth, so UDP
 *       loss cannot eat it and retransmission cannot apply it twice;</li>
 *   <li>re-apply it after every snapshot that predates the acknowledgement, since the
 *       reconciliation otherwise stomps the predicted loadout with the older server state.</li>
 * </ol>
 *
 * <p>The same shared {@code tapSlot}/{@code cycle} code runs here and on the server, so the two
 * cannot disagree about what a press means — only about whether it has arrived yet.
 *
 * <p>F2/F3/F4 are a stopgap for loadout editing until the Phase 7 menu owns it: they cycle the
 * primary, handgun and melee choices and send a {@link PacketLoadoutUpdate}. The server applies
 * the composition at the next respawn.
 */
public final class LoadoutController {

    /** Wheel notches remembered between polls, so a fast flick cycles several slots. */
    private static final int MAX_PENDING_SCROLL_NOTCHES = 8;

    /**
     * Presses waiting for their acknowledgements, capped: beyond this the oldest are dropped and
     * the next snapshot repairs the view. Four cover any human burst inside one round trip.
     */
    private static final int MAX_OUTSTANDING_PRESSES = 4;

    /**
     * One slot press the prediction applied but the server has not yet acknowledged. The birth
     * sequence is the sequence of the first input packet that carried it; negative until then.
     * The server deduplicates on it, which is what makes retransmitting an edge safe.
     */
    private record PendingPress(int slot, long birthSequence) {
    }

    private record PendingGadgetPress(int gadget, long birthSequence) {
    }

    private final KeyBindings bindings;
    private final InputRouter router;
    private Consumer<Packet> packetSender;

    private final Deque<PendingPress> outstanding = new ArrayDeque<>();
    private final Deque<PendingGadgetPress> outstandingGadgets = new ArrayDeque<>();
    private int pendingScrollNotches;

    // Stopgap composition editing, replaced by the loadout menu in a later phase.
    private int debugPrimaryIndex = WeaponId.DEFAULT.ordinal();
    private int debugHandgunIndex = firstPistolOrdinal();
    private int debugMeleeIndex = MeleeId.DEFAULT.ordinal();

    public LoadoutController(KeyBindings bindings, InputRouter router) {
        this.bindings = bindings;
        this.router = router;
    }

    /** Where debug composition packets go. Optional; without it the F-keys do nothing. */
    public void setPacketSender(Consumer<Packet> packetSender) {
        this.packetSender = packetSender;
    }

    /** Mouse wheel event hook (libGDX delivers these on an input processor, not by polling). */
    public void scrolled(float amountY) {
        if (amountY == 0f || !router.isGameplayActive()) {
            return;
        }
        int notch = amountY > 0f ? 1 : -1;
        pendingScrollNotches = clamp(
            pendingScrollNotches + notch, -MAX_PENDING_SCROLL_NOTCHES, MAX_PENDING_SCROLL_NOTCHES);
    }

    /**
     * Polls one frame of loadout input against the predicted local player. Call once per frame,
     * before the input packet is sampled, so a press this frame rides this frame's packet.
     */
    public void update(Player localPlayer) {
        if (!router.isGameplayActive()) {
            pendingScrollNotches = 0;
            return;
        }

        for (int slot = 1; slot <= PlayerLoadout.SLOT_COUNT; slot++) {
            if (bindings.isSlotJustPressed(slot)) {
                press(slot, localPlayer);
            }
        }
        if (bindings.isWeaponNextJustPressed()) {
            cycle(localPlayer, 1);
        }
        if (bindings.isWeaponPrevJustPressed()) {
            cycle(localPlayer, -1);
        }
        while (pendingScrollNotches != 0) {
            cycle(localPlayer, pendingScrollNotches > 0 ? 1 : -1);
            pendingScrollNotches -= pendingScrollNotches > 0 ? 1 : -1;
        }

        if (bindings.isGadgetQJustPressed()) {
            pressGadget(PacketPlayerInput.GADGET_Q_PRESS, localPlayer);
        }
        if (bindings.isGadgetEJustPressed()) {
            pressGadget(PacketPlayerInput.GADGET_E_PRESS, localPlayer);
        }

        pollDebugLoadoutKeys();
    }

    /**
     * Puts the oldest unacknowledged press on the packet about to be sent — every packet, until
     * the server acknowledges it, so UDP loss cannot eat a switch. The oldest goes first: presses
     * must reach the server in the order the player made them, and a newborn press earns its
     * birth sequence here, from the first packet that carries it.
     */
    public void stampPacket(PacketPlayerInput packet) {
        packet.slotPress = PacketPlayerInput.NO_SLOT_PRESS;
        packet.slotPressSeq = -1L;
        PendingPress oldest = outstanding.peekFirst();
        if (oldest != null) {
            long birth = oldest.birthSequence();
            if (birth < 0L) {
                birth = packet.sequence;
                outstanding.pollFirst();
                outstanding.addFirst(new PendingPress(oldest.slot(), birth));
            }
            packet.slotPress = oldest.slot();
            packet.slotPressSeq = birth;
        }

        packet.gadgetPress = PacketPlayerInput.NO_GADGET_PRESS;
        packet.gadgetPressSeq = -1L;
        PendingGadgetPress oldestGadget = outstandingGadgets.peekFirst();
        if (oldestGadget != null) {
            long birth = oldestGadget.birthSequence();
            if (birth < 0L) {
                birth = packet.sequence;
                outstandingGadgets.pollFirst();
                outstandingGadgets.addFirst(new PendingGadgetPress(oldestGadget.gadget(), birth));
            }
            packet.gadgetPress = oldestGadget.gadget();
            packet.gadgetPressSeq = birth;
        }
    }

    /**
     * Reconciles the prediction against an authoritative local-player state. Presses the server
     * has processed are retired; the rest are re-applied in order on top of the snapshot, because
     * the reconciliation otherwise stomps the predicted loadout with the older server state.
     * This is the movement prediction model, exactly: replay what the server has not confirmed.
     */
    public void onAuthoritativePlayer(Player authoritative, Player predicted) {
        if (authoritative == null) {
            return;
        }
        long acked = authoritative.lastProcessedInputSequence;
        while (!outstanding.isEmpty()) {
            PendingPress oldest = outstanding.peekFirst();
            if (oldest.birthSequence() < 0L || oldest.birthSequence() > acked) {
                break;
            }
            outstanding.pollFirst();
        }
        while (!outstandingGadgets.isEmpty()) {
            PendingGadgetPress oldest = outstandingGadgets.peekFirst();
            if (oldest.birthSequence() < 0L || oldest.birthSequence() > acked) {
                break;
            }
            outstandingGadgets.pollFirst();
        }
        if (predicted == null || predicted.loadout == null) {
            return;
        }
        if (!authoritative.alive || authoritative.isSlowed()) {
            // Authority consumed and rejected these edges; never replay them across death/stun.
            outstandingGadgets.clear();
        }
        for (PendingPress press : outstanding) {
            predicted.loadout.tapSlot(press.slot());
        }
        for (PendingGadgetPress press : outstandingGadgets) {
            predicted.loadout.toggleGadget(press.gadget() == PacketPlayerInput.GADGET_Q_PRESS ? 0 : 1);
        }
        predicted.loadout.enforceShieldHandgunLock();
    }

    /** One-line readout for the debug overlay. */
    public String debugStatusLine() {
        StringBuilder line = new StringBuilder();
        if (!outstanding.isEmpty()) {
            PendingPress oldest = outstanding.peekFirst();
            line.append("press slot ").append(oldest.slot()).append(" awaiting ack  ");
        }
        if (!outstandingGadgets.isEmpty()) {
            PendingGadgetPress oldest = outstandingGadgets.peekFirst();
            line.append("gadget ").append(oldest.gadget() == PacketPlayerInput.GADGET_Q_PRESS ? "Q" : "E")
                .append(" awaiting ack  ");
        }
        return line.append("loadout edit (next respawn): F2 ")
            .append(WeaponId.fromOrdinal(debugPrimaryIndex).displayName())
            .append("  F3 ").append(MeleeId.fromOrdinal(debugMeleeIndex).displayName())
            .append("  F4 ").append(WeaponId.fromOrdinal(debugHandgunIndex).displayName())
            .toString();
    }

    private void press(int slot, Player localPlayer) {
        if (localPlayer != null && localPlayer.loadout != null) {
            localPlayer.loadout.tapSlot(slot);
            localPlayer.loadout.enforceShieldHandgunLock();
        }
        queuePress(slot);
    }

    private void cycle(Player localPlayer, int direction) {
        if (localPlayer == null || localPlayer.loadout == null) {
            return;
        }
        int before = localPlayer.loadout.activeSlot;
        int after = localPlayer.loadout.cycle(direction);
        localPlayer.loadout.enforceShieldHandgunLock();
        if (after != before) {
            queuePress(after);
        }
    }

    private void pressGadget(int gadgetPress, Player localPlayer) {
        if (localPlayer == null || localPlayer.loadout == null || !localPlayer.alive || localPlayer.isSlowed()) {
            return;
        }
        int index = gadgetPress == PacketPlayerInput.GADGET_Q_PRESS ? 0 : 1;
        if (!localPlayer.loadout.toggleGadget(index)) {
            return;
        }
        localPlayer.loadout.enforceShieldHandgunLock();
        outstandingGadgets.addLast(new PendingGadgetPress(gadgetPress, -1L));
        while (outstandingGadgets.size() > MAX_OUTSTANDING_PRESSES) {
            outstandingGadgets.pollFirst();
        }
    }

    private void queuePress(int slot) {
        outstanding.addLast(new PendingPress(slot, -1L));
        while (outstanding.size() > MAX_OUTSTANDING_PRESSES) {
            outstanding.pollFirst();
        }
    }

    // --- Stopgap composition editing (until the menu owns it) ------------------------------------

    private void pollDebugLoadoutKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            debugPrimaryIndex = (debugPrimaryIndex + 1) % WeaponId.values().length;
            sendLoadoutUpdate(new PacketLoadoutUpdate(
                debugPrimaryIndex, PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugMeleeIndex = (debugMeleeIndex + 1) % MeleeId.values().length;
            sendLoadoutUpdate(new PacketLoadoutUpdate(
                PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT, debugMeleeIndex));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F4)) {
            debugHandgunIndex = nextPistolOrdinal(debugHandgunIndex);
            sendLoadoutUpdate(new PacketLoadoutUpdate(
                PacketLoadoutUpdate.KEEP_CURRENT, debugHandgunIndex, PacketLoadoutUpdate.KEEP_CURRENT));
        }
    }

    private void sendLoadoutUpdate(Packet packet) {
        if (packetSender != null) {
            packetSender.accept(packet);
        }
    }

    private static int firstPistolOrdinal() {
        for (WeaponId id : WeaponId.values()) {
            if (WeaponRegistry.of(id).ballistics().weaponClass().isSidearm()) {
                return id.ordinal();
            }
        }
        return WeaponId.DEFAULT_SIDEARM.ordinal();
    }

    private static int nextPistolOrdinal(int current) {
        WeaponId[] ids = WeaponId.values();
        for (int i = 1; i <= ids.length; i++) {
            int ordinal = (current + i) % ids.length;
            if (WeaponRegistry.of(ids[ordinal]).ballistics().weaponClass().isSidearm()) {
                return ordinal;
            }
        }
        return current;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
