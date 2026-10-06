package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.gadget.GadgetId;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.utility.UtilityId;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponClass;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;

/**
 * Applies {@link PacketLoadoutUpdate}: the client asks for a new loadout composition, the server
 * validates every field and stores the choice on the session. The composition itself is applied
 * at the next respawn — nothing here touches the live loadout mid-life.
 *
 * <p>Validation is the whole point of the packet existing server-side: slot 1 takes any gun,
 * slot 2 takes sidearms only — pistols and revolvers — slot 3 takes any melee weapon, slots
 * 4–5 take utility ordinals, and the Q/E slots take gadget ordinals ({@code NONE} included, so
 * a gadget can be explicitly dropped). Anything else is dropped field by field, not trusted
 * field by field. The duplicate-gadget rule lives with the composition in
 * {@code PlayerLoadout.setGadgets}, so client prediction and the server share it.
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

        int primary = isValidGun(packet.primary) ? packet.primary : PacketLoadoutUpdate.KEEP_CURRENT;
        int handgun = isValidPistol(packet.handgun) ? packet.handgun : PacketLoadoutUpdate.KEEP_CURRENT;
        int melee = packet.melee == PacketLoadoutUpdate.KEEP_CURRENT || MeleeId.isValidOrdinal(packet.melee)
            ? packet.melee
            : PacketLoadoutUpdate.KEEP_CURRENT;
        int utilityA = isValidUtility(packet.utilityA)
            ? packet.utilityA : PacketLoadoutUpdate.KEEP_CURRENT;
        int utilityB = isValidUtility(packet.utilityB)
            ? packet.utilityB : PacketLoadoutUpdate.KEEP_CURRENT;
        int gadgetQ = isValidGadget(packet.gadgetQ)
            ? packet.gadgetQ : PacketLoadoutUpdate.KEEP_CURRENT;
        int gadgetE = isValidGadget(packet.gadgetE)
            ? packet.gadgetE : PacketLoadoutUpdate.KEEP_CURRENT;

        session.requestLoadout(primary, handgun, melee, utilityA, utilityB, gadgetQ, gadgetE);
        System.out.printf(
            "[loadout] player=%d requested primary=%d handgun=%d melee=%d utilityA=%d utilityB=%d"
                + " gadgetQ=%d gadgetE=%d (applies at respawn)%n",
            session.playerId(), primary, handgun, melee, utilityA, utilityB, gadgetQ, gadgetE);
    }

    private static boolean isValidGun(int ordinal) {
        return ordinal == PacketLoadoutUpdate.KEEP_CURRENT || WeaponId.isValidOrdinal(ordinal);
    }

    private static boolean isValidUtility(int ordinal) {
        return ordinal == PacketLoadoutUpdate.KEEP_CURRENT || UtilityId.isValidOrdinal(ordinal);
    }

    /** Gadget slots accept any real gadget and the explicit NONE (ordinal 0, "carry nothing"). */
    private static boolean isValidGadget(int ordinal) {
        return ordinal == PacketLoadoutUpdate.KEEP_CURRENT || GadgetId.isValidOrdinal(ordinal);
    }

    private static boolean isValidPistol(int ordinal) {
        if (ordinal == PacketLoadoutUpdate.KEEP_CURRENT) {
            return true;
        }
        if (!WeaponId.isValidOrdinal(ordinal)) {
            return false;
        }
        return WeaponRegistry.ofOrdinal(ordinal).ballistics().weaponClass().isSidearm();
    }
}
