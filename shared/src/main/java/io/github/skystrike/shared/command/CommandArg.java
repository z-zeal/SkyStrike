package io.github.skystrike.shared.command;

/**
 * One declared argument of a command: name, type, whether it may be omitted.
 *
 * <p>The record carries everything usage text, validation and completion need, so adding an
 * argument to a command updates all three at once (console plan §7.3).
 *
 * @param name        argument name as shown in usage, e.g. {@code player}
 * @param type        coercion and completion source
 * @param required    true when the command cannot run without it
 * @param description one short line for {@code help <command>}
 */
public record CommandArg(String name, ArgType<?> type, boolean required, String description) {

    public CommandArg {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("argument name is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("argument type is required for " + name);
        }
        description = description == null ? "" : description;
    }

    public static CommandArg required(String name, ArgType<?> type, String description) {
        return new CommandArg(name, type, true, description);
    }

    public static CommandArg optional(String name, ArgType<?> type, String description) {
        return new CommandArg(name, type, false, description);
    }

    /** The usage rendering: {@code <player>} when required, {@code [player]} when optional. */
    public String usageToken() {
        return required ? "<" + name + ">" : "[" + name + "]";
    }
}
