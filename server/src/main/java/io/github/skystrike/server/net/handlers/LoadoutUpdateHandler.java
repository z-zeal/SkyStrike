package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.model.LoadoutOptions;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;

/**
 * Applies {@link PacketLoadoutUpdate}: the client asks for a new loadout composition, the server
 * validates every field and stores the choice on the session. The composition itself is applied
 * at the next respawn — nothing here touches the live loadout mid-life.
 *
 * <p>Validation is the whole point of the packet existing server-side: slot 1 takes any gun,
 * slot 2 takes sidearms only — pistols and revolvers — slot 3 takes any melee weapon, slots
 * 4–5 take utility ordinals, and the Q/E slots take gadget ordinals ({@code NONE} included, so
 * a gadget can be explicitly dropped). Anything else is dropped field by field, not trusted
 * field by field. Those rules are {@link LoadoutOptions}, shared with the M4 loadout picker so
 * the picker cannot offer a choice this handler would silently drop. The duplicate-gadget rule
 * lives with the composition in {@code PlayerLoadout.setGadgets}, so client prediction and the
 * server share it.
 */
public final class LoadoutUpdateHandler implements PacketHandler<PacketLoadoutUpdate> {

    private final PlayerRegistry players;

    public LoadoutUpdateHandler(PlayerRegistry players) {
        this.players = players;
    }

    @Override
    public void handle(Connection connection, PacketLoadoutUpdate packet) {
        PlayerSession session = players.byConnection(connection);
        if (session == null || packet == null || packet.isEmpty()) {
            return;
        }

        int primary = accept(packet.primary, LoadoutOptions.isLegalPrimary(packet.primary));
        int handgun = accept(packet.handgun, LoadoutOptions.isLegalHandgun(packet.handgun));
        int melee = accept(packet.melee, LoadoutOptions.isLegalMelee(packet.melee));
        int utilityA = accept(packet.utilityA, LoadoutOptions.isLegalUtility(packet.utilityA));
        int utilityB = accept(packet.utilityB, LoadoutOptions.isLegalUtility(packet.utilityB));
        int gadgetQ = accept(packet.gadgetQ, LoadoutOptions.isLegalGadget(packet.gadgetQ));
        int gadgetE = accept(packet.gadgetE, LoadoutOptions.isLegalGadget(packet.gadgetE));

        session.requestLoadout(primary, handgun, melee, utilityA, utilityB, gadgetQ, gadgetE);
        System.out.printf(
            "[loadout] player=%d requested primary=%d handgun=%d melee=%d utilityA=%d utilityB=%d"
                + " gadgetQ=%d gadgetE=%d (applies at respawn)%n",
            session.playerId(), primary, handgun, melee, utilityA, utilityB, gadgetQ, gadgetE);
    }

    /**
     * One field's verdict: the keep-sentinel always survives, a legal ordinal survives, and
     * anything else becomes the keep-sentinel — dropped, never clamped to a neighbour.
     */
    private static int accept(int requested, boolean legal) {
        if (requested == PacketLoadoutUpdate.KEEP_CURRENT) {
            return PacketLoadoutUpdate.KEEP_CURRENT;
        }
        return legal ? requested : PacketLoadoutUpdate.KEEP_CURRENT;
    }
}
