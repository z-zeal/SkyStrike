package io.github.skystrike.shared.command;

/**
 * A parse or validation failure with a player-readable message.
 *
 * <p>The message is written to be shown verbatim in the console: it names the offending argument
 * and what the argument expected. The dispatcher appends the spec's usage line itself, so throw
 * sites never do.
 */
public final class CommandException extends Exception {

    public CommandException(String message) {
        super(message);
    }
}
