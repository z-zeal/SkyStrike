package io.github.skystrike.shared.net.s2c;

import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.net.Packet;
import java.util.ArrayList;

/**
 * The output of a server-executed command, addressed to the caller only
 * (playable build plan M1 §2.3).
 *
 * <p>Carries lines plus a severity ordinal and an ok flag — the wire stays dumb on purpose:
 * the enum itself is not registered because ordinals decode defensively ({@code fromOrdinal}
 * never throws), while a renumbered registered class id is the silent-divergence bug
 * {@code NetworkRegistrationTest} exists to catch.
 */
public final class PacketCommandResponse implements Packet {

    /** False when the command was refused or failed; the severity refines the reading. */
    public boolean ok;

    /** Ordinal into {@code CommandResult.Severity}; decodes via {@link #severity()}. */
    public int severityOrdinal;

    /** Bounded by {@code TextLimits} before sending. Never null. */
    public ArrayList<String> lines = new ArrayList<>();

    public PacketCommandResponse() {
    }

    public PacketCommandResponse(boolean ok, int severityOrdinal, ArrayList<String> lines) {
        this.ok = ok;
        this.severityOrdinal = severityOrdinal;
        this.lines = lines == null ? new ArrayList<>() : lines;
    }

    /** Builds the packet from an execution result. */
    public static PacketCommandResponse of(CommandResult result) {
        return new PacketCommandResponse(
            result.ok(), result.severity().ordinal(), new ArrayList<>(result.lines()));
    }

    /** The decoded severity; an unknown ordinal reads as INFO rather than failing. */
    public CommandResult.Severity severity() {
        return CommandResult.Severity.fromOrdinal(severityOrdinal);
    }

    @Override
    public String toString() {
        return "PacketCommandResponse[ok=" + ok + ", lines=" + lines.size() + "]";
    }
}
