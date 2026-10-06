package io.github.skystrike.command;

import io.github.skystrike.chat.ChatClient;
import io.github.skystrike.shared.command.CommandContext;
import io.github.skystrike.shared.command.CommandDispatcher;
import io.github.skystrike.shared.command.CommandException;
import io.github.skystrike.shared.command.CommandParser;
import io.github.skystrike.shared.command.CommandRegistry;
import io.github.skystrike.shared.command.CommandResult;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CommandSpec;
import io.github.skystrike.shared.command.CvarRegistry;
import io.github.skystrike.shared.config.DebugFlags;
import io.github.skystrike.shared.net.s2c.PacketCommandResponse;
import io.github.skystrike.shared.text.ChatChannel;
import io.github.skystrike.ui.text.MessageSeverity;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The client half of the command core (playable build plan M1 §2.6).
 *
 * <p>Owns the two questions a console line can ask — <i>is this a command?</i> and <i>does it
 * run here or on the server?</i> — answers the first from the capability push plus the debug
 * flag, and answers the second entirely from local metadata: a command runs locally when its
 * spec is executable on the {@link CommandSide#CLIENT} side; anything else (server-side specs,
 * names this client does not know) is forwarded as a raw line and re-parsed, re-authorised and
 * re-executed by the server. There is no third mode.
 *
 * <p>Output lands in the scrollback like any other line, on the two console channels: the line
 * as typed on {@link ChatChannel#COMMAND_ECHO}, result and response lines on
 * {@link ChatChannel#CONSOLE}. Both are invisible to a client without access, and such a client
 * never reaches this service — {@code /leak} is chat text for them, sent by the dialog.
 */
public final class ClientCommandService {

    private final ClientCapabilities capabilities;
    private final ChatClient chat;
    private final CommandRegistry registry = new CommandRegistry();
    private final CvarRegistry cvars = new CvarRegistry();
    private final CommandDispatcher dispatcher;

    private final IntSupplier localPlayerId;
    private final Supplier<String> localPlayerName;
    private final Supplier<List<String>> connectedPlayerNames;

    /** Where forwarded lines go. {@code ClientSession::sendCommand} on the live path. */
    private Consumer<String> serverSender = line -> {
    };

    public ClientCommandService(
        ClientCapabilities capabilities,
        ChatClient chat,
        IntSupplier localPlayerId,
        Supplier<String> localPlayerName,
        Supplier<List<String>> connectedPlayerNames
    ) {
        if (capabilities == null || chat == null || localPlayerId == null
            || localPlayerName == null || connectedPlayerNames == null) {
            throw new IllegalArgumentException("all collaborators are required");
        }
        this.capabilities = capabilities;
        this.chat = chat;
        this.localPlayerId = localPlayerId;
        this.localPlayerName = localPlayerName;
        this.connectedPlayerNames = connectedPlayerNames;
        this.dispatcher = new CommandDispatcher(registry, cvars);
    }

    /** The {@code ClientCommandModule} starter set, wired to the game's state. */
    public void registerDefaults(ClientCommandModule.Deps deps) {
        ClientCommandModule.registerAll(registry, cvars, deps);
    }

    public void setServerSender(Consumer<String> serverSender) {
        if (serverSender != null) {
            this.serverSender = serverSender;
        }
    }

    /** Whether the console exists for this client at all (debug flag or server grant). */
    public boolean unlocked() {
        return DebugFlags.enabled() || capabilities.consoleAccess();
    }

    /**
     * The whole "is this line a command?" rule, recomputed on every keystroke. With no console
     * this is false and a leading slash is ordinary chat text; with the console, exactly one
     * leading slash makes a command and a doubled one is chat reading {@code /something}.
     */
    public boolean isCommandLine(String text) {
        return unlocked() && capabilities.isCommandLine(text);
    }

    /**
     * Executes one line already known to satisfy {@link #isCommandLine}.
     *
     * <p>Client-executable commands run in-process and their result lands in the scrollback
     * immediately; everything else is shipped to the server as the raw line (never a capability
     * claim) and its {@link PacketCommandResponse} arrives asynchronously.
     *
     * @return the local result when the line ran in-process, otherwise {@code null}
     */
    public CommandResult submit(String typed) {
        if (!isCommandLine(typed)) {
            return CommandResult.error("The console is not available.");
        }
        String trimmed = typed.trim();
        String line = trimmed.substring(ClientCapabilities.COMMAND_PREFIX.length()).trim();
        long now = System.currentTimeMillis();

        chat.addSystemLine(now, ChatChannel.COMMAND_ECHO, trimmed, MessageSeverity.INFO);
        if (line.isEmpty()) {
            CommandResult empty = CommandResult.error("No command given. Type /help for what is available.");
            fileLines(now, empty.severity(), empty.lines());
            return empty;
        }

        CommandContext context = new CommandContext(
            Math.max(0, localPlayerId.getAsInt()),
            String.valueOf(localPlayerName.get()),
            capabilities.level(),
            CommandSide.CLIENT,
            now,
            connectedPlayerNames.get());

        CommandResult result = null;
        boolean forwarding;
        try {
            String name = CommandParser.leadingName(line);
            CommandSpec spec = registry.find(name);
            if (spec != null && spec.side() != CommandSide.SERVER) {
                result = dispatcher.dispatch(line, context);
                forwarding = false;
            } else if (spec == null && cvars.find(name) != null) {
                result = dispatcher.dispatch(line, context);
                forwarding = false;
            } else {
                // Server-side spec, or a name this client does not know — either way authority
                // is over there. Send the line exactly as given; never a capability claim.
                forwarding = true;
            }
        } catch (CommandException parseError) {
            result = CommandResult.error(parseError.getMessage());
            forwarding = false;
        }

        if (forwarding) {
            serverSender.accept(line);
            return null;
        }
        if (result != null) {
            fileLines(now, result.severity(), result.lines());
        }
        return result;
    }

    /**
     * Files a response the server sent back. The console is never this client's sole view of
     * authoritative state, so a response that arrives after a capability revocation still lands —
     * the buffer's own visibility rules decide what the player sees.
     */
    public void handleResponse(PacketCommandResponse response) {
        if (response == null || response.lines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        fileLines(now, response.severity(), response.lines);
    }

    private void fileLines(long now, CommandResult.Severity severity, List<String> lines) {
        MessageSeverity messageSeverity = switch (severity) {
            case SUCCESS -> MessageSeverity.SUCCESS;
            case WARNING -> MessageSeverity.WARNING;
            case ERROR -> MessageSeverity.ERROR;
            default -> MessageSeverity.INFO;
        };
        int filed = 0;
        for (String line : lines) {
            chat.addSystemLine(now, ChatChannel.CONSOLE, line, messageSeverity);
            if (++filed >= 50) {
                break;
            }
        }
    }

    /** Names for the completion popup, filtered to what this level may see. */
    public List<String> completeCommandNames(String prefix) {
        List<String> names = registry.completeNames(prefix, capabilities.level());
        for (String name : cvars.completeNames(prefix, capabilities.level())) {
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    public CommandRegistry registry() {
        return registry;
    }

    public CvarRegistry cvars() {
        return cvars;
    }
}
