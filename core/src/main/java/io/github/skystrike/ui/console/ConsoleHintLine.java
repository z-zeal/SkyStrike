package io.github.skystrike.ui.console;

import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.shared.command.CommandException;
import io.github.skystrike.shared.command.CommandParser;
import io.github.skystrike.shared.command.CommandSpec;
import io.github.skystrike.shared.command.Cvar;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.DebugFlags;

/**
 * The live one-liner above the input strip that restates whatever the player is typing
 * (console plan §3.5): plain chat gets no hint; a command line shows the matched spec's usage
 * and description, refreshed per keystroke because the rule is recomputed, not cached.
 *
 * <p>"Here is what this command wants" beats "here is what went wrong": by the time a range or
 * enum error can appear, the hint has shown the shape of a correct line for as long as the line
 * has been in the bar.
 */
public final class ConsoleHintLine {

    private final ClientCommandService commands;
    private final ClientCapabilities capabilities;

    /** The text the current hint was computed for, so unchanged input repays nothing. */
    private String computedFor = "\u0000";
    private String hint = "";

    public ConsoleHintLine(ClientCommandService commands, ClientCapabilities capabilities) {
        if (commands == null || capabilities == null) {
            throw new IllegalArgumentException("commands and capabilities are required");
        }
        this.commands = commands;
        this.capabilities = capabilities;
    }

    /** The hint for the field's current full text (including any leading slash). */
    public String hintFor(String fieldText) {
        String key = fieldText == null ? "" : fieldText;
        if (!key.equals(computedFor)) {
            computedFor = key;
            hint = compute(key);
        }
        return hint;
    }

    private String compute(String text) {
        if (!commands.isCommandLine(text)) {
            return "";
        }
        String body = text.substring(ClientCapabilities.COMMAND_PREFIX.length());
        String name;
        try {
            name = CommandParser.leadingName(body);
        } catch (CommandException unparseable) {
            // A half-tokenised quote is still being written; stay silent rather than nag.
            return "";
        }
        if (name.isEmpty()) {
            return "";
        }
        Permission level = DebugFlags.enabled() && !capabilities.consoleAccess()
            ? Permission.ADMIN : capabilities.level();
        CommandSpec spec = commands.registry().find(name);
        if (spec != null && level.atLeast(spec.permission())) {
            StringBuilder line = new StringBuilder(spec.usage());
            if (!spec.description().isEmpty()) {
                line.append(" — ").append(spec.description());
            }
            return line.toString();
        }
        Cvar cvar = commands.cvars().find(name);
        if (cvar != null && level.atLeast(cvar.permission())) {
            return "/" + cvar.name() + " [" + cvar.value() + "] — " + cvar.description();
        }
        return "";
    }
}
