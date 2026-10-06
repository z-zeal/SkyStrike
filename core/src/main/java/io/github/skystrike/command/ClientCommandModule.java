package io.github.skystrike.command;

import io.github.skystrike.chat.ChatMuteList;
import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.command.ArgType;
import io.github.skystrike.shared.command.ArgTypes;
import io.github.skystrike.shared.command.CommandException;
import io.github.skystrike.shared.command.CommandRegistry;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CommandSpec;
import io.github.skystrike.shared.command.Cvar;
import io.github.skystrike.shared.command.CvarRegistry;
import io.github.skystrike.shared.command.ServerCommandCatalog;
import io.github.skystrike.ui.text.MessageBuffer;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The client-side command set (playable build plan M1 §2.5), plus registration of the shared
 * server-command metadata so {@code help}, the hint line and completion describe the commands
 * this client will forward.
 *
 * <p>Client commands never leave the process (console plan §7.3). They also never reach for
 * authoritative state: {@code bind} edits local bindings, {@code mute} edits the local cosmetic
 * mute list, {@code disconnect} severs the local transport. Anything else belongs on the server.
 */
public final class ClientCommandModule {

    /** What the client commands need from the composition root. All local, none authoritative. */
    public record Deps(
        MessageBuffer messages,
        KeyBindings bindings,
        ChatMuteList muteList,
        /** The local player's id; muting yourself is nonsense. */
        IntSupplier localPlayerId,
        /** Name lookup over the latest snapshot, for {@code mute} — exact, then unique prefix. */
        NameResolver findPlayerId,
        IntSupplier fps,
        Supplier<Float> lastFrameMillis,
        Runnable disconnectAction
    ) {
        /** -1 for "no such name", -2 for "ambiguous prefix". */
        @FunctionalInterface
        public interface NameResolver {
            int resolveId(String name);
        }
    }

    private ClientCommandModule() {
    }

    public static void registerAll(CommandRegistry registry, CvarRegistry cvars, Deps deps) {
        // The shared server-command metadata: registered handler-less, which is what makes the
        // client forward those lines after hinting and completing them. One table, two sides.
        for (CommandSpec metadata : ServerCommandCatalog.metadata()) {
            registry.register(metadata);
        }

        registry.register(help(registry, cvars));
        registry.register(CommandSpec.builder("clear")
            .description("empty the scrollback")
            .side(CommandSide.CLIENT)
            .handler((ctx, args) -> {
                deps.messages().clear();
                return CommandResult.info("Scrollback cleared.");
            })
            .build());
        registry.register(CommandSpec.builder("bind")
            .description("bind an action to a key, e.g. /bind openChat T")
            .side(CommandSide.CLIENT)
            .arg("action", actionNames(deps), "one of the rebindable actions")
            .arg("key", ArgTypes.STRING, "a key name as libGDX spells it, e.g. T, F5, SPACE")
            .handler((ctx, args) -> {
                String error = deps.bindings().bindByName(
                    args.getString("action"), args.getString("key"));
                return error == null
                    ? CommandResult.success("Bound " + args.getString("action") + " to "
                        + deps.bindings().keyNameFor(args.getString("action")) + ".")
                    : CommandResult.error(error);
            })
            .build());
        registry.register(CommandSpec.builder("unbind")
            .description("reset an action to its default key")
            .side(CommandSide.CLIENT)
            .arg("action", actionNames(deps), "the action to reset")
            .handler((ctx, args) -> {
                String error = deps.bindings().resetByName(args.getString("action"));
                return error == null
                    ? CommandResult.success("Reset " + args.getString("action")
                        + " to " + deps.bindings().keyNameFor(args.getString("action")) + ".")
                    : CommandResult.error(error);
            })
            .build());
        registry.register(CommandSpec.builder("fps")
            .description("print the current frame rate")
            .side(CommandSide.CLIENT)
            .handler((ctx, args) -> {
                Float frameMs = deps.lastFrameMillis().get();
                String detail = frameMs == null
                    ? "" : String.format(" (%.1f ms/frame)", frameMs);
                return CommandResult.info(deps.fps().getAsInt() + " fps" + detail + ".");
            })
            .build());
        registry.register(CommandSpec.builder("mute")
            .description("hide a player's chat lines locally; nobody else knows")
            .side(CommandSide.CLIENT)
            .arg("player", ArgTypes.GREEDY_STRING, "the player's name")
            .handler((ctx, args) -> {
                int id = deps.findPlayerId().resolveId(args.getString("player"));
                if (id == deps.localPlayerId().getAsInt()) {
                    return CommandResult.error("You cannot mute yourself.");
                }
                if (id < 0) {
                    return CommandResult.error("No player matches '" + args.getString("player") + "'.");
                }
                return deps.muteList().mute(id)
                    ? CommandResult.success("Muted " + args.getString("player") + " locally.")
                    : CommandResult.warning(args.getString("player") + " is already muted.");
            })
            .build());
        registry.register(CommandSpec.builder("unmute")
            .description("stop hiding a player you muted locally")
            .side(CommandSide.CLIENT)
            .arg("player", ArgTypes.GREEDY_STRING, "the player's name")
            .handler((ctx, args) -> {
                int id = deps.findPlayerId().resolveId(args.getString("player"));
                if (id < 0) {
                    return CommandResult.error("No player matches '" + args.getString("player") + "'.");
                }
                return deps.muteList().unmute(id)
                    ? CommandResult.success("Unmuted " + args.getString("player") + ".")
                    : CommandResult.warning(args.getString("player") + " was not muted.");
            })
            .build());
        registry.register(CommandSpec.builder("disconnect")
            .description("leave the match and return to the menu flow")
            .side(CommandSide.CLIENT)
            .handler((ctx, args) -> {
                deps.disconnectAction().run();
                // The action files the visible SYSTEM notice after revoking the old server's
                // capability; returning another CONSOLE line would duplicate it in dev mode.
                return CommandResult.info();
            })
            .build());

        // The quality tier is a cvar rather than a command: `/quality` alone prints current,
        // default and range for free, and `/quality low` validates itself. The FX budget reads
        // it from milestone M7.
        cvars.register(Cvar.builder("quality", ArgTypes.ENUM(QualityTier.class), "mid")
            .description("effects quality tier; respected by the FX budget when it lands (M7)")
            .build());
    }

    /** The quality tiers the FX budget will consume in milestone M7. */
    public enum QualityTier {
        LOW, MID, HIGH
    }

    private static ArgType<String> actionNames(Deps deps) {
        return new ArgType<>() {
            @Override
            public String parse(String token) throws CommandException {
                if (!deps.bindings().hasAction(token)) {
                    throw new CommandException(
                        "unknown action '" + token + "' (bindings: "
                            + String.join(", ", deps.bindings().actionNames()) + ")");
                }
                return token;
            }

            @Override
            public List<String> complete(
                String prefix, io.github.skystrike.shared.command.CommandContext context) {
                return ArgTypes.filter(deps.bindings().actionNames(), prefix);
            }

            @Override
            public String describe() {
                return "action";
            }
        };
    }

    private static CommandSpec help(CommandRegistry registry, CvarRegistry cvars) {
        return CommandSpec.builder("help")
            .description("list the commands available to you, or explain one")
            .alias("?")
            .side(CommandSide.BOTH)
            .optionalArg("command", ArgTypes.STRING, "a command or variable name")
            .handler((ctx, args) -> {
                if (!args.contains("command")) {
                    ctx.reply("Commands available to you (level " + ctx.permission() + "):");
                    for (CommandSpec spec : registry.visibleTo(ctx.permission())) {
                        ctx.reply("  " + spec.usage()
                            + (spec.description().isEmpty() ? "" : " — " + spec.description()));
                    }
                    List<Cvar> visible = cvars.visibleTo(ctx.permission());
                    if (!visible.isEmpty()) {
                        ctx.reply("Variables:");
                        for (Cvar cvar : visible) {
                            ctx.reply("  /" + cvar.name() + " — " + cvar.description());
                        }
                    }
                    ctx.reply("Type /help <command> for arguments. Server commands (shown with "
                        + "their permission) are forwarded and re-authorised by the server.");
                    return null;
                }
                String name = args.getString("command");
                CommandSpec spec = registry.find(name);
                if (spec != null && ctx.permission().atLeast(spec.permission())) {
                    spec.helpLines().forEach(ctx::reply);
                    return null;
                }
                Cvar cvar = cvars.find(name);
                if (cvar != null && ctx.permission().atLeast(cvar.permission())) {
                    cvar.describeLines().forEach(ctx::reply);
                    return null;
                }
                return CommandResult.error(registry.unknownLines(name, ctx.permission()));
            })
            .build();
    }
}
