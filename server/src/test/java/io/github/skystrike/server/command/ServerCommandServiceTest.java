package io.github.skystrike.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.server.chat.ChatService;
import io.github.skystrike.server.combat.BulletSystem;
import io.github.skystrike.server.combat.MeleeSystem;
import io.github.skystrike.server.gadget.ShieldSystem;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.RespawnService;
import io.github.skystrike.server.player.SpawnService;
import io.github.skystrike.server.utility.UtilitySystem;
import io.github.skystrike.server.weapons.FireController;
import io.github.skystrike.server.weapons.LoadoutSystem;
import io.github.skystrike.server.weapons.RecoilService;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.text.TextLimits;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The M1 gate, pinned: a caller is authorised from the server's own records on every line, so
 * nothing a client can claim — including a hand-forged capability packet — changes what it may
 * execute. Everything here runs without a socket, exactly like the chat relay's tests.
 */
class ServerCommandServiceTest {

    /** A roster backed by the real registry, as {@code GameServer} wires it. */
    private static final class RegistryRoster implements ChatService.Roster {
        private final PlayerRegistry players;

        RegistryRoster(PlayerRegistry players) {
            this.players = players;
        }

        @Override
        public Collection<Integer> playerIds() {
            List<Integer> ids = new ArrayList<>();
            for (PlayerSession session : players.all()) {
                ids.add(session.playerId());
            }
            return ids;
        }

        @Override
        public int teamIndexOf(int playerId) {
            PlayerSession session = players.byPlayerId(playerId);
            return session == null ? ChatService.NOT_JOINED : session.player().teamIndex;
        }
    }

    private PlayerRegistry players;
    private PermissionResolver resolver;
    private ServerCommandService service;
    private List<Packet> sent;
    private List<Integer> recipients;
    private LoadoutSystem loadoutSystem;

    @BeforeEach
    void setUp() {
        ArenaMap arena = ArenaMap.standard();
        players = new PlayerRegistry();
        resolver = new PermissionResolver();
        sent = new ArrayList<>();
        recipients = new ArrayList<>();
        RespawnService respawn = new RespawnService(new SpawnService(arena));
        ChatService chat = new ChatService(new RegistryRoster(players));
        loadoutSystem = new LoadoutSystem(
            new FireController(new Random(), new RecoilService()),
            new MeleeSystem(),
            new BulletSystem(arena),
            new UtilitySystem(arena),
            new ShieldSystem());
        service = new ServerCommandService(
            players,
            resolver,
            new ServerCommandModule.Deps(
                players,
                respawn,
                loadoutSystem,
                chat,
                (playerId, packet) -> {
                    recipients.add(playerId);
                    sent.add(packet);
                },
                session -> {
                    session.applyRequestedLoadout();
                    loadoutSystem.resetForRespawn(session);
                }));

        players.register(null, 1, "Nova", 0, 100f, 200f);
        players.register(null, 2, "Rook", 1, 200f, 200f);
    }

    private CommandResult run(int playerId, String name, String line) {
        return service.execute(playerId, name, line, 1_000L);
    }

    @Test
    @DisplayName("an ordinary player is refused privileged commands with the typo response")
    void unprivilegedCallerIsRefusedGenerically() {
        CommandResult result = run(1, "Nova", "noclip on");
        assertFalse(result.ok());
        assertTrue(result.lines().get(0).startsWith("Unknown command 'noclip'"),
            result.lines().get(0));

        // And the refusal changed nothing: no flag was set, no level was granted.
        assertFalse(players.byPlayerId(1).noclip());
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));
    }

    @Test
    @DisplayName("help listing is filtered: a player sees only what they may run")
    void helpHidesAboveLevel() {
        CommandResult playerHelp = run(1, "Nova", "help");
        String joined = String.join("\n", playerHelp.lines());
        assertTrue(joined.contains("/players"), joined);
        assertFalse(joined.contains("noclip"), joined);
        assertFalse(joined.contains("kick"), joined);

        resolver.grant("Nova", Permission.ADMIN);
        CommandResult adminHelp = service.execute(1, "Nova", "help", 60_000L);
        String adminJoined = String.join("\n", adminHelp.lines());
        assertTrue(adminJoined.contains("noclip"), adminJoined);
        assertTrue(adminJoined.contains("kick"), adminJoined);
    }

    @Test
    @DisplayName("a granted caller runs cheats; an omitted state toggles the flag")
    void cheatCommandsFlipSessionFlags() {
        resolver.grant("Nova", Permission.ADMIN);

        CommandResult on = service.execute(1, "Nova", "noclip on", 2_000L);
        assertTrue(on.ok(), on.lines().toString());
        assertTrue(players.byPlayerId(1).noclip());

        CommandResult toggle = service.execute(1, "Nova", "godmode", 2_500L);
        assertTrue(toggle.ok(), toggle.lines().toString());
        assertTrue(players.byPlayerId(1).godmode());
        assertTrue(toggle.lines().get(0).contains("enabled"), toggle.lines().get(0));

        // Targeting moves with the player argument.
        CommandResult other = service.execute(1, "Nova", "infiniteammo on Rook", 3_000L);
        assertTrue(other.ok());
        assertTrue(players.byPlayerId(2).infiniteAmmo());
        assertFalse(players.byPlayerId(1).infiniteAmmo());
    }

    @Test
    @DisplayName("sethealth and teleport mutate the authoritative player, errors mutate nothing")
    void stateChangesGoThroughThePlayerRecord() {
        resolver.grant("Nova", Permission.MODERATOR);

        CommandResult hp = service.execute(1, "Nova", "sethealth 77", 1_500L);
        assertTrue(hp.ok());
        assertEquals(77f, players.byPlayerId(1).player().health);

        CommandResult others = service.execute(1, "Nova", "sethealth 33 Rook", 2_000L);
        assertTrue(others.ok());
        assertEquals(33f, players.byPlayerId(2).player().health);

        CommandResult tp = service.execute(1, "Nova", "teleport 500 600", 2_500L);
        assertTrue(tp.ok());
        assertEquals(500f, players.byPlayerId(1).player().x);
        assertEquals(600f, players.byPlayerId(1).player().y);

        CommandResult bad = service.execute(1, "Nova", "teleport 99999 0", 3_000L);
        assertFalse(bad.ok());
        assertEquals("Usage: /teleport <x> <y> [player]", bad.lines().get(1));
        assertEquals(500f, players.byPlayerId(1).player().x, "a refused command changes nothing");
    }

    @Test
    @DisplayName("say is broadcast to everyone through the chat dispatch; the message is sanitised")
    void sayBroadcastsThroughChatPath() {
        resolver.grant("Nova", Permission.MODERATOR);

        CommandResult said = service.execute(1, "Nova", "say Rotate to mid, please", 1_500L);
        assertTrue(said.ok());
        assertEquals(List.of(1, 2), recipients);
        PacketChatMessage delivered = (PacketChatMessage) sent.get(0);
        assertEquals("Rotate to mid, please", delivered.message.body);
        assertEquals(io.github.skystrike.shared.text.ChatChannel.SERVER, delivered.message.channel);
    }

    @Test
    @DisplayName("give applies a requested primary immediately through the respawn path")
    void giveSwapsPrimaryNow() {
        resolver.grant("Nova", Permission.ADMIN);

        CommandResult given = service.execute(1, "Nova", "give cathedral", 1_500L);
        assertTrue(given.ok(), given.lines().toString());
        assertEquals(WeaponId.CATHEDRAL.ordinal(),
            players.byPlayerId(1).player().loadout.primary.weapon);
        assertEquals(WeaponId.CATHEDRAL.ordinal(), players.byPlayerId(1).requestedPrimary());
    }

    @Test
    @DisplayName("kick tells the player and closes their slot, and cannot target yourself")
    void kickLifecycle() {
        resolver.grant("Nova", Permission.MODERATOR);

        CommandResult self = service.execute(1, "Nova", "kick Nova", 1_500L);
        assertFalse(self.ok());

        CommandResult kicked = service.execute(1, "Nova", "kick Rook repeated teamkilling", 2_000L);
        assertTrue(kicked.ok(), kicked.lines().toString());
        assertEquals(List.of(2), recipients);
        assertTrue(sent.get(0) instanceof PacketChatMessage);
    }

    @Test
    @DisplayName("the command budget is six per window per caller, not per victim")
    void rateLimited() {
        resolver.grant("Nova", Permission.ADMIN);
        for (int i = 0; i < TextLimits.COMMAND_RATE_CAPACITY; i++) {
            assertTrue(service.execute(1, "Nova", "godmode", 1_000L).ok(), "burst " + i);
        }
        CommandResult refused = service.execute(1, "Nova", "godmode", 1_000L);
        assertFalse(refused.ok());
        assertTrue(refused.lines().get(0).contains("too quickly"), refused.lines().get(0));

        // The other player's budget is untouched.
        assertTrue(service.execute(2, "Rook", "players", 1_000L).ok());

        // After the window slides, commands flow again.
        assertTrue(service.execute(
            1, "Nova", "godmode", 1_000L + TextLimits.COMMAND_RATE_WINDOW_MILLIS + 1).ok());
    }

    @Test
    @DisplayName("overlong and empty lines are refused before parsing")
    void malformedLinesRefusedEarly() {
        String huge = "say " + "x".repeat(TextLimits.MAX_COMMAND_LINE_CODE_POINTS + 50);
        CommandResult tooLong = run(1, "Nova", huge);
        assertFalse(tooLong.ok());
        assertTrue(tooLong.lines().get(0).contains("too long"), tooLong.lines().get(0));

        assertFalse(run(1, "Nova", "   ").ok());
    }

    @Test
    @DisplayName("a forged capability cannot exist: there is no packet path into the resolver")
    void wireCannotElevatePermissions() {
        // The client is *told* its capability and never sends it; the only write paths are the
        // resolver's own grant/override APIs. Asserting the shape of this class is the closest
        // a unit test gets to "a forged PacketCapabilities grants nothing".
        run(1, "Nova", "help");
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));
        CommandResult refused = service.execute(1, "Nova", "setteam b", 1_500L);
        assertFalse(refused.ok());
        assertEquals(0, players.byPlayerId(1).player().teamIndex,
            "a refused command must not touch authoritative state");
    }
}
