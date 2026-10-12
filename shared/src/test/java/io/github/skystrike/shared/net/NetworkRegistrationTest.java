package io.github.skystrike.shared.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.effect.EffectSpawn;
import io.github.skystrike.shared.effect.EffectType;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.map.DamageSource;
import io.github.skystrike.shared.map.DoorState;
import io.github.skystrike.shared.model.CameraEntity;
import io.github.skystrike.shared.model.DroneEntity;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.net.c2s.PacketChatRequest;
import io.github.skystrike.shared.net.c2s.PacketCommandRequest;
import io.github.skystrike.shared.net.c2s.PacketJoinRequest;
import io.github.skystrike.shared.net.c2s.PacketLeaveRequest;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.c2s.PacketPing;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.net.s2c.PacketBlockBreak;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.net.s2c.PacketCommandResponse;
import io.github.skystrike.shared.net.s2c.PacketDoorState;
import io.github.skystrike.shared.net.s2c.PacketDamageEvent;
import io.github.skystrike.shared.net.s2c.PacketEffectSpawn;
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
        assertTrue(NetworkRegistration.isSupportType(UtilityZone.class));
        assertTrue(NetworkRegistration.isSupportType(HitZone.class));
        assertTrue(NetworkRegistration.isSupportType(PlayerLoadout.class));
        assertTrue(NetworkRegistration.isSupportType(GadgetSlot.class));
        assertTrue(NetworkRegistration.isSupportType(EffectType.class));
        assertTrue(NetworkRegistration.isSupportType(EffectSpawn.class));
        assertTrue(NetworkRegistration.isSupportType(DroneEntity.class));
        assertTrue(NetworkRegistration.isSupportType(CameraEntity.class));
        assertTrue(NetworkRegistration.isSupportType(DamageSource.class));
        // DoorState deliberately stays off the registered list: the wire carries its ordinal.
        assertFalse(NetworkRegistration.registeredTypes().contains(DoorState.class));
        assertFalse(NetworkRegistration.isSupportType(PacketGameState.class));
        assertFalse(NetworkRegistration.isSupportType(PacketLoadoutUpdate.class));
        assertFalse(NetworkRegistration.isSupportType(PacketEffectSpawn.class));
        assertFalse(NetworkRegistration.isSupportType(PacketBlockBreak.class));
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
    @DisplayName("throwable and persistent-zone state are appended at the Phase 5 wire positions")
    void phaseFiveThrowableStateIsAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(ThrownUtility.class, types.get(25));
        assertEquals(UtilityZone.class, types.get(26));
    }

    @Test
    @DisplayName("the Phase 6 gadget slot sits at its pinned position")
    void phaseSixGadgetSlotIsAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(GadgetSlot.class, types.get(27));
        assertTrue(types.size() >= 28, "Phase 6 must append, never replace");
    }

    @Test
    @DisplayName("the build-plan M1 command packets sit at their pinned positions")
    void buildPlanM1CommandPacketsAreAppended() {
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(PacketCommandRequest.class, types.get(28));
        assertEquals(PacketCommandResponse.class, types.get(29));
        assertTrue(types.size() >= 30, "append only, never replace");
    }

    @Test
    @DisplayName("the M7 effect-channel types are appended, and the M10 gadget entities follow them")
    void buildPlanM7EffectTypesAreAppended() {
        // Registration order is the wire format: these indices can never move again.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(EffectType.class, types.get(30));
        assertEquals(EffectSpawn.class, types.get(31));
        assertEquals(PacketEffectSpawn.class, types.get(32));
        assertEquals(DroneEntity.class, types.get(33));
        assertEquals(CameraEntity.class, types.get(34));
        assertTrue(types.size() >= 35, "append only, never replace");
    }

    @Test
    @DisplayName("the protocol-14 block event types are appended after the M10 gadget entities")
    void protocolFourteenBlockEventsAreAppended() {
        // Registration order is the wire format: these indices can never move again.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(DamageSource.class, types.get(35));
        assertEquals(PacketBlockBreak.class, types.get(36));
        assertTrue(types.size() >= 37, "append only, never replace");
    }

    @Test
    @DisplayName("the protocol-15 door-state packet is appended after the block event types")
    void protocolFifteenDoorStateIsAppended() {
        // Registration order is the wire format: this index can never move again. The version pin
        // belongs to the newest protocol's test, so this one only asserts the protocol can never
        // come back down.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(PacketDoorState.class, types.get(37));
        assertEquals(38, types.size(), "append only; bump PROTOCOL_VERSION when this changes");
        assertTrue(NetConfig.PROTOCOL_VERSION >= 15);
    }

    @Test
    @DisplayName("the protocol-16 join map gate changed the handshake without appending a type")
    void protocolSixteenJoinMapHashIsPinned() {
        // The one bump so far that adds no registration: the join request and the join reject both
        // changed shape instead, which is why the pin asserts a size that stayed put and a version
        // that moved with it.
        List<Class<?>> types = NetworkRegistration.registeredTypes();
        assertEquals(38, types.size(), "the join gate appends nothing");
        assertEquals(PacketDoorState.class, types.get(37));
        assertEquals(16, NetConfig.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("a join request states the map it parsed, and a map refusal states the server's")
    void theJoinHandshakeCarriesBothSidesOfTheMapIdentity() {
        PacketJoinRequest stated = roundTrip(new PacketJoinRequest(
            7, "Nova", "bef76535455be93d0fd3f845a92dee9a6c68053c4e2dc8159c7210a9cca26693"));
        assertEquals(
            "bef76535455be93d0fd3f845a92dee9a6c68053c4e2dc8159c7210a9cca26693",
            stated.mapHashHex);

        PacketJoinRequest silent = roundTrip(new PacketJoinRequest(7, "Nova"));
        assertEquals("", silent.mapHashHex, "an unstated map is empty, never a wildcard");

        PacketJoinReject refused = roundTrip(new PacketJoinReject(
            PacketJoinReject.REASON_MAP_MISMATCH, stated.mapHashHex));
        assertEquals(PacketJoinReject.REASON_MAP_MISMATCH, refused.reason);
        assertEquals(stated.mapHashHex, refused.serverMapHashHex);

        PacketJoinReject full = roundTrip(
            new PacketJoinReject(PacketJoinReject.REASON_SERVER_FULL));
        assertEquals("", full.serverMapHashHex, "only a map refusal names a map");
    }

    @Test
    @DisplayName("a door-state record round-trips with its ordering tick, identity and anchor")
    void doorStatePacketsRoundTrip() {
        PacketDoorState decoded = roundTrip(
            new PacketDoorState(4242L, 7, DoorState.OPENING, 4200L));
        assertEquals(4242L, decoded.tick);
        assertEquals(7, decoded.doorId);
        assertEquals(
            DoorState.OPENING,
            decoded.state(),
            "ordinal carried plain decodes back");
        assertEquals(4200L, decoded.sinceTick);
        // The defensive decode: a garbage ordinal fails shut to CLOSED, never an exception.
        assertEquals(
            DoorState.CLOSED,
            DoorState.fromOrdinal(-1));
        assertEquals(
            DoorState.CLOSED,
            DoorState.fromOrdinal(99));

        PacketJoinAccept accept = new PacketJoinAccept(3, "Nova", 60, 900L);
        accept.doors.add(new PacketDoorState(
            900L, 1, DoorState.OPEN, 830L));
        assertEquals(1, roundTrip(accept).doors.size(), "the join baseline rides the accept");
        assertTrue(roundTrip(new PacketJoinAccept(4, "Rook", 60, 901L)).doors.isEmpty());
    }

    @Test
    @DisplayName("an effect spawn batch survives the wire with types, positions and seeds intact")
    void effectSpawnPacketsRoundTrip() {
        PacketEffectSpawn packet = new PacketEffectSpawn(4242L, List.of(
            new EffectSpawn(EffectType.FRAG_EXPLOSION, 120f, 240f, 35f, 1f),
            new EffectSpawn(EffectType.MUZZLE_FLASH, 10f, 20f, 90f, 0.5f)));
        packet.effects.get(0).seed = 7;

        PacketEffectSpawn decoded = roundTrip(packet);
        assertEquals(4242L, decoded.tick);
        assertEquals(2, decoded.effectCount());
        assertEquals(EffectType.FRAG_EXPLOSION, decoded.effects.get(0).type);
        assertEquals(120f, decoded.effects.get(0).x);
        assertEquals(240f, decoded.effects.get(0).y);
        assertEquals(35f, decoded.effects.get(0).angle);
        assertEquals(1f, decoded.effects.get(0).scale, 1e-6f);
        assertEquals(7, decoded.effects.get(0).seed);
        assertEquals(EffectType.MUZZLE_FLASH, decoded.effects.get(1).type);
        assertEquals(0.5f, decoded.effects.get(1).scale, 1e-6f);
    }

    @Test
    @DisplayName("a block break survives the wire with its identity, cause and revision intact")
    void blockBreakPacketsRoundTrip() {
        PacketBlockBreak decoded = roundTrip(
            new PacketBlockBreak(4242L, 57, DamageSource.BLAST, 1770f, 1348f, false, 2));
        assertEquals(4242L, decoded.tick);
        assertEquals(57, decoded.blockIndex);
        assertEquals(DamageSource.BLAST, decoded.cause);
        assertEquals(1770f, decoded.x);
        assertEquals(1348f, decoded.y);
        assertFalse(decoded.shattered);
        assertEquals(2, decoded.revision);

        PacketBlockBreak shattered = roundTrip(
            new PacketBlockBreak(4243L, 58, DamageSource.BULLET, 1670f, 1400f, true, 3));
        assertEquals(DamageSource.BULLET, shattered.cause);
        assertTrue(shattered.shattered);
        assertEquals(3, shattered.revision);
    }

    @Test
    @DisplayName("the join accept carries the map hash, the revision and the break history")
    void joinAcceptCarriesTheDestructionBaseline() {
        PacketJoinAccept outbound = new PacketJoinAccept(3, "Nova", 60, 900L);
        outbound.mapHashHex = "bef76535455be93d0fd3f845a92dee9a6c68053c4e2dc8159c7210a9cca26693";
        outbound.mapRevision = 2;
        outbound.destroyedBlocks.add(
            new PacketBlockBreak(800L, 57, DamageSource.BLAST, 1770f, 1348f, false, 1));
        outbound.destroyedBlocks.add(
            new PacketBlockBreak(850L, 58, DamageSource.BULLET, 1670f, 1400f, true, 2));

        PacketJoinAccept accept = roundTrip(outbound);
        assertEquals(3, accept.playerId);
        assertEquals("Nova", accept.playerName);
        assertEquals(
            "bef76535455be93d0fd3f845a92dee9a6c68053c4e2dc8159c7210a9cca26693", accept.mapHashHex);
        assertEquals(2, accept.mapRevision);
        assertEquals(2, accept.destroyedBlocks.size());
        assertEquals(57, accept.destroyedBlocks.get(0).blockIndex);
        assertEquals(DamageSource.BLAST, accept.destroyedBlocks.get(0).cause);
        assertEquals(1, accept.destroyedBlocks.get(0).revision);
        assertEquals(58, accept.destroyedBlocks.get(1).blockIndex);
        assertTrue(accept.destroyedBlocks.get(1).shattered);
        assertEquals(2, accept.destroyedBlocks.get(1).revision);

        PacketJoinAccept fresh = roundTrip(new PacketJoinAccept(4, "Rook", 60, 901L));
        assertEquals("", fresh.mapHashHex, "the field default, before the server states its map");
        assertEquals(0, fresh.mapRevision);
        assertTrue(fresh.destroyedBlocks.isEmpty());
    }

    @Test
    @DisplayName("command request and response survive the wire intact")
    void commandPacketsRoundTrip() {
        PacketCommandRequest request = roundTrip(new PacketCommandRequest("give iron_carbine"));
        assertEquals("give iron_carbine", request.line);

        PacketCommandResponse response = roundTrip(new PacketCommandResponse(
            false, CommandResult.Severity.WARNING.ordinal(),
            new java.util.ArrayList<>(java.util.List.of("first line", "second line"))));
        assertFalse(response.ok);
        assertEquals(CommandResult.Severity.WARNING, response.severity());
        assertEquals(java.util.List.of("first line", "second line"), response.lines);

        // The severity decode is defensive: an ordinal from a newer protocol never throws.
        assertEquals(CommandResult.Severity.INFO,
            new PacketCommandResponse(true, 999, new java.util.ArrayList<>()).severity());
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
        assertEquals("", join.mapHashHex, "the two-argument form states no map");

        PacketPing ping = roundTrip(new PacketPing(1234L));
        assertEquals(1234L, ping.clientTimeMillis);

        assertInstanceOf(PacketLeaveRequest.class, roundTrip(new PacketLeaveRequest()));

        PacketPlayerInput input = roundTrip(new PacketPlayerInput(10L, 1.0f, true, false, true, true, false, 45f));
        assertEquals(10L, input.sequence);
        assertEquals(PacketPlayerInput.NO_SLOT_PRESS, input.slotPress);
        assertEquals(-1L, input.slotPressSeq);
        assertEquals(PacketPlayerInput.NO_GADGET_PRESS, input.gadgetPress);
        assertEquals(-1L, input.gadgetPressSeq);
        assertEquals(PacketPlayerInput.NO_VIEW_ACTION, input.viewAction);
        assertEquals(-1L, input.viewActionSeq);
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

        PacketPlayerInput withGadget = new PacketPlayerInput(
            12L, 0f, false, false, false, false, false, 0f,
            PacketPlayerInput.NO_SLOT_PRESS, PacketPlayerInput.GADGET_Q_PRESS);
        withGadget.gadgetPressSeq = 12L;
        PacketPlayerInput decodedGadget = roundTrip(withGadget);
        assertEquals(PacketPlayerInput.GADGET_Q_PRESS, decodedGadget.gadgetPress);
        assertEquals(12L, decodedGadget.gadgetPressSeq);

        PacketPlayerInput withViewAction = new PacketPlayerInput(13L, 0f, false, false, false, false, false, 0f);
        withViewAction.viewAction = PacketPlayerInput.VIEW_CYCLE;
        withViewAction.viewActionSeq = 13L;
        PacketPlayerInput decodedViewAction = roundTrip(withViewAction);
        assertEquals(PacketPlayerInput.VIEW_CYCLE, decodedViewAction.viewAction);
        assertEquals(13L, decodedViewAction.viewActionSeq);
        assertEquals(PacketPlayerInput.NO_VIEW_ACTION, input.viewAction,
            "the plain constructor leaves no view edge riding");

        PacketPlayerInput withUse = new PacketPlayerInput(14L, 0f, false, false, false, false, false, 0f);
        withUse.usePress = PacketPlayerInput.USE_PRESS;
        withUse.usePressSeq = 14L;
        PacketPlayerInput decodedUse = roundTrip(withUse);
        assertEquals(PacketPlayerInput.USE_PRESS, decodedUse.usePress);
        assertEquals(14L, decodedUse.usePressSeq);
        assertEquals(PacketPlayerInput.NO_USE_PRESS, input.usePress,
            "the plain constructor leaves no use edge riding");

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
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, loadout.gadgetQ,
            "the five-slot constructor keeps both gadget slots");
        assertEquals(PacketLoadoutUpdate.KEEP_CURRENT, loadout.gadgetE);

        PacketLoadoutUpdate gadgets = roundTrip(new PacketLoadoutUpdate(
            PacketLoadoutUpdate.KEEP_CURRENT,
            PacketLoadoutUpdate.KEEP_CURRENT,
            PacketLoadoutUpdate.KEEP_CURRENT,
            PacketLoadoutUpdate.KEEP_CURRENT,
            PacketLoadoutUpdate.KEEP_CURRENT,
            GadgetId.SHIELD.ordinal(),
            GadgetId.NONE.ordinal()));
        assertEquals(GadgetId.SHIELD.ordinal(), gadgets.gadgetQ);
        assertEquals(GadgetId.NONE.ordinal(), gadgets.gadgetE,
            "the explicit NONE request survives the wire distinctly from KEEP_CURRENT");
        assertFalse(gadgets.isEmpty());
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
        p.blindRemaining = 1.5f;
        p.blindDuration = 3f;
        p.slowRemaining = 0.75f;
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
        p.loadout.setGadgets(GadgetId.SHIELD, GadgetId.FUEL_TANK);
        p.loadout.gadgetQ.active = true;
        p.loadout.gadgetQ.durability = 85.5f;
        p.loadout.gadgetQ.cooldownRemaining = 0.75f;
        p.loadout.gadgetE.broken = true;
        p.surveillanceView = SurveillanceView.DRONE.ordinal();

        Projectile round = new Projectile(9, 1, 0, WeaponId.CATHEDRAL.ordinal(), 300f, 400f, 1500f, 20f);
        round.age = 0.25f;
        round.distanceTravelled = 375f;

        ThrownUtility thrown = new ThrownUtility(
            12, 1, 0, UtilityId.FRAG.ordinal(), 320f, 410f, 600f, 300f, 2.5f);
        thrown.prevX = 310f;
        thrown.prevY = 405f;
        thrown.aimAngle = 35f;
        thrown.age = 0.25f;
        thrown.fuseRemaining = 2.25f;
        thrown.resting = false;
        thrown.bounces = 2;
        thrown.contactNormalX = -1f;
        thrown.contactNormalY = 0f;

        UtilityZone zone = new UtilityZone(
            3, 1, 0, UtilityId.POISON_SMOKE.ordinal(), 350f, 420f, 220f, 12f, 6.5f);

        DroneEntity drone = new DroneEntity(21, 1, 0, 500f, 950f, 30f, 22f);
        drone.prevX = 495f;
        drone.prevY = 955f;
        drone.vx = 60f;
        drone.vy = -15f;

        CameraEntity camera = new CameraEntity(22, 1, 0, 800f, 300f, 0f, 0f, 90f, 12f);
        camera.stuck = true;
        camera.contactNormalX = 0f;
        camera.contactNormalY = 1f;

        PacketGameState outbound = new PacketGameState(
            4242L, 99L, 1, List.of(p), List.of(round), List.of(thrown),
            List.of(zone), List.of(drone), List.of(camera));
        outbound.mapRevision = 3;
        EffectSpawn gunfire = new EffectSpawn(EffectType.MUZZLE_FLASH, 125f, 225f, 30f, 1f);
        gunfire.seed = 321;
        gunfire.sourcePlayerId = 1;
        gunfire.weaponId = WeaponId.IRON_CARBINE.ordinal();
        outbound.gunfireEvents.add(gunfire);
        PacketGameState state = roundTrip(outbound);
        assertEquals(4242L, state.tick);
        assertEquals(99L, state.serverTimeMillis);
        assertEquals(3, state.mapRevision);
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
        assertTrue(decodedLoadout.gadgetQ.holds(GadgetId.SHIELD));
        assertTrue(decodedLoadout.gadgetQ.active);
        assertEquals(85.5f, decodedLoadout.gadgetQ.durability, 1e-4f);
        assertEquals(0.75f, decodedLoadout.gadgetQ.cooldownRemaining, 1e-4f);
        assertTrue(decodedLoadout.gadgetE.holds(GadgetId.FUEL_TANK));
        assertTrue(decodedLoadout.gadgetE.broken);
        assertFalse(decodedLoadout.hasFuelTank(), "a detonated tank stays detonated on the wire");
        assertEquals(1.25f, state.players.get(0).spread);
        assertEquals(4.5f, state.players.get(0).gunKick);
        assertEquals(3, state.players.get(0).kills);
        assertFalse(state.players.get(0).alive);
        assertEquals(2.5f, state.players.get(0).respawnTimer);
        assertEquals(1.5f, state.players.get(0).blindRemaining);
        assertEquals(3f, state.players.get(0).blindDuration);
        assertEquals(0.75f, state.players.get(0).slowRemaining);
        assertEquals(SurveillanceView.DRONE, state.players.get(0).surveillance(),
            "the surveillance view rides the player record");
        assertTrue(state.players.get(0).isSurveillanceLocked());

        assertEquals(1, state.gunfireEvents.size());
        EffectSpawn decodedGunfire = state.gunfireEvents.get(0);
        assertEquals(EffectType.MUZZLE_FLASH, decodedGunfire.type);
        assertEquals(321, decodedGunfire.seed);
        assertEquals(1, decodedGunfire.sourcePlayerId);
        assertEquals(WeaponId.IRON_CARBINE.ordinal(), decodedGunfire.weaponId);

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
        assertEquals(35f, decodedThrown.aimAngle);
        assertEquals(0.25f, decodedThrown.age);
        assertEquals(2.25f, decodedThrown.fuseRemaining);
        assertFalse(decodedThrown.resting);
        assertEquals(2, decodedThrown.bounces);
        assertEquals(-1f, decodedThrown.contactNormalX);
        assertEquals(0f, decodedThrown.contactNormalY);
        assertEquals(UtilityId.FRAG.wireId(), decodedThrown.weaponWireId());

        assertEquals(1, state.utilityZones.size());
        UtilityZone decodedZone = state.utilityZones.get(0);
        assertEquals(3, decodedZone.id);
        assertEquals(UtilityId.POISON_SMOKE, decodedZone.utility());
        assertEquals(350f, decodedZone.x);
        assertEquals(420f, decodedZone.y);
        assertEquals(220f, decodedZone.radius);
        assertEquals(12f, decodedZone.damage);
        assertEquals(6.5f, decodedZone.remainingSeconds);
        assertTrue(decodedZone.blocksVision());

        assertEquals(1, state.drones.size());
        DroneEntity decodedDrone = state.drones.get(0);
        assertEquals(21, decodedDrone.id);
        assertEquals(1, decodedDrone.ownerId);
        assertEquals(0, decodedDrone.teamIndex);
        assertEquals(500f, decodedDrone.x);
        assertEquals(950f, decodedDrone.y);
        assertEquals(495f, decodedDrone.prevX);
        assertEquals(955f, decodedDrone.prevY);
        assertEquals(60f, decodedDrone.vx);
        assertEquals(-15f, decodedDrone.vy);
        assertEquals(30f, decodedDrone.aimAngle);
        assertEquals(22f, decodedDrone.health);

        assertEquals(1, state.cameras.size());
        CameraEntity decodedCamera = state.cameras.get(0);
        assertEquals(22, decodedCamera.id);
        assertEquals(1, decodedCamera.ownerId);
        assertEquals(800f, decodedCamera.x);
        assertEquals(300f, decodedCamera.y);
        assertTrue(decodedCamera.stuck);
        assertEquals(0f, decodedCamera.contactNormalX);
        assertEquals(1f, decodedCamera.contactNormalY);
        assertEquals(90f, decodedCamera.aimAngle);
        assertEquals(12f, decodedCamera.health);
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
    @DisplayName("the one-way drop-through window is motion state and never enters the snapshot layout")
    void dropThroughWindowIsNotSerialized() {
        // The window is re-derived by the shared step on both sides, so it must stay off the wire:
        // a serialized field on Player would silently change the snapshot layout and need a
        // protocol bump with it. The Kryo configuration here is the one both endpoints build.
        Player player = new Player(5, "Dropper", 0, 500f, 500f);
        player.grounded = false;
        player.coyoteTimer = 0.05f;
        player.dropThroughTimer = 0.25f;

        PacketGameState state = roundTrip(new PacketGameState(7L, 1_000L, 1, List.of(player)));

        Player decoded = state.players.get(0);
        assertEquals(0.05f, decoded.coyoteTimer, 1e-6f,
            "the rest of the per-step motion state still travels with the snapshot");
        assertFalse(decoded.grounded);
        assertEquals(0f, decoded.dropThroughTimer, 0f,
            "a transient field must not reach the wire");
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
