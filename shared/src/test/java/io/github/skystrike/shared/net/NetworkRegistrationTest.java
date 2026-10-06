package io.github.skystrike.shared.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketGameState;
import io.github.skystrike.shared.net.s2c.PacketJoinAccept;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.net.s2c.PacketPong;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.text.ChatTarget;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
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
        // State records and ArrayList travel as fields of a packet. Kryo still has to know them,
        // which is why they are registered, but nothing addresses them directly.
        assertTrue(NetworkRegistration.isSupportType(Player.class));
        assertTrue(NetworkRegistration.isSupportType(Projectile.class));
        assertTrue(NetworkRegistration.isSupportType(ThrownUtility.class));
        assertTrue(NetworkRegistration.isSupportType(HitZone.class));
        assertTrue(NetworkRegistration.isSupportType(PlayerLoadout.class));
        assertFalse(NetworkRegistration.isSupportType(PacketGameState.class));
        assertFalse(NetworkRegistration.isSupportType(PacketLoadoutUpdate.class));
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
    @DisplayName("the combat types added in Phase 3 sit at their pinned positions")
    void phaseThreeTypesArePinned() {
        // Registration order is the wire format: these indices can never move again.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(Projectile.class, types.get(11));
        assertEquals(HitZone.class, types.get(12));
        assertEquals(PacketDamageEvent.class, types.get(13));
        assertEquals(PacketKillEvent.class, types.get(14));
    }

    @Test
    @DisplayName("the loadout types added in Phase 4 sit at their pinned positions")
    void phaseFourTypesArePinned() {
        // Pinned by index, not by distance from the end: Phase 7 appended after these, and an
        // end-relative assertion would have quietly followed them and stopped guarding anything.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(PacketLoadoutUpdate.class, types.get(15));
        assertEquals(io.github.skystrike.shared.model.WeaponItem.class, types.get(16));
        assertEquals(PlayerLoadout.class, types.get(17));
        assertTrue(types.size() >= 18, "Phase 4 must append, never replace");
    }

    @Test
    @DisplayName("the chat and capability types added in Phase 7 are appended, never inserted")
    void phaseSevenTypesAreAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(ChatChannel.class, types.get(18));
        assertEquals(ChatTarget.class, types.get(19));
        assertEquals(ChatMessage.class, types.get(20));
        assertEquals(Permission.class, types.get(21));
        assertEquals(PacketChatRequest.class, types.get(22));
        assertEquals(PacketChatMessage.class, types.get(23));
        assertEquals(PacketCapabilities.class, types.get(24));
        assertTrue(types.size() >= 25, "Phase 7 must append, never replace");
    }

    @Test
    @DisplayName("throwable state is appended at the Phase 5 wire position")
    void phaseFiveThrowableStateIsAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(ThrownUtility.class, types.get(25));
        assertEquals(26, types.size(), "append only; bump PROTOCOL_VERSION when this changes");
        assertEquals(6, NetConfig.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("chat and capability packets survive the wire with their enums intact")
    void phaseSevenPacketsRoundTrip() {
        PacketChatRequest request = roundTrip(new PacketChatRequest(ChatTarget.TEAM, "  rotating B  "));
        assertEquals(ChatTarget.TEAM, request.target);
        assertEquals("  rotating B  ", request.body, "the server sanitises, the wire does not");

        ChatMessage line = ChatMessage.fromPlayer(
            1234L, ChatTarget.TEAM, 7, "Nova", Team.TEAM_B.index(), "rotating B");
        PacketChatMessage delivered = roundTrip(new PacketChatMessage(line));
        assertEquals(ChatChannel.TEAM, delivered.message.channel);
        assertEquals(7, delivered.message.authorId);
        assertEquals("Nova", delivered.message.authorName);
        assertEquals(Team.TEAM_B, delivered.message.authorTeam());
        assertEquals("rotating B", delivered.message.body);
        assertEquals(1234L, delivered.message.timestampMillis);

        PacketCapabilities caps = roundTrip(PacketCapabilities.forLevel(Permission.MODERATOR));
        assertEquals(Permission.MODERATOR, caps.level);
        assertTrue(caps.consoleAccess);

        PacketCapabilities none = roundTrip(PacketCapabilities.forLevel(Permission.PLAYER));
        assertEquals(Permission.PLAYER, none.level);
        assertFalse(none.consoleAccess);
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
        assertEquals(PacketPlayerInput.NO_SLOT_PRESS, input.slotPress);
        assertEquals(-1L, input.slotPressSeq);
        assertEquals(1.0f, input.moveX);
        assertTrue(input.jump);
        assertFalse(input.crouch);
        assertTrue(input.jetpack);
        assertTrue(input.ads);
        assertFalse(input.fire);
        assertEquals(45f, input.aimAngle);

        PacketPlayerInput withPress =
            new PacketPlayerInput(11L, 0f, false, false, false, false, true, 0f, PlayerLoadout.SLOT_MELEE);
        withPress.slotPressSeq = 11L;
        PacketPlayerInput decodedPress = roundTrip(withPress);
        assertTrue(decodedPress.fire);
        assertEquals(PlayerLoadout.SLOT_MELEE, decodedPress.slotPress);
        assertEquals(11L, decodedPress.slotPressSeq);

        PacketLoadoutUpdate loadout = roundTrip(new PacketLoadoutUpdate(
            WeaponId.CATHEDRAL.ordinal(),
            PacketLoadoutUpdate.KEEP_CURRENT,
            MeleeId.WINTER_KATANA.ordinal(),
            UtilityId.CLAYMORE.ordinal(),
            UtilityId.POISON_SMOKE.ordinal()));
        assertEquals(WeaponId.CATHEDRAL.ordinal(), loadout.primary);
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, loadout.handgun);
        assertEquals(MeleeId.WINTER_KATANA.ordinal(), loadout.melee);
        assertEquals(UtilityId.CLAYMORE.ordinal(), loadout.utilityA);
        assertEquals(UtilityId.POISON_SMOKE.ordinal(), loadout.utilityB);
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
        p.weaponId = MeleeId.YARD_WRENCH.wireId();
        p.spread = 1.25f;
        p.gunKick = 4.5f;
        p.kills = 3;
        p.deaths = 1;
        p.alive = false;
        p.respawnTimer = 2.5f;
        p.loadout.primary.magazine = 3;
        p.loadout.primary.reserve = 17;
        p.loadout.handgun.magazine = 4;
        p.loadout.melee = MeleeId.YARD_WRENCH.ordinal();
        p.loadout.activeSlot = PlayerLoadout.SLOT_MELEE;
        p.loadout.quickSwapOrigin = PlayerLoadout.SLOT_PRIMARY;
        p.loadout.utilityA = UtilityId.CLAYMORE.ordinal();
        p.loadout.utilityACount = 1;
        p.loadout.utilityB = UtilityId.POISON_SMOKE.ordinal();
        p.loadout.utilityBCount = 2;
        p.loadout.reloading = true;
        p.loadout.reloadTimer = 1.25f;

        Projectile round = new Projectile(9, 1, 0, WeaponId.CATHEDRAL.ordinal(), 300f, 400f, 1500f, 20f);
        round.age = 0.25f;
        round.distanceTravelled = 375f;

        ThrownUtility thrown = new ThrownUtility(
            12, 1, 0, UtilityId.FRAG.ordinal(), 320f, 410f, 600f, 300f, 2.5f);
        thrown.prevX = 310f;
        thrown.prevY = 405f;
        thrown.age = 0.25f;
        thrown.fuseRemaining = 2.25f;
        thrown.resting = false;
        thrown.bounces = 2;
        thrown.contactNormalX = -1f;
        thrown.contactNormalY = 0f;

        PacketGameState state = roundTrip(
            new PacketGameState(4242L, 99L, 1, List.of(p), List.of(round), List.of(thrown)));
        assertEquals(4242L, state.tick);
        assertEquals(99L, state.serverTimeMillis);
        assertEquals(1, state.playerCount);
        assertEquals(1, state.players.size());
        assertEquals("Nova", state.players.get(0).name);
        assertEquals(100f, state.players.get(0).x);
        assertEquals(200f, state.players.get(0).y);
        assertEquals(15f, state.players.get(0).rotation);
        assertEquals(MeleeId.YARD_WRENCH.wireId(), state.players.get(0).weaponId);
        PlayerLoadout decodedLoadout = state.players.get(0).loadout;
        assertEquals(3, decodedLoadout.primary.magazine);
        assertEquals(17, decodedLoadout.primary.reserve);
        assertEquals(4, decodedLoadout.handgun.magazine);
        assertEquals(MeleeId.YARD_WRENCH, decodedLoadout.meleeId());
        assertEquals(PlayerLoadout.SLOT_MELEE, decodedLoadout.activeSlot);
        assertEquals(PlayerLoadout.SLOT_PRIMARY, decodedLoadout.quickSwapOrigin);
        assertEquals(UtilityId.CLAYMORE,
            decodedLoadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_A));
        assertEquals(1, decodedLoadout.utilityACount);
        assertEquals(UtilityId.POISON_SMOKE,
            decodedLoadout.utilityIdForSlot(PlayerLoadout.SLOT_UTILITY_B));
        assertEquals(2, decodedLoadout.utilityBCount);
        assertTrue(decodedLoadout.reloading);
        assertEquals(1.25f, decodedLoadout.reloadTimer, 1e-4f);
        assertEquals(1.25f, state.players.get(0).spread);
        assertEquals(4.5f, state.players.get(0).gunKick);
        assertEquals(3, state.players.get(0).kills);
        assertFalse(state.players.get(0).alive);
        assertEquals(2.5f, state.players.get(0).respawnTimer);

        assertEquals(1, state.projectiles.size());
        Projectile decoded = state.projectiles.get(0);
        assertEquals(9, decoded.id);
        assertEquals(1, decoded.ownerId);
        assertEquals(WeaponId.CATHEDRAL, decoded.weapon());
        assertEquals(300f, decoded.x);
        assertEquals(1500f, decoded.vx);
        assertEquals(375f, decoded.distanceTravelled);

        assertEquals(1, state.thrownUtilities.size());
        ThrownUtility decodedThrown = state.thrownUtilities.get(0);
        assertEquals(12, decodedThrown.id);
        assertEquals(1, decodedThrown.ownerId);
        assertEquals(0, decodedThrown.teamIndex);
        assertEquals(UtilityId.FRAG, decodedThrown.utility());
        assertEquals(320f, decodedThrown.x);
        assertEquals(410f, decodedThrown.y);
        assertEquals(310f, decodedThrown.prevX);
        assertEquals(405f, decodedThrown.prevY);
        assertEquals(600f, decodedThrown.vx);
        assertEquals(300f, decodedThrown.vy);
        assertEquals(0.25f, decodedThrown.age);
        assertEquals(2.25f, decodedThrown.fuseRemaining);
        assertFalse(decodedThrown.resting);
        assertEquals(2, decodedThrown.bounces);
        assertEquals(-1f, decodedThrown.contactNormalX);
        assertEquals(0f, decodedThrown.contactNormalY);
        assertEquals(UtilityId.FRAG.wireId(), decodedThrown.weaponWireId());
    }

    @Test
    @DisplayName("damage and kill events survive the wire with their hit zone intact")
    void combatEventsRoundTrip() {
        PacketDamageEvent damage = roundTrip(new PacketDamageEvent(
            1, 2, 96.4f, 53.6f, HitZone.HEAD, WeaponId.IRON_CARBINE.ordinal(), 120f, 240f, 310f, false));
        assertEquals(1, damage.attackerId);
        assertEquals(2, damage.targetId);
        assertEquals(96.4f, damage.amount);
        assertEquals(53.6f, damage.remainingHealth);
        assertEquals(HitZone.HEAD, damage.zone);
        assertTrue(damage.isHeadshot());
        assertFalse(damage.killed);
        assertEquals(WeaponId.IRON_CARBINE, damage.weapon());

        PacketKillEvent kill = roundTrip(new PacketKillEvent(
            1, "Nova", 2, "Rook", WeaponId.CATHEDRAL.ordinal(), true, false, false));
        assertEquals("Nova", kill.killerName);
        assertEquals("Rook", kill.victimName);
        assertTrue(kill.headshot);
        assertEquals(WeaponId.CATHEDRAL, kill.weapon());

        PacketDamageEvent utilityDamage = roundTrip(new PacketDamageEvent(
            1, 1, 21f, 129f, HitZone.BODY, UtilityId.MOLOTOV.wireId(), 10f, 20f, 0f, false));
        assertEquals("Molotov", utilityDamage.weaponDisplayName());

        PacketKillEvent utilityKill = roundTrip(new PacketKillEvent(
            1, "Nova", 1, "Nova", UtilityId.FRAG.wireId(), false, true, false));
        assertEquals("Frag Grenade", utilityKill.weaponDisplayName());
        assertTrue(utilityKill.feedLine().contains("Frag Grenade"));
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
