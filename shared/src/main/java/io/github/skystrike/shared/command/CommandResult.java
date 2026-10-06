package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.List;

/**
 * What executing one line produced: an ok flag, a severity for colouring, and the output lines
 * (playable build plan M1 §2.2).
 *
 * <p>Immutable. The severity ladder is deliberately the wire-relevant subset of the client's
 * message severities — command output crosses the network, so the meaning of a colour must be a
 * {@code shared} concept, not a {@code core} one.
 */
public final class CommandResult {

    /** How the line should be read: neutral output, a confirmed change, a caution, a failure. */
    public enum Severity {
        INFO,
        SUCCESS,
        WARNING,
        ERROR;

        /** Defensive decode for a wire ordinal; unknown values read as {@link #INFO}. */
        public static Severity fromOrdinal(int ordinal) {
            Severity[] values = values();
            return ordinal < 0 || ordinal >= values.length ? INFO : values[ordinal];
        }
    }

    private final boolean ok;
    private final Severity severity;
    private final List<String> lines;

    private CommandResult(boolean ok, Severity severity, List<String> lines) {
        this.ok = ok;
        this.severity = severity == null ? Severity.INFO : severity;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
    }

    /** Neutral informational output. */
    public static CommandResult info(String... lines) {
        return new CommandResult(true, Severity.INFO, List.of(lines));
    }

    /** Info with a pre-built list (results assembled line by line). */
    public static CommandResult info(List<String> lines) {
        return new CommandResult(true, Severity.INFO, lines);
    }

    /** A confirmed state change: "noclip enabled", "teleported to ...". */
    public static CommandResult success(String... lines) {
        return new CommandResult(true, Severity.SUCCESS, List.of(lines));
    }

    /** Worked, but the caller should notice: "clamped to range", "already in that state". */
    public static CommandResult warning(String... lines) {
        return new CommandResult(true, Severity.WARNING, List.of(lines));
    }

    /** Refused or failed. Always {@link Severity#ERROR}, always not-ok. */
    public static CommandResult error(String... lines) {
        return new CommandResult(false, Severity.ERROR, List.of(lines));
    }

    /** Error with a pre-built list. */
    public static CommandResult error(List<String> lines) {
        return new CommandResult(false, Severity.ERROR, lines);
    }

    public boolean ok() {
        return ok;
    }

    public Severity severity() {
        return severity;
    }

    public List<String> lines() {
        return lines;
    }

    /** This result's lines plus more, keeping the original flag and severity. */
    public CommandResult withExtraLines(List<String> extra) {
        List<String> joined = new ArrayList<>(lines);
        joined.addAll(extra);
        return new CommandResult(ok, severity, joined);
    }

    @Override
    public String toString() {
        return "CommandResult[" + severity + ", " + lines.size() + " lines]";
    }
}
