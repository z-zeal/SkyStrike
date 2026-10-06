package io.github.skystrike.shared.command;

/**
 * The behaviour part of a command spec.
 *
 * <p>Handlers either return a result or emit lines through {@link CommandContext#reply(String)}
 * and return {@code null} — the dispatcher wraps any collected lines into an ok result. Whatever
 * it does, a handler never talks to a socket or a screen directly; its output goes through the
 * result so the caller decides where text lands.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Runs the command. Arguments are already coerced and validated; {@code context} carries the
     * caller's identity and resolved permission, already authorised against the spec.
     *
     * @return the outcome, or {@code null} to mean "whatever I replied into the context"
     */
    CommandResult execute(CommandContext context, Arguments arguments);
}
