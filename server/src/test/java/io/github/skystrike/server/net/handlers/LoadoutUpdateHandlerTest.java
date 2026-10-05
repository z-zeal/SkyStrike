package io.github.skystrike.server.net.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.weapons.MeleeId;
import io.github.skystrike.shared.weapons.WeaponId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The loadout edit request: valid fields stick, invalid ones are dropped field by field, and
 * nothing takes effect on the live loadout until the respawn applies it.
 */
class LoadoutUpdateHandlerTest {

    private static final class FakeConnection extends Connection {
        private final int id;

        FakeConnection(int id) {
            this.id = id;
        }

        @Override
        public int getID() {
            return id;
        }
    }

    private PlayerRegistry registry;
    private LoadoutUpdateHandler handler;
    private Connection connection;
    private PlayerSession session;

    @BeforeEach
    void setUp() {
        registry = new PlayerRegistry();
        handler = new LoadoutUpdateHandler(registry);
        connection = new FakeConnection(7);
        session = registry.register(connection, 7, "Nova", 0, 100f, 100f);
    }

    @Test
    @DisplayName("a valid request is recorded, per field, and applied at respawn time")
    void validRequestIsRecorded() {
        handler.handle(connection, new PacketLoadoutUpdate(
            WeaponId.CATHEDRAL.ordinal(), PacketLoadoutUpdate.KEEP_CURRENT, MeleeId.WINTER_KATANA.ordinal()));

        assertEquals(WeaponId.CATHEDRAL.ordinal(), session.requestedPrimary());
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(), session.requestedHandgun(), "untouched");
        assertEquals(MeleeId.WINTER_KATANA.ordinal(), session.requestedMelee());

        // The live loadout is untouched until the respawn flow applies the request.
        assertEquals(WeaponId.DEFAULT, session.player().loadout.primary.weaponId());

        assertTrue(session.applyRequestedLoadout());
        assertEquals(WeaponId.CATHEDRAL, session.player().loadout.primary.weaponId());
        assertEquals(5, session.player().loadout.primary.magazine, "a fresh weapon comes full");
        assertEquals(MeleeId.WINTER_KATANA, session.player().loadout.meleeId());
    }

    @Test
    @DisplayName("a non-pistol cannot take the handgun slot, while the rest of the packet lives")
    void handgunSlotRejectsNonPistols() {
        handler.handle(connection, new PacketLoadoutUpdate(
            WeaponId.SMOKE_STITCH.ordinal(), WeaponId.CATHEDRAL.ordinal(), PacketLoadoutUpdate.KEEP_CURRENT));

        assertEquals(WeaponId.SMOKE_STITCH.ordinal(), session.requestedPrimary(), "the valid field stuck");
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(), session.requestedHandgun(),
            "a sniper rifle is not a handgun: the field was dropped");

        // A revolver is a sidearm too, and it does pass.
        handler.handle(connection, new PacketLoadoutUpdate(
            PacketLoadoutUpdate.KEEP_CURRENT, WeaponId.LONGSPUR_44.ordinal(), PacketLoadoutUpdate.KEEP_CURRENT));
        assertEquals(WeaponId.LONGSPUR_44.ordinal(), session.requestedHandgun());
    }

    @Test
    @DisplayName("garbage ordinals and empty packets change nothing")
    void invalidOrdinalsAreDropped() {
        handler.handle(connection, new PacketLoadoutUpdate(999, -7, 42)); // 42 >= 21 melee ids

        assertEquals(WeaponId.DEFAULT.ordinal(), session.requestedPrimary());
        assertEquals(WeaponId.DEFAULT_SIDEARM.ordinal(), session.requestedHandgun());
        assertEquals(MeleeId.DEFAULT.ordinal(), session.requestedMelee());

        handler.handle(connection, new PacketLoadoutUpdate()); // all KEEP_CURRENT
        handler.handle(connection, null);
        handler.handle(null, new PacketLoadoutUpdate(0, 0, 0)); // no session behind it
        assertEquals(WeaponId.DEFAULT.ordinal(), session.requestedPrimary());
    }

    @Test
    @DisplayName("later requests overwrite earlier ones, still as full packets")
    void laterRequestsOverwrite() {
        handler.handle(connection, new PacketLoadoutUpdate(
            WeaponId.BLACK_CORRIDOR.ordinal(), PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT));
        handler.handle(connection, new PacketLoadoutUpdate(
            WeaponId.SMOKE_STITCH.ordinal(), PacketLoadoutUpdate.KEEP_CURRENT, PacketLoadoutUpdate.KEEP_CURRENT));

        assertEquals(WeaponId.SMOKE_STITCH.ordinal(), session.requestedPrimary());
    }
}
