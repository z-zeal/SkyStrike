package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The full declaration of one command: everything help, usage, completion, validation,
 * permission checks and routing need, in one place (console plan §7.3).
 *
 * <p>Because all of those are <i>derived</i> from this record, they cannot drift from the
 * implementation — the failure mode of hand-maintained help text. Adding a command is: write one
 * spec, register it once, done.
 *
 * <p>A spec whose {@link CommandSide} is {@link CommandSide#SERVER} may legally carry no handler:
 * that is the client's copy, used for help, the hint line, completion and the forward decision.
 * Executing a handler-less spec is a programming error the dispatcher refuses.
 */
public final class CommandSpec {

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final List<CommandArg> args;
    private final Permission permission;
    private final CommandSide side;
    private final CommandHandler handler;
    private final String usage;

    private CommandSpec(Builder builder) {
        this.name = builder.name;
        this.aliases = List.copyOf(builder.aliases);
        this.description = builder.description;
        this.args = List.copyOf(builder.args);
        this.permission = builder.permission;
        this.side = builder.side;
        this.handler = builder.handler;

        boolean seenOptional = false;
        Set<String> argNames = new LinkedHashSet<>();
        for (int i = 0; i < args.size(); i++) {
            CommandArg arg = args.get(i);
            if (!argNames.add(arg.name())) {
                throw new IllegalArgumentException(name + ": duplicate argument '" + arg.name() + "'");
            }
            if (arg.required() && seenOptional) {
                throw new IllegalArgumentException(
                    name + ": required argument '" + arg.name() + "' follows an optional one");
            }
            seenOptional |= !arg.required();
            if (arg.type() == ArgTypes.GREEDY_STRING && i != args.size() - 1) {
                throw new IllegalArgumentException(
                    name + ": a greedy argument must be the last one");
            }
        }

        StringBuilder usage = new StringBuilder().append('/').append(name);
        for (CommandArg arg : args) {
            usage.append(' ').append(arg.usageToken());
        }
        this.usage = usage.toString();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Primary name, lower-case. Lookup is case-insensitive; this is the canonical spelling. */
    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String description() {
        return description;
    }

    public List<CommandArg> args() {
        return args;
    }

    /** The level a caller must reach for this command to exist for them at all. */
    public Permission permission() {
        return permission;
    }

    public CommandSide side() {
        return side;
    }

    /** The behaviour, or {@code null} on a description-only copy (the client's view of a server command). */
    public CommandHandler handler() {
        return handler;
    }

    /**
     * A copy of this spec with {@code newHandler} attached and identical metadata. The server
     * declares its command catalogue once (in {@code shared}, so the client can offer help and
     * completion for the same commands) and attaches behaviour with this — metadata can never
     * drift between the two sides.
     */
    public CommandSpec withHandler(CommandHandler newHandler) {
        Builder copy = builder(name)
            .description(description)
            .permission(permission)
            .side(side)
            .handler(newHandler);
        for (String alias : aliases) {
            copy.alias(alias);
        }
        for (CommandArg arg : args) {
            copy.arg(arg);
        }
        return copy.build();
    }

    /** Generated usage, e.g. {@code /sethealth <hp> [player]} — never hand-written. */
    public String usage() {
        return usage;
    }

    /** {@code help <command>} detail: one line per argument with its type and requirement. */
    public List<String> helpLines() {
        List<String> lines = new ArrayList<>();
        lines.add(usage() + " — " + description);
        for (CommandArg arg : args) {
            lines.add("  " + arg.usageToken() + "  " + arg.type().describe()
                + (arg.description().isEmpty() ? "" : " — " + arg.description()));
        }
        if (!aliases.isEmpty()) {
            lines.add("  aliases: " + String.join(", ", aliases));
        }
        return lines;
    }

    public static final class Builder {

        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private final List<CommandArg> args = new ArrayList<>();
        private String description = "";
        private Permission permission = Permission.EVERYONE;
        private CommandSide side = CommandSide.BOTH;
        private CommandHandler handler;

        private Builder(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("command name is required");
            }
            this.name = name.trim().toLowerCase(Locale.ROOT);
        }

        public Builder alias(String alias) {
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias.trim().toLowerCase(Locale.ROOT));
            }
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder arg(CommandArg arg) {
            this.args.add(arg);
            return this;
        }

        public Builder arg(String name, ArgType<?> type, String description) {
            return arg(CommandArg.required(name, type, description));
        }

        public Builder optionalArg(String name, ArgType<?> type, String description) {
            return arg(CommandArg.optional(name, type, description));
        }

        public Builder permission(Permission permission) {
            this.permission = permission == null ? Permission.EVERYONE : permission;
            return this;
        }

        public Builder side(CommandSide side) {
            this.side = side == null ? CommandSide.BOTH : side;
            return this;
        }

        public Builder handler(CommandHandler handler) {
            this.handler = handler;
            return this;
        }

        public CommandSpec build() {
            return new CommandSpec(this);
        }
    }
}
