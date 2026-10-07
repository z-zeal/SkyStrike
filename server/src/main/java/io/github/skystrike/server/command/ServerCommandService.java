package io.github.skystrike.server.command;

import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.command.CommandContext;
import io.github.skystrike.shared.command.CommandDispatcher;
import io.github.skystrike.shared.command.CommandException;
import io.github.skystrike.shared.command.CommandParser;
import io.github.skystrike.shared.command.CommandRegistry;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CvarRegistry;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.text.RateLimiter;
import io.github.skystrike.shared.text.TextLimits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses, authorises and executes command lines for player callers (console plan §11 build-order
 * Phase 6; playable build plan M1 §2.3).
 *
 * <p>The three words in that sentence are ordered on purpose. A line is parsed <i>here</i>, from
 * scratch — the client may have shown hints for the same text, but that parse is a convenience
 * and never a boundary. Callers are authorised against the level {@link PermissionResolver}
 * resolves from the server's own records, which is why a forged capability flag on the wire buys
 * exactly one thing: a well-formatted refusal.
 *
 * <p>Like {@code ChatService}, this class knows nothing about sockets. The handler hands in the
 * caller's id and name and ships the returned result back to exactly one player.
 *
 * <p><b>Threading.</b> Tick thread only.
 */
public final class ServerCommandService {

    private final PlayerRegistry players;
    private final PermissionResolver resolver;
    private final CommandRegistry registry = new CommandRegistry();
    private final CvarRegistry cvars = new CvarRegistry();
    private final CommandDispatcher dispatcher;
    private final Map<Integer, RateLimiter> limiters = new HashMap<>();

    public ServerCommandService(PlayerRegistry players, PermissionResolver resolver, ServerCommandModule.Deps deps) {
        this(players, resolver, deps, DebugFlags.enabled());
    }

    /**
     * Creates a service with debug metadata only when the host explicitly enabled it through
     * development mode or an ADMIN grant. Permission checks still run for every request.
     */
    public ServerCommandService(
        PlayerRegistry players,
        PermissionResolver resolver,
        ServerCommandModule.Deps deps,
        boolean debugCommandsEnabled
    ) {
        if (players == null || resolver == null || deps == null) {
            throw new IllegalArgumentException("players, resolver and deps are required");
        }
        this.players = players;
        this.resolver = resolver;
        ServerCommandModule.registerAll(registry, deps, debugCommandsEnabled);
        this.dispatcher = new CommandDispatcher(registry, cvars);
    }

    /** The registry, exposed for {@code ServerConsole} (a later build phase) and tests. */
    public CommandRegistry registry() {
        return registry;
    }

    /**
     * Runs one line for one caller.
     *
     * @param playerId the caller, as the server's connection bookkeeping knows them
     * @param name     the caller's server-accepted name, never one claimed inside the line
     * @param rawLine  the command text without a leading slash
     * @param nowMillis server clock
     * @return the outcome; never null — unknown names produce the refusal here, because on this
     *     side of the wire there is nowhere left to forward to
     */
    public CommandResult execute(int playerId, String name, String rawLine, long nowMillis) {
        RateLimiter limiter = limiters.computeIfAbsent(
            playerId,
            id -> new RateLimiter(
                TextLimits.COMMAND_RATE_CAPACITY, TextLimits.COMMAND_RATE_WINDOW_MILLIS, nowMillis));
        if (!limiter.tryAcquire(nowMillis)) {
            return CommandResult.error("You are sending commands too quickly. Slow down.");
        }

        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty()) {
            return CommandResult.error("No command given. Type /help for what is available.");
        }
        if (line.codePointCount(0, line.length()) > TextLimits.MAX_COMMAND_LINE_CODE_POINTS) {
            return CommandResult.error(
                "Command line is too long (max " + TextLimits.MAX_COMMAND_LINE_CODE_POINTS
                    + " characters).");
        }

        Permission level = resolver.resolve(playerId, name);
        CommandContext context =
            new CommandContext(playerId, name, level, CommandSide.SERVER, nowMillis, playerNames());

        CommandResult result = dispatcher.dispatch(line, context);
        if (result == null) {
            final String attempted;
            try {
                attempted = CommandParser.leadingName(line);
            } catch (CommandException bad) {
                return CommandResult.error(bad.getMessage());
            }
            result = CommandResult.error(registry.unknownLines(attempted, level));
        }
        return clampForWire(result);
    }

    /** Names of the joined, for {@code PLAYER} argument completion. */
    private List<String> playerNames() {
        List<String> names = new ArrayList<>();
        for (PlayerSession session : players.all()) {
            names.add(session.name());
        }
        return names;
    }

    /** Drops the rate-limit state of a departing player. */
    public void forget(int playerId) {
        limiters.remove(playerId);
    }

    public void clear() {
        limiters.clear();
    }

    /**
     * Bounds the output before it is serialised. A command's enthusiasm must not turn into an
     * oversized packet, and the client renders each line as-is.
     */
    private static CommandResult clampForWire(CommandResult result) {
        List<String> lines = result.lines();
        List<String> clamped = new ArrayList<>(Math.min(lines.size(), TextLimits.MAX_COMMAND_RESPONSE_LINES));
        for (int i = 0; i < lines.size() && i < TextLimits.MAX_COMMAND_RESPONSE_LINES; i++) {
            String line = lines.get(i);
            if (line.codePointCount(0, line.length()) > TextLimits.MAX_COMMAND_RESPONSE_LINE_CODE_POINTS) {
                int end = line.offsetByCodePoints(0, TextLimits.MAX_COMMAND_RESPONSE_LINE_CODE_POINTS - 1);
                line = line.substring(0, end) + "…";
            }
            clamped.add(line);
        }
        CommandResult trimmed;
        if (result.ok()) {
            trimmed = switch (result.severity()) {
                case SUCCESS -> CommandResult.success();
                case WARNING -> CommandResult.warning();
                default -> CommandResult.info();
            };
        } else {
            trimmed = CommandResult.error();
        }
        return trimmed.withExtraLines(clamped);
    }
}
