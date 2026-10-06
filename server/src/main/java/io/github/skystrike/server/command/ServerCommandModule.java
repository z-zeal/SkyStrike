package io.github.skystrike.server.command;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.chat.ChatService;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.server.player.RespawnService;
import io.github.skystrike.server.weapons.LoadoutSystem;
import io.github.skystrike.shared.command.ArgTypes;
import io.github.skystrike.shared.command.Arguments;
import io.github.skystrike.shared.command.CommandContext;
import io.github.skystrike.shared.command.CommandHandler;
import io.github.skystrike.shared.command.CommandRegistry;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CommandSpec;
import io.github.skystrike.shared.command.PlayerRef;
import io.github.skystrike.shared.command.ServerCommandCatalog;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.debug.DebugState;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.model.WeaponItem;
import io.github.skystrike.shared.net.Packet;
import io.github.skystrike.shared.net.c2s.PacketLoadoutUpdate;
import io.github.skystrike.shared.net.s2c.PacketChatMessage;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.shared.text.ChatMessage;
import io.github.skystrike.shared.weapons.WeaponId;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Attaches behaviour to the shared server-command metadata (playable build plan M1 §2.5).
 *
 * <p>The specs themselves are declared once in {@link ServerCommandCatalog} so the client can
 * offer help and completion for the commands it forwards; this module maps each name to its
 * handler, and an unmapped name is a startup error, never a silently dead command. Everything
 * that changes game state goes through the authoritative services — a command is an operator's
 * keyboard, not a second simulation path.
 */
public final class ServerCommandModule {

    /** How one addressed packet reaches one player. Same contract as the chat handler's. */
    @FunctionalInterface
    public interface Dispatch {
        void send(int playerId, Packet packet);
    }

    /** The authoritative services the command set touches. */
    public record Deps(
        PlayerRegistry players,
        RespawnService respawnService,
        LoadoutSystem loadoutSystem,
        ChatService chat,
        Dispatch dispatch,
        /** The respawn-time preparation the tick hook applies; commands reuse it verbatim. */
        Consumer<PlayerSession> prepareForLife,
        /** The global (non-per-player) debug state {@code timescale} and {@code dumpstate} read. */
        DebugState debugState
    ) {
        public Deps {
            if (players == null || respawnService == null || loadoutSystem == null
                || chat == null || dispatch == null || prepareForLife == null || debugState == null) {
                throw new IllegalArgumentException("all deps are required");
            }
        }
    }

    /** One per-player session cheat flag, toggled identically from the console. */
    private interface SessionFlag {
        boolean get(PlayerSession session);
        void set(PlayerSession session, boolean value);
    }

    private ServerCommandModule() {
    }

    /**
     * Registers every catalog command with its handler into {@code registry}, plus the
     * server-side {@code help} that describes the result.
     */
    public static void registerAll(CommandRegistry registry, Deps deps) {
        Map<String, CommandHandler> handlers = handlers(deps);
        boolean debugEnabled = DebugFlags.enabled();
        for (CommandSpec metadata : ServerCommandCatalog.metadata()) {
            if (ServerCommandCatalog.isDebugOnly(metadata.name()) && !debugEnabled) {
                // Build plan M3 §4: with the master switch off, these are invisible, not
                // refused — never registered, so dispatch reports them exactly like a typo.
                continue;
            }
            CommandHandler handler = handlers.get(metadata.name());
            if (handler == null) {
                throw new IllegalStateException(
                    "no server handler registered for /" + metadata.name());
            }
            registry.register(metadata.withHandler(handler));
        }
        registry.register(help(registry));
    }

    private static Map<String, CommandHandler> handlers(Deps deps) {
        Map<String, CommandHandler> handlers = new HashMap<>();
        handlers.put("players", (ctx, args) -> playersCommand(deps));
        handlers.put("say", (ctx, args) -> sayCommand(deps, ctx, args));
        handlers.put("respawn", (ctx, args) -> respawnCommand(deps, ctx, args));
        handlers.put("kill", (ctx, args) -> killCommand(deps, ctx, args));
        handlers.put("teleport", (ctx, args) -> teleportCommand(deps, ctx, args));
        handlers.put("give", (ctx, args) -> giveCommand(deps, ctx, args));
        handlers.put("setammo", (ctx, args) -> setAmmoCommand(deps, ctx, args));
        handlers.put("sethealth", (ctx, args) -> setHealthCommand(deps, ctx, args));
        handlers.put("setteam", (ctx, args) -> setTeamCommand(deps, ctx, args));
        handlers.put("kick", (ctx, args) -> kickCommand(deps, ctx, args));
        handlers.put("noclip", cheat(deps, "noclip", new SessionFlag() {
            @Override public boolean get(PlayerSession s) { return s.noclip(); }
            @Override public void set(PlayerSession s, boolean v) { s.setNoclip(v); }
        }));
        handlers.put("godmode", cheat(deps, "godmode", new SessionFlag() {
            @Override public boolean get(PlayerSession s) { return s.godmode(); }
            @Override public void set(PlayerSession s, boolean v) { s.setGodmode(v); }
        }));
        handlers.put("infiniteammo", cheat(deps, "infiniteammo", new SessionFlag() {
            @Override public boolean get(PlayerSession s) { return s.infiniteAmmo(); }
            @Override public void set(PlayerSession s, boolean v) { s.setInfiniteAmmo(v); }
        }));
        handlers.put("timescale", (ctx, args) -> timescaleCommand(deps, args));
        handlers.put("dumpstate", (ctx, args) -> dumpStateCommand(deps));
        return handlers;
    }

    private static CommandResult timescaleCommand(Deps deps, Arguments args) {
        if (!args.contains("multiplier")) {
            return CommandResult.info("timescale = " + deps.debugState().timescale());
        }
        float multiplier = args.getFloat("multiplier");
        deps.debugState().setTimescale(multiplier);
        return CommandResult.success("timescale set to " + deps.debugState().timescale() + ".");
    }

    private static CommandResult dumpStateCommand(Deps deps) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long maxMb = runtime.maxMemory() / (1024L * 1024L);
        lines.add("players: " + deps.players().count());
        lines.add("memory: " + usedMb + "/" + maxMb + " MB used/max");
        lines.add("timescale: " + deps.debugState().timescale());
        for (PlayerSession session : deps.players().all()) {
            Player p = session.player();
            lines.add(String.format(
                "  #%d %s pos(%.0f,%.0f) hp=%.0f alive=%s noclip=%s godmode=%s infiniteAmmo=%s",
                session.playerId(), session.name(), p.x, p.y, p.health, p.alive,
                session.noclip(), session.godmode(), session.infiniteAmmo()));
        }
        return CommandResult.info(lines);
    }

    private static CommandHandler cheat(Deps deps, String name, SessionFlag flag) {
        return (ctx, args) -> {
            PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
            if (target == null) {
                return CommandResult.error(playerError(args.getPlayer("player")));
            }
            boolean value = args.contains("state")
                ? args.getBool("state", false) : !flag.get(target);
            flag.set(target, value);
            return CommandResult.success(
                name + " " + (value ? "enabled" : "disabled") + " for " + target.name() + ".");
        };
    }

    private static CommandSpec help(CommandRegistry registry) {
        return CommandSpec.builder("help")
            .description("list the commands available to you, or explain one")
            .alias("?")
            .side(CommandSide.BOTH)
            .optionalArg("command", ArgTypes.STRING, "a command name to explain")
            .handler((ctx, args) -> {
                if (!args.contains("command")) {
                    ctx.reply("Commands available to you (level " + ctx.permission() + "):");
                    for (CommandSpec spec : registry.visibleTo(ctx.permission())) {
                        ctx.reply("  " + spec.usage() + " — " + spec.description());
                    }
                    ctx.reply("Type /help <command> for arguments.");
                    return null;
                }
                CommandSpec spec = registry.find(args.getString("command"));
                if (spec == null || !ctx.permission().atLeast(spec.permission())) {
                    return CommandResult.error(
                        registry.unknownLines(args.getString("command"), ctx.permission()));
                }
                spec.helpLines().forEach(ctx::reply);
                return null;
            })
            .build();
    }

    private static CommandResult playersCommand(Deps deps) {
        PlayerRegistry registry = deps.players();
        if (registry.count() == 0) {
            return CommandResult.info("No players are connected.");
        }
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(registry.count() + " player(s):");
        for (PlayerSession session : registry.all()) {
            Player p = session.player();
            lines.add(String.format(
                "  #%d %s — %s, %.0f hp, %s",
                session.playerId(),
                session.name(),
                Team.fromIndex(p.teamIndex).displayName(),
                p.health,
                p.alive ? "alive" : "dead"));
        }
        return CommandResult.info(lines);
    }

    private static CommandResult sayCommand(Deps deps, CommandContext ctx, Arguments args) {
        String text = args.getString("message");
        ChatService.Result result = deps.chat().announce(
            ChatMessage.system(ctx.nowMillis(), ChatChannel.SERVER, text));
        if (!result.accepted()) {
            return CommandResult.error("The announcement was empty after sanitisation.");
        }
        PacketChatMessage out = new PacketChatMessage(result.message());
        for (Integer recipientId : result.recipientIds()) {
            if (recipientId != null) {
                deps.dispatch().send(recipientId, out);
            }
        }
        return CommandResult.success(
            "Announcement sent to " + result.recipientIds().size() + " player(s).");
    }

    private static CommandResult respawnCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        deps.respawnService().respawn(target.player());
        deps.prepareForLife().accept(target);
        return CommandResult.success("Respawned " + target.name() + ".");
    }

    private static CommandResult killCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        if (!target.player().alive) {
            return CommandResult.warning(target.name() + " is already dead.");
        }
        deps.respawnService().kill(target.player());
        return CommandResult.success("Killed " + target.name() + ".");
    }

    private static CommandResult teleportCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        float x = args.getFloat("x");
        float y = args.getFloat("y");
        Player player = target.player();
        player.x = x;
        player.y = y;
        player.vx = 0f;
        player.vy = 0f;
        return CommandResult.success(
            String.format("Teleported %s to (%.0f, %.0f).", target.name(), x, y));
    }

    private static CommandResult giveCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        WeaponId weapon = (WeaponId) args.get("weapon");
        // KEEP_CURRENT everywhere but the primary, which becomes the requested weapon.
        target.requestLoadout(
            weapon.ordinal(),
            PacketLoadoutUpdate.KEEP_CURRENT,
            PacketLoadoutUpdate.KEEP_CURRENT);
        // A debug gift is expected now, not on the next life — the same code path a respawn
        // takes applies it, so live state can only ever be rebuilt one way.
        deps.prepareForLife().accept(target);
        target.player().weaponId = target.player().loadout.heldWeaponWireId();
        return CommandResult.success(
            "Gave " + target.name() + " a "
                + WeaponRegistry.displayNameForWireId(weapon.ordinal()) + ".");
    }

    private static CommandResult setAmmoCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        WeaponItem item = target.player().loadout == null
            ? null : target.player().loadout.activeItem();
        if (item == null) {
            return CommandResult.error(target.name() + " is not holding a gun.");
        }
        item.magazine = args.getInt("magazine");
        if (args.contains("reserve")) {
            item.reserve = args.getInt("reserve");
        }
        return CommandResult.success(String.format(
            "%s now carries %d/%d rounds.", target.name(), item.magazine, item.reserve));
    }

    private static CommandResult setHealthCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        int hp = args.getInt("hp");
        target.player().health = hp;
        return CommandResult.success(target.name() + " now has " + hp + " hp.");
    }

    private static CommandResult setTeamCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerSession target = resolveTarget(deps, ctx, args.getPlayer("player"));
        if (target == null) {
            return CommandResult.error(playerError(args.getPlayer("player")));
        }
        Team team = (Team) args.get("team");
        target.player().teamIndex = team.index();
        deps.respawnService().respawn(target.player());
        deps.prepareForLife().accept(target);
        return CommandResult.success(
            target.name() + " is now on " + team.displayName() + " and has been respawned.");
    }

    private static CommandResult kickCommand(Deps deps, CommandContext ctx, Arguments args) {
        PlayerRef ref = args.getPlayer("player");
        PlayerSession target = resolveTarget(deps, ctx, ref);
        if (target == null) {
            return CommandResult.error(playerError(ref));
        }
        if (target.playerId() == ctx.callerId()) {
            return CommandResult.error("Kicking yourself is what /disconnect is for.");
        }
        String reason = args.contains("reason") ? args.getString("reason") : "no reason given";
        deps.dispatch().send(
            target.playerId(),
            new PacketChatMessage(ChatMessage.system(
                ctx.nowMillis(), ChatChannel.SERVER, "You were kicked (" + reason + ").")));
        Connection connection = target.connection();
        if (connection != null) {
            connection.close();
        }
        return CommandResult.success("Kicked " + target.name() + " (" + reason + ").");
    }

    /** Omitted target = the caller; a ref resolves against the authoritative registry. */
    private static PlayerSession resolveTarget(Deps deps, CommandContext ctx, PlayerRef ref) {
        if (ref == null) {
            return ctx.callerId() == CommandContext.NO_CALLER
                ? null : deps.players().byPlayerId(ctx.callerId());
        }
        if (ref.hasId()) {
            return deps.players().byPlayerId(ref.id());
        }
        PlayerRegistry registry = deps.players();
        // Exact case-insensitive first, then a unique-prefix courtesy — never a guessing game
        // between two players who share a name fragment.
        PlayerSession prefixMatch = null;
        for (PlayerSession session : registry.all()) {
            if (session.name().equalsIgnoreCase(ref.token())) {
                return session;
            }
            if (session.name().toLowerCase(java.util.Locale.ROOT)
                .startsWith(ref.token().toLowerCase(java.util.Locale.ROOT))) {
                if (prefixMatch != null) {
                    return null; // ambiguous prefix; the caller must type more
                }
                prefixMatch = session;
            }
        }
        return prefixMatch;
    }

    private static String playerError(PlayerRef ref) {
        return ref == null
            ? "No target and no caller."
            : "No player matches '" + ref.token() + "'.";
    }
}
