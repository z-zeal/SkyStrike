package io.github.skystrike.shared.command;

import java.util.List;

/**
 * One argument type: parse, validate, complete, describe.
 *
 * <p>One instance per type, not per argument (console plan §7.3 {@code ArgType/ArgTypes}). Parsing
 * either returns a coerced value or throws {@link CommandException} with a message that names what
 * was expected — it never partially succeeds, because a coerced-wrong argument beats no command
 * exactly once and causes a bug report forever.
 *
 * @param <T> the Java value this type produces
 */
public interface ArgType<T> {

    /**
     * Coerces one token. The token has already been through quote handling by the parser, so a
     * quoted phrase arrives as a single token.
     *
     * @throws CommandException when the token cannot become a value of this type
     */
    T parse(String token) throws CommandException;

    /**
     * Completion candidates for a partially typed token. The context carries whatever live
     * candidates exist (connected player names, server-side); types without a live source return
     * an empty list.
     */
    default List<String> complete(String prefix, CommandContext context) {
        return List.of();
    }

    /**
     * Short human description of legal values, used in {@code help <command>} and the hint line,
     * e.g. {@code "integer between 0 and 500"}.
     */
    String describe();
}
