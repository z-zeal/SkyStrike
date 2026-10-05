package io.github.skystrike.shared.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketPong;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the one thing about the transport that fails silently: a registration list that differs
 * between the two sides, or a packet that cannot actually be serialised.
 */
class NetworkRegistrationTest {

    @Test
    void theRegistrationListHasNoDuplicates() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        Set<Class<?>> unique = new HashSet<>(types);
        assertEquals(types.size(), unique.size(), "a type is registered twice");
    }

    @Test
    @DisplayName("every registered concrete type implements Packet")
    void everyRegisteredTypeIsAPacket() {
        for (Class<?> type : NetworkRegistration.registeredTypes()) {
            if (type == Packet.class) {
                continue;
            }
            assertTrue(Packet.class.isAssignableFrom(type), type + " is not a Packet");
        }
    }

    @Test
    @DisplayName("every packet has the public no-arg constructor the serialiser needs")
    void everyPacketIsInstantiable() throws ReflectiveOperationException {
        for (Class<?> type : NetworkRegistration.registeredTypes()) {
            if (type.isInterface()) {
                continue;
            }
            assertNotNull(type.getConstructor().newInstance(), type + " has no usable constructor");
        }
    }

    @Test
    @DisplayName("two independently registered endpoints agree on every class id")
    void registrationIsDeterministicAcrossEndpoints() {
        Kryo first = newRegisteredKryo();
        Kryo second = newRegisteredKryo();

        for (Class<?> type : NetworkRegistration.registeredTypes()) {
            assertEquals(
                first.getRegistration(type).getId(),
                second.getRegistration(type).getId(),
                "class id differs for " + type);
        }
    }

    @Test
    void clientToServerPacketsRoundTrip() {
        PacketJoinRequest join = roundTrip(new PacketJoinRequest(7, "Nova"));
        assertEquals(7, join.protocolVersion);
        assertEquals("Nova", join.playerName);

        PacketPing ping = roundTrip(new PacketPing(1234L));
        assertEquals(1234L, ping.clientTimeMillis);

        assertInstanceOf(PacketLeaveRequest.class, roundTrip(new PacketLeaveRequest()));
    }

    @Test
    void serverToClientPacketsRoundTrip() {
        PacketJoinAccept accept = roundTrip(new PacketJoinAccept(3, "Nova", 60, 900L));
        assertEquals(3, accept.playerId);
        assertEquals("Nova", accept.playerName);
        assertEquals(60, accept.tickRateHz);
        assertEquals(900L, accept.serverTick);

        PacketJoinReject reject = roundTrip(new PacketJoinReject(PacketJoinReject.REASON_SERVER_FULL));
        assertEquals(PacketJoinReject.REASON_SERVER_FULL, reject.reason);

        PacketPong pong = roundTrip(new PacketPong(11L, 22L));
        assertEquals(11L, pong.clientTimeMillis);
        assertEquals(22L, pong.serverTick);

        PacketGameState state = roundTrip(new PacketGameState(4242L, 99L, 2));
        assertEquals(4242L, state.tick);
        assertEquals(99L, state.serverTimeMillis);
        assertEquals(2, state.playerCount);
    }

    @Test
    @DisplayName("a packet written by one endpoint reads back as the same type on the other")
    void typesSurviveTheWireBetweenTwoEndpoints() {
        Kryo sender = newRegisteredKryo();
        Kryo receiver = newRegisteredKryo();

        Output output = new Output(1024);
        sender.writeClassAndObject(output, new PacketJoinAccept(1, "a", 60, 0L));
        output.flush();

        Object decoded = receiver.readClassAndObject(new Input(output.getBuffer(), 0, output.position()));
        assertInstanceOf(PacketJoinAccept.class, decoded);
    }

    private static Kryo newRegisteredKryo() {
        Kryo kryo = new Kryo();
        NetworkRegistration.register(kryo);
        return kryo;
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T packet) {
        Kryo kryo = newRegisteredKryo();
        Output output = new Output(1024);
        kryo.writeClassAndObject(output, packet);
        output.flush();
        Input input = new Input(output.getBuffer(), 0, output.position());
        return (T) kryo.readClassAndObject(input);
    }
}
