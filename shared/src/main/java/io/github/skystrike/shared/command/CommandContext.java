package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a handler or an argument type may legitimately know about one invocation
 * (console plan §7.3 {@code CommandContext}).
 *
 * <p>Identity and permission arrive <b>resolved by the executing side</b> — a handler never
 * re-checks the spec's permission and never trusts an id from the text; the dispatcher gated
 * before this object was built. {@link #side()} tells portable handlers (e.g. {@code help})
 * which process they are in.
 *
 * <p>{@link #reply(String)} accumulates output for handlers that build their lines
 * incrementally; returning {@code null} from the handler wraps the collected lines in an ok
 * result.
 */
public final class CommandContext {

    /** Caller identity when there is no joined player: a local client console or a terminal. */
    public static final int NO_CALLER = -1;

    private final int callerId;
    private final String callerName;
    private final Permission permission;
    private final CommandSide side;
    private final long nowMillis;
    private final List<String> connectedPlayerNames;
    private final List<String> replies = new ArrayList<>();

    public CommandContext(
        int callerId,
        String callerName,
        Permission permission,
        CommandSide side,
        long nowMillis,
        List<String> connectedPlayerNames
    ) {
        this.callerId = callerId;
        this.callerName = callerName == null ? "" : callerName;
        this.permission = permission == null ? Permission.EVERYONE : permission;
        this.side = side == null ? CommandSide.CLIENT : side;
        this.nowMillis = nowMillis;
        this.connectedPlayerNames =
            connectedPlayerNames == null ? List.of() : List.copyOf(connectedPlayerNames);
    }

    /** A client-side invocation on behalf of the local player. */
    public static CommandContext local(Permission permission) {
        return new CommandContext(NO_CALLER, "", permission, CommandSide.CLIENT, 0L, List.of());
    }

    /** The executor's clock at invocation time; 0 on the client, which has no need of it. */
    public long nowMillis() {
        return nowMillis;
    }

    public int callerId() {
        return callerId;
    }

    public String callerName() {
        return callerName;
    }

    /** The caller's resolved level. Authoritative on the server; the echo of it on the client. */
    public Permission permission() {
        return permission;
    }

    /** Which process this invocation executes in. */
    public CommandSide side() {
        return side;
    }

    /** Live players the executor knows, for {@code PLAYER} completion. Empty where unknown. */
    public List<String> connectedPlayerNames() {
        return connectedPlayerNames;
    }

    /** Adds one output line, later wrapped into the result when the handler returns null. */
    public void reply(String line) {
        replies.add(line == null ? "" : line);
    }

    public List<String> replies() {
        return List.copyOf(replies);
    }
}
