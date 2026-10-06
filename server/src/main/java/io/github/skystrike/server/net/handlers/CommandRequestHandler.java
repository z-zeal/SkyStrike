package io.github.skystrike.server.net.handlers;

import com.esotericsoftware.kryonet.Connection;
import io.github.skystrike.server.command.ServerCommandModule;
import io.github.skystrike.server.command.ServerCommandService;
import io.github.skystrike.server.net.PacketHandler;
import io.github.skystrike.server.player.PlayerRegistry;
import io.github.skystrike.server.player.PlayerSession;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.net.c2s.PacketCommandRequest;
import io.github.skystrike.shared.net.s2c.PacketCommandResponse;

/**
 * Applies {@link PacketCommandRequest}: resolve who actually sent it, execute the raw line
 * through the authoritative command service, and answer that one caller.
 *
 * <p>The packet carries no identity of its own for the same reason the chat request doesn't —
 * a forged line is only ever attributed to the connection it arrived on. The response goes to
 * the caller alone; command output is nobody else's business, and on the client it lands in the
 * shared scrollback on the console channel, where a client without access never renders it.
 */
public final class CommandRequestHandler implements PacketHandler<PacketCommandRequest> {

    private final PlayerRegistry players;
    private final ServerCommandService commands;
    private final ServerCommandModule.Dispatch dispatch;

    public CommandRequestHandler(
        PlayerRegistry players,
        ServerCommandService commands,
        ServerCommandModule.Dispatch dispatch
    ) {
        if (players == null || commands == null || dispatch == null) {
            throw new IllegalArgumentException("players, commands and dispatch are required");
        }
        this.players = players;
        this.commands = commands;
        this.dispatch = dispatch;
    }

    @Override
    public void handle(Connection connection, PacketCommandRequest packet) {
        if (packet == null) {
            return;
        }
        PlayerSession session = players.byConnection(connection);
        if (session == null) {
            return;
        }

        CommandResult result = commands.execute(
            session.playerId(), session.name(), packet.line, System.currentTimeMillis());
        dispatch.send(session.playerId(), PacketCommandResponse.of(result));

        System.out.printf(
            "[cmd] player=%d status=%s lines=%d line=%.80s%n",
            session.playerId(), result.ok() ? "ok" : "refused", result.lines().size(), packet.line);
    }
}
