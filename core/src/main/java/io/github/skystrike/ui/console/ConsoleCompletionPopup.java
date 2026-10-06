package io.github.skystrike.ui.console;

import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.shared.command.CommandContext;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CompletionEngine;
import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.DebugFlags;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The suggestion list above the input field (console plan §3.4).
 *
 * <p>Stateless about sources: it recomputes candidates from the current text whenever the
 * dialog asks, so typed input and permission changes are always reflected live — there is no
 * stale cache to disagree with the field. Tab cycles the selection inside it; accepting the
 * selection is the dialog's job because only the dialog rewrites the field's text.
 */
public final class ConsoleCompletionPopup {

    private final ClientCommandService commands;
    private final ClientCapabilities capabilities;
    private final IntSupplier localPlayerId;
    private final Supplier<String> localPlayerName;
    private final Supplier<List<String>> connectedPlayerNames;

    private List<String> candidates = List.of();
    private int selected;

    /** The field text these candidates were computed for; a mismatch means recompute. */
    private String computedFor = "";

    public ConsoleCompletionPopup(
        ClientCommandService commands,
        ClientCapabilities capabilities,
        IntSupplier localPlayerId,
        Supplier<String> localPlayerName,
        Supplier<List<String>> connectedPlayerNames
    ) {
        if (commands == null || capabilities == null || localPlayerId == null
            || localPlayerName == null || connectedPlayerNames == null) {
            throw new IllegalArgumentException("all collaborators are required");
        }
        this.commands = commands;
        this.capabilities = capabilities;
        this.localPlayerId = localPlayerId;
        this.localPlayerName = localPlayerName;
        this.connectedPlayerNames = connectedPlayerNames;
    }

    /** Candidates for {@code typedCommandBody} (no leading slash). Recomputes on text change. */
    public List<String> candidatesFor(String typedCommandBody) {
        String key = typedCommandBody == null ? "" : typedCommandBody;
        if (!key.equals(computedFor)) {
            computedFor = key;
            // The server-granted level is what the client is told; the dev flag merely opens the
            // local console, so with no grant it shows the full local table while the server
            // still re-authorises every forwarded line (playable build plan M1 §2.8).
            Permission level = DebugFlags.enabled() && !capabilities.consoleAccess()
                ? Permission.ADMIN : capabilities.level();
            CommandContext context = new CommandContext(
                Math.max(0, localPlayerId.getAsInt()),
                String.valueOf(localPlayerName.get()),
                level,
                CommandSide.CLIENT,
                System.currentTimeMillis(),
                connectedPlayerNames.get());
            candidates = CompletionEngine.complete(key, commands.registry(), commands.cvars(), context);
            selected = 0;
        }
        return candidates;
    }

    /** Hides the popup when the field leaves command mode. */
    public void clear() {
        candidates = List.of();
        selected = 0;
        computedFor = "";
    }

    /** Cycles the selection (Tab); wraps around. */
    public void cycle(int direction) {
        if (candidates.isEmpty()) {
            return;
        }
        int size = candidates.size();
        selected = ((selected + direction) % size + size) % size;
    }

    public int selected() {
        return selected;
    }

    public List<String> candidates() {
        return candidates;
    }

    /** The single selected candidate, or null. */
    public String selectedCandidate() {
        return selected >= 0 && selected < candidates.size() ? candidates.get(selected) : null;
    }

    /** Permanently visible share: how many rows the dialog draws, from last up to the cap. */
    public static int visibleCount(List<String> candidates, int maxRows) {
        return Math.min(candidates.size(), Math.max(1, maxRows));
    }
}
