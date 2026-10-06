package io.github.skystrike.server.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The trigger edge.
 *
 * <p>Input arrives at the client's frame rate over an unreliable channel while the simulation
 * consumes one input per tick. Without a latch, a semi-automatic tap that begins and ends
 * between two ticks is simply lost — the player presses the button and nothing happens.
 */
class PlayerSessionTest {

    private PlayerSession session;

    @BeforeEach
    void setUp() {
        // The connection is only ever used to address packets; the session logic never touches it.
        session = new PlayerSession(null, 7, "Nova", 0, 500f, 100f);
    }

    private static PacketPlayerInput input(long sequence, boolean fire) {
        return new PacketPlayerInput(sequence, 0f, false, false, false, false, fire, 0f);
    }

    @Test
    @DisplayName("a session starts with the default weapon and no pending shot")
    void initialState() {
        assertEquals(7, session.playerId());
        assertEquals("Nova", session.name());
        assertEquals(WeaponId.DEFAULT, session.gun().weaponId());
        assertEquals(WeaponId.DEFAULT.ordinal(), session.player().weaponId);
        assertFalse(session.triggerHeld());
        assertFalse(session.consumeFirePressed());
    }

    @Test
    @DisplayName("a press is latched and consumed exactly once")
    void pressIsLatchedOnce() {
        session.setInput(input(1, true));

        assertTrue(session.triggerHeld());
        assertTrue(session.consumeFirePressed(), "the tick must see the press");
        assertFalse(session.consumeFirePressed(), "and must not see it twice");
    }

    @Test
    @DisplayName("holding the trigger does not re-latch a press")
    void holdingDoesNotRepeatThePress() {
        session.setInput(input(1, true));
        assertTrue(session.consumeFirePressed());

        session.setInput(input(2, true));
        session.setInput(input(3, true));
        assertFalse(session.consumeFirePressed(), "a held trigger is not a new press");
        assertTrue(session.triggerHeld(), "but it is still held, which is what an automatic needs");

        session.setInput(input(4, false));
        session.setInput(input(5, true));
        assertTrue(session.consumeFirePressed(), "release and press is a new shot");
    }

    @Test
    @DisplayName("a tap that begins and ends between two ticks still fires")
    void tapBetweenTicksSurvives() {
        // Three packets arrive inside one tick: press, release, and an idle frame.
        session.setInput(input(1, true));
        session.setInput(input(2, false));
        session.setInput(input(3, false));

        assertFalse(session.triggerHeld(), "the button is up again by the time the tick runs");
        assertTrue(session.consumeFirePressed(), "but the shot must not be lost");
    }

    @Test
    @DisplayName("stale and out-of-order packets are discarded")
    void staleInputIsIgnored() {
        session.setInput(input(10, false));
        session.setInput(input(4, true));

        assertEquals(10L, session.latestInput().sequence);
        assertFalse(session.triggerHeld(), "an old packet must not press the trigger");
        assertFalse(session.consumeFirePressed());

        session.setInput(null);
        assertEquals(10L, session.latestInput().sequence);
    }

    @Test
    @DisplayName("dying clears a held trigger so no shot is banked for the respawn")
    void clearTriggerDropsThePendingPress() {
        session.setInput(input(1, true));
        session.clearTrigger();

        assertFalse(session.triggerHeld());
        assertFalse(session.consumeFirePressed());
    }

    @Test
    @DisplayName("the requested slot press rides along with the input")
    void slotPressTravelsWithInput() {
        PacketPlayerInput packet = new PacketPlayerInput(
            1L, 0f, false, false, false, false, false, 0f, PlayerLoadout.SLOT_MELEE);
        packet.slotPressSeq = 1L; // the client stamps every press with its birth sequence
        session.setInput(packet);

        assertEquals(PlayerLoadout.SLOT_MELEE, session.latestInput().slotPress);
        assertEquals(
            PacketPlayerInput.NO_SLOT_PRESS,
            new PacketPlayerInput().slotPress,
            "an input with no press must not silently trigger slot 3 (or any slot)");
        assertEquals(PlayerLoadout.SLOT_MELEE, session.consumeSlotPress());
        assertEquals(PacketPlayerInput.NO_SLOT_PRESS, session.consumeSlotPress(),
            "a press is consumed exactly once");
    }

    @Test
    @DisplayName("a retransmitted slot press is applied exactly once, by birth sequence")
    void retransmittedSlotPressIsDeduplicated() {
        // The client repeats a press (same birth sequence) until it is acknowledged.
        for (int i = 0; i < 3; i++) {
            PacketPlayerInput packet = new PacketPlayerInput(
                10L + i, 0f, false, false, false, false, false, 0f, PlayerLoadout.SLOT_HANDGUN);
            packet.slotPressSeq = 10L;
            session.setInput(packet);
        }

        assertEquals(PlayerLoadout.SLOT_HANDGUN, session.consumeSlotPress(),
            "the press reaches the tick once");
        assertEquals(PacketPlayerInput.NO_SLOT_PRESS, session.consumeSlotPress(),
            "and retransmissions of the same birth never re-fire it");
        assertEquals(PacketPlayerInput.NO_SLOT_PRESS, session.consumeSlotPress());

        // A genuinely new press (new birth sequence) must latch again.
        PacketPlayerInput next = new PacketPlayerInput(
            20L, 0f, false, false, false, false, false, 0f, PlayerLoadout.SLOT_PRIMARY);
        next.slotPressSeq = 20L;
        session.setInput(next);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, session.consumeSlotPress());
    }

    @Test
    @DisplayName("a Q/E gadget press is deduplicated by its birth sequence")
    void retransmittedGadgetPressIsDeduplicated() {
        for (int i = 0; i < 3; i++) {
            PacketPlayerInput packet = new PacketPlayerInput(
                30L + i, 0f, false, false, false, false, false, 0f,
                PacketPlayerInput.NO_SLOT_PRESS,
                PacketPlayerInput.GADGET_Q_PRESS);
            packet.gadgetPressSeq = 30L;
            session.setInput(packet);
        }

        assertEquals(PacketPlayerInput.GADGET_Q_PRESS, session.consumeGadgetPress());
        assertEquals(PacketPlayerInput.NO_GADGET_PRESS, session.consumeGadgetPress());

        PacketPlayerInput next = new PacketPlayerInput(
            40L, 0f, false, false, false, false, false, 0f,
            PacketPlayerInput.NO_SLOT_PRESS,
            PacketPlayerInput.GADGET_E_PRESS);
        next.gadgetPressSeq = 40L;
        session.setInput(next);
        assertEquals(PacketPlayerInput.GADGET_E_PRESS, session.consumeGadgetPress());
    }

    @Test
    @DisplayName("clearing input edges drops a pending gadget toggle")
    void clearTriggerDropsGadgetPress() {
        PacketPlayerInput packet = new PacketPlayerInput(
            50L, 0f, false, false, false, false, false, 0f,
            PacketPlayerInput.NO_SLOT_PRESS,
            PacketPlayerInput.GADGET_Q_PRESS);
        packet.gadgetPressSeq = 50L;
        session.setInput(packet);
        session.clearTrigger();
        assertEquals(PacketPlayerInput.NO_GADGET_PRESS, session.consumeGadgetPress());
    }
}
