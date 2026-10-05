package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
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
 * slot 2 takes pistols only (it is the handgun slot), slot 3 takes any melee weapon. Anything
 * else is dropped field by field, not trusted field by field.
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

        session.requestLoadout(primary, handgun, melee);
        System.out.printf("[loadout] player=%d requested primary=%d handgun=%d melee=%d (applies at respawn)%n",
            session.playerId(), primary, handgun, melee);
    }

    private static boolean isValidGun(int ordinal) {
        return ordinal == PacketLoadoutUpdate.KEEP_CURRENT || WeaponId.isValidOrdinal(ordinal);
    }

    private static boolean isValidPistol(int ordinal) {
        if (ordinal == PacketLoadoutUpdate.KEEP_CURRENT) {
            return true;
        }
        if (!WeaponId.isValidOrdinal(ordinal)) {
            return false;
        }
        return WeaponRegistry.ofOrdinal(ordinal).ballistics().weaponClass() == WeaponClass.PISTOL;
    }
}
