package io.github.skystrike.shared.command;

/**
 * Which process a command executes in (playable build plan M1 §2.2).
 *
 * <p>A {@link #CLIENT} command never leaves the process that typed it. A {@link #SERVER} command
 * is sent as a raw line and re-parsed and re-authorised by the server — the client's copy of the
 * spec carries no handler, because its only jobs there are help, the hint line, completion and
 * the decision that the line must be forwarded. {@link #BOTH} means a spec executable on either
 * side, such as {@code help}.
 */
public enum CommandSide {
    CLIENT,
    SERVER,
    BOTH
}
