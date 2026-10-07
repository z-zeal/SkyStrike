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
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.debug.DebugState;
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
        Runnable disconnectAction,
        /** Where the debug-toolkit cvars (build plan M3 §4) write their mirrored read site. */
        DebugState debugState
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
        // Debug-toolkit commands (build plan M3 §4) stay unregistered — invisible, not refused —
        // while the master switch is off, matching the server's own gate on the same names.
        boolean debugEnabled = DebugFlags.enabled();
        for (CommandSpec metadata : ServerCommandCatalog.metadata()) {
            if (ServerCommandCatalog.isDebugOnly(metadata.name()) && !debugEnabled) {
                continue;
            }
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
        // it live (milestone M7).
        cvars.register(Cvar.builder("quality", ArgTypes.ENUM(QualityTier.class), "mid")
            .description("effects quality tier; read live by the FX budget (M7)")
            .build());

        // The loadout picker (build plan M4 §5). A real UI feature, not a debug one, so it is
        // registered whatever the master debug switch says — and it holds its own state: the
        // cvar *is* the picker's open flag, which is what keeps the L key, the console and the
        // HUD from each believing something different about whether the picker is up.
        cvars.register(Cvar.builder("ui_loadout", ArgTypes.BOOL, "false")
            .description("the loadout picker; changes apply at your next respawn (L)")
            .build());

        registerDebugToolkitCvars(cvars, deps);
    }

    /**
     * Debug-toolkit cvars from build plan M3 §4 and M6. The ten debug cvars are invisible — not
     * refused, never even registered — while the master switch is off, exactly like the server's
     * own gate on {@code noclip} et al. Boolean toggles mirror into {@link DebugState}, whose
     * master-gated reads keep a stray local toggle inert even if registration were bypassed. M6's
     * radius and intensity are read live from this same registry; they do not create a second
     * settings path.
     *
     * <p>{@code r_shadows} is the always-available exception: it is a real graphics setting, not
     * a debug one, so it stays registered regardless of the master switch and defaults to
     * {@code true} (the shipped soft-shadow look).
     */
    private static void registerDebugToolkitCvars(CvarRegistry cvars, Deps deps) {
        cvars.register(Cvar.builder("r_shadows", ArgTypes.BOOL, "true")
            .description("SDF soft shadows; off falls back to hard edges, as the low tier does (F3)")
            .build());

        if (!DebugFlags.enabled()) {
            return;
        }
        cvars.register(Cvar.builder("cl_debug_overlay", ArgTypes.BOOL, "false")
            .description("toggleable status readout, off by default (F1)")
            .onChange((prev, next) -> deps.debugState().setOverlay(Boolean.parseBoolean(next)))
            .build());
        cvars.register(Cvar.builder("cl_freecam", ArgTypes.BOOL, "false")
            .description("detach the camera; WASD/arrows pan, -/= zoom (F2)")
            .onChange((prev, next) -> deps.debugState().setFreecam(Boolean.parseBoolean(next)))
            .build());
        cvars.register(Cvar.builder("r_show_sdf", ArgTypes.BOOL, "false")
            .description("the raw SDF debug view, moved off F1 (F6)")
            .onChange((prev, next) -> deps.debugState().setSdfView(Boolean.parseBoolean(next)))
            .build());
        cvars.register(Cvar.builder("r_show_hitboxes", ArgTypes.BOOL, "false")
            .description("body/head/fuel-tank hit zones from HitZoneMath (F9)")
            .onChange((prev, next) -> deps.debugState().setHitboxes(Boolean.parseBoolean(next)))
            .build());
        cvars.register(Cvar.builder("r_player_light", ArgTypes.BOOL, "false")
            .description("warm-white local and visible-player lights (M6) (F10)")
            .onChange((prev, next) -> deps.debugState().setPlayerLight(Boolean.parseBoolean(next)))
            .build());
        Cvar playerLightShadows = Cvar.builder("r_player_light_shadows", ArgTypes.BOOL, "true")
            .description("SDF-shadow player lights; off disables their occlusion sampling")
            .onChange(
                (prev, next) -> deps.debugState().setPlayerLightShadows(Boolean.parseBoolean(next)))
            .build();
        cvars.register(playerLightShadows);
        // Cvar hooks fire on changes, not on construction; seed the mirrored default explicitly.
        deps.debugState().setPlayerLightShadows(Boolean.parseBoolean(playerLightShadows.value()));
        cvars.register(Cvar.builder(
                "r_player_light_radius", ArgTypes.FLOAT(32f, 512f), "140.0")
            .description("player-light radius in world units; live-tunable (M6)")
            .build());
        cvars.register(Cvar.builder(
                "r_player_light_intensity", ArgTypes.FLOAT(0f, 1f), "0.35")
            .description("player-light additive intensity from 0 to 1; live-tunable (M6)")
            .build());
        cvars.register(Cvar.builder("fx_debug", ArgTypes.BOOL, "false")
            .description("particle/light counts and phase queue against the tier budget (M7) (F11)")
            .onChange((prev, next) -> deps.debugState().setFxDebug(Boolean.parseBoolean(next)))
            .build());
        cvars.register(Cvar.builder("ui_contrast_test", ArgTypes.BOOL, "false")
            .description("four-background text legibility check (console plan §5.2) (F12)")
            .onChange((prev, next) -> deps.debugState().setContrastTest(Boolean.parseBoolean(next)))
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
