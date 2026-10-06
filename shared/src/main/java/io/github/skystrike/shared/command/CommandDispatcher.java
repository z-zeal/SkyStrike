package io.github.skystrike.shared.command;

import java.util.List;

/**
 * The one execution path both sides use: tokenise, resolve against commands then cvars,
 * authorise, run (playable build plan M1 §2.2).
 *
 * <p>Commands win on a name collision with a cvar, and the constructor <b>refuses to build</b>
 * if the two registries share a name — an ambiguous namespace is a build-time bug, never a
 * runtime coin flip.
 *
 * <p>Authorisation mirrors the visibility rule: a caller below a spec's level gets the exact
 * "unknown command" response a typo gets. The refusal text comes from the registry so the two
 * can never drift apart.
 *
 * <p>When the name is known to neither registry, {@link #dispatch} returns {@code null} and the
 * caller decides what "unknown" means in context — on the client that can mean "forward the raw
 * line to the server"; on the server it always means the refusal.
 */
public final class CommandDispatcher {

    private final CommandRegistry commands;
    private final CvarRegistry cvars;

    public CommandDispatcher(CommandRegistry commands, CvarRegistry cvars) {
        if (commands == null || cvars == null) {
            throw new IllegalArgumentException("both registries are required");
        }
        for (CommandSpec spec : commands.all()) {
            if (cvars.find(spec.name()) != null) {
                throw new IllegalStateException(
                    "'" + spec.name() + "' exists as both a command and a cvar");
            }
        }
        this.commands = commands;
        this.cvars = cvars;
    }

    /**
     * Executes {@code commandText} — the line <i>without</i> its leading slash.
     *
     * @return the outcome, or {@code null} when the name is unknown to both registries
     */
    public CommandResult dispatch(String commandText, CommandContext context) {
        if (commandText == null || commandText.isBlank() || context == null) {
            return null;
        }
        final String name;
        try {
            name = CommandParser.leadingName(commandText);
        } catch (CommandException bad) {
            return CommandResult.error(bad.getMessage());
        }
        if (name.isEmpty()) {
            return null;
        }

        CommandSpec spec = commands.find(name);
        if (spec != null) {
            return dispatchCommand(spec, commandText, context);
        }

        Cvar cvar = cvars.find(name);
        if (cvar != null) {
            return dispatchCvar(cvar, commandText, context);
        }

        return null;
    }

    private CommandResult dispatchCommand(CommandSpec spec, String commandText, CommandContext context) {
        if (!context.permission().atLeast(spec.permission())) {
            return CommandResult.error(commands.unknownLines(spec.name(), context.permission()));
        }
        if (spec.handler() == null) {
            // The client's description-only copy of a server command must never reach execution;
            // the forward decision is made before dispatching.
            return CommandResult.error("/" + spec.name() + " is not executable in this process");
        }
        final Arguments arguments;
        try {
            arguments = CommandParser.parseArguments(spec, commandText);
        } catch (CommandException bad) {
            return CommandResult.error(bad.getMessage(), "Usage: " + spec.usage());
        }
        final CommandResult result;
        try {
            result = spec.handler().execute(context, arguments);
        } catch (RuntimeException handlerBug) {
            return CommandResult.error("/" + spec.name() + " failed: " + handlerBug.getMessage());
        }
        if (result != null) {
            return context.replies().isEmpty() ? result : result.withExtraLines(context.replies());
        }
        return CommandResult.info(context.replies());
    }

    private CommandResult dispatchCvar(Cvar cvar, String commandText, CommandContext context) {
        if (!context.permission().atLeast(cvar.permission())) {
            return CommandResult.error(commands.unknownLines(cvar.name(), context.permission()));
        }
        final List<CommandParser.Token> tokens;
        try {
            tokens = CommandParser.tokenise(commandText);
        } catch (CommandException bad) {
            return CommandResult.error(bad.getMessage());
        }
        if (tokens.size() == 1) {
            return CommandResult.info(cvar.describeLines());
        }
        if (tokens.size() != 2) {
            return CommandResult.error(
                "expected /" + cvar.name() + " [value]",
                "current: " + cvar.value() + " — " + cvar.type().describe());
        }
        try {
            boolean changed = cvar.set(tokens.get(1).text());
            return CommandResult.success(
                cvar.name() + " = " + cvar.value() + (changed ? "" : " (unchanged)"));
        } catch (CommandException bad) {
            return CommandResult.error(
                cvar.name() + ": " + bad.getMessage(), "current: " + cvar.value());
        }
    }
}
