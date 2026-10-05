package io.github.skystrike.shared.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.net.s2c.PacketPong;
import io.github.skystrike.shared.weapons.WeaponId;
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
    @DisplayName("every registered addressable type implements Packet")
    void everyRegisteredTypeIsAPacket() {
        for (Class<?> type : NetworkRegistration.registeredTypes()) {
            if (type == Packet.class || NetworkRegistration.isSupportType(type)) {
                continue;
            }
            assertTrue(Packet.class.isAssignableFrom(type), type + " is not a Packet");
        }
    }

    @Test
    @DisplayName("support types are payloads carried inside packets, not packets themselves")
    void supportTypesAreNotPackets() {
        // Player, Projectile, HitZone and ArrayList travel as fields of a packet. Kryo still has
        // to know them, which is why they are registered, but nothing addresses them directly.
        assertTrue(NetworkRegistration.isSupportType(Player.class));
        assertTrue(NetworkRegistration.isSupportType(Projectile.class));
        assertTrue(NetworkRegistration.isSupportType(HitZone.class));
        assertFalse(NetworkRegistration.isSupportType(PacketGameState.class));
    }

    @Test
    @DisplayName("every packet has the public no-arg constructor the serialiser needs")
    void everyPacketIsInstantiable() throws ReflectiveOperationException {
        for (Class<?> type : NetworkRegistration.registeredTypes()) {
            if (type.isInterface() || type.isEnum()) {
                continue;
            }
            assertNotNull(type.getConstructor().newInstance(), type + " has no usable constructor");
        }
    }

    @Test
    @DisplayName("the combat types added in Phase 3 are registered, and appended at the end")
    void phaseThreeTypesAreAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        int size = types.size();
        assertEquals(Projectile.class, types.get(size - 4));
        assertEquals(HitZone.class, types.get(size - 3));
        assertEquals(PacketDamageEvent.class, types.get(size - 2));
        assertEquals(PacketKillEvent.class, types.get(size - 1));
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

        PacketPlayerInput input = roundTrip(new PacketPlayerInput(10L, 1.0f, true, false, true, true, false, 45f));
        assertEquals(10L, input.sequence);
        assertEquals(PacketPlayerInput.NO_WEAPON_CHANGE, input.weaponSelect);
        assertEquals(1.0f, input.moveX);
        assertTrue(input.jump);
        assertFalse(input.crouch);
        assertTrue(input.jetpack);
        assertTrue(input.ads);
        assertFalse(input.fire);
        assertEquals(45f, input.aimAngle);

        PacketPlayerInput withWeapon = roundTrip(
            new PacketPlayerInput(11L, 0f, false, false, false, false, true, 0f, WeaponId.AWP.ordinal()));
        assertTrue(withWeapon.fire);
        assertEquals(WeaponId.AWP.ordinal(), withWeapon.weaponSelect);
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

        Player p = new Player(1, "Nova", 0, 100f, 200f);
        p.vx = 50f;
        p.vy = -100f;
        p.rotation = 15f;
        p.weaponId = WeaponId.AWP.ordinal();
        p.spread = 1.25f;
        p.gunKick = 4.5f;
        p.kills = 3;
        p.deaths = 1;
        p.alive = false;
        p.respawnTimer = 2.5f;

        Projectile round = new Projectile(9, 1, 0, WeaponId.AWP.ordinal(), 300f, 400f, 1500f, 20f);
        round.age = 0.25f;
        round.distanceTravelled = 375f;

        PacketGameState state = roundTrip(new PacketGameState(4242L, 99L, 1, List.of(p), List.of(round)));
        assertEquals(4242L, state.tick);
        assertEquals(99L, state.serverTimeMillis);
        assertEquals(1, state.playerCount);
        assertEquals(1, state.players.size());
        assertEquals("Nova", state.players.get(0).name);
        assertEquals(100f, state.players.get(0).x);
        assertEquals(200f, state.players.get(0).y);
        assertEquals(15f, state.players.get(0).rotation);
        assertEquals(WeaponId.AWP.ordinal(), state.players.get(0).weaponId);
        assertEquals(1.25f, state.players.get(0).spread);
        assertEquals(4.5f, state.players.get(0).gunKick);
        assertEquals(3, state.players.get(0).kills);
        assertFalse(state.players.get(0).alive);
        assertEquals(2.5f, state.players.get(0).respawnTimer);

        assertEquals(1, state.projectiles.size());
        Projectile decoded = state.projectiles.get(0);
        assertEquals(9, decoded.id);
        assertEquals(1, decoded.ownerId);
        assertEquals(WeaponId.AWP, decoded.weapon());
        assertEquals(300f, decoded.x);
        assertEquals(1500f, decoded.vx);
        assertEquals(375f, decoded.distanceTravelled);
    }

    @Test
    @DisplayName("damage and kill events survive the wire with their hit zone intact")
    void combatEventsRoundTrip() {
        PacketDamageEvent damage = roundTrip(new PacketDamageEvent(
            1, 2, 96.4f, 53.6f, HitZone.HEAD, WeaponId.SCAR_L.ordinal(), 120f, 240f, 310f, false));
        assertEquals(1, damage.attackerId);
        assertEquals(2, damage.targetId);
        assertEquals(96.4f, damage.amount);
        assertEquals(53.6f, damage.remainingHealth);
        assertEquals(HitZone.HEAD, damage.zone);
        assertTrue(damage.isHeadshot());
        assertFalse(damage.killed);
        assertEquals(WeaponId.SCAR_L, damage.weapon());

        PacketKillEvent kill = roundTrip(new PacketKillEvent(
            1, "Nova", 2, "Rook", WeaponId.AWP.ordinal(), true, false, false));
        assertEquals("Nova", kill.killerName);
        assertEquals("Rook", kill.victimName);
        assertTrue(kill.headshot);
        assertEquals(WeaponId.AWP, kill.weapon());
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
