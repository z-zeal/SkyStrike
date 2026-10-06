package io.github.skystrike.shared.net;

import com.esotericsoftware.kryo.Kryo;
import io.github.skystrike.shared.model.GadgetSlot;
import io.github.skystrike.shared.model.HitZone;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.PlayerLoadout;
import io.github.skystrike.shared.model.Projectile;
import io.github.skystrike.shared.model.ThrownUtility;
import io.github.skystrike.shared.model.UtilityZone;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.command.Permission;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The single registration both sides call.
 *
 * <p>Kryo identifies classes on the wire by the order they were registered. If the client and the
 * server ever register a different set, or the same set in a different order, every packet still
 * deserialises — into the wrong type, silently. So there is exactly one list, here, and neither
 * side is allowed to register a packet of its own.
 *
 * <p><b>Rules for changing this list:</b> only append, never insert or reorder, and bump
 * {@code NetConfig.PROTOCOL_VERSION} whenever it changes.
 */
public final class NetworkRegistration {

    private static final List<Class<?>> TYPES = List.of(
        // Marker, registered first so the field type of any future polymorphic field resolves.
        Packet.class,

        // Client to server.
        PacketJoinRequest.class,
        PacketPing.class,
        PacketLeaveRequest.class,

        // Server to client.
        PacketJoinAccept.class,
        PacketJoinReject.class,
        PacketPong.class,
        PacketGameState.class,

        // Phase 1: Player input and state types (append-only)
        PacketPlayerInput.class,
        Player.class,
        ArrayList.class,

        // Phase 3: Combat — rounds in flight, damage and kills (append-only)
        Projectile.class,
        HitZone.class,
        PacketDamageEvent.class,
        PacketKillEvent.class,

        // Phase 4: Loadout — composition updates and the loadout carried inside Player (append-only)
        PacketLoadoutUpdate.class,
        WeaponItem.class,
        PlayerLoadout.class,

        // Phase 7: chat transport and the server-pushed console capability (append-only)
        ChatChannel.class,
        ChatTarget.class,
        ChatMessage.class,
        Permission.class,
        PacketChatRequest.class,
        PacketChatMessage.class,
        PacketCapabilities.class,

        // Phase 5: throwable state carried inside snapshots (append-only)
        ThrownUtility.class,

        // Phase 5 lifecycle: persistent smoke, poison and fire zones (append-only)
        UtilityZone.class,

        // Phase 6: the Q/E gadget slots carried inside PlayerLoadout (append-only)
        GadgetSlot.class);

    private NetworkRegistration() {
    }

    /**
     * Registers every packet type on {@code kryo}, in the canonical order.
     *
     * <p>Call this on the Kryo instance owned by the transport endpoint, after the transport has
     * registered its own framework classes, and on both sides.
     */
    public static void register(Kryo kryo) {
        for (Class<?> type : TYPES) {
            kryo.register(type);
        }
    }

    /** The canonical list, in registration order. Exposed so tests can assert on it. */
    public static List<Class<?>> registeredTypes() {
        return TYPES;
    }

    /**
     * True when {@code type} is a payload carried <i>inside</i> a packet rather than a packet
     * itself — the state records and the collection types that hold them.
     *
     * <p>Kryo has to know these classes too, but they are not addressed on the wire, so the
     * tests that check packet shape skip them.
     */
    public static boolean isSupportType(Class<?> type) {
        return !Packet.class.isAssignableFrom(type);
    }
}
