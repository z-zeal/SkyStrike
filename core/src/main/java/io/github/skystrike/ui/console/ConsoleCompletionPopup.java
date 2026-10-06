package io.github.skystrike.ui.console;

import io.github.skystrike.command.ClientCapabilities;
import io.github.skystrike.command.ClientCommandService;
import io.github.skystrike.shared.command.CommandContext;
import io.github.skystrike.shared.command.CommandSide;
import io.github.skystrike.shared.command.CompletionEngine;
import io.github.skystrike.shared.command.Permission;
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
        this.localPlayerId = localPlayerId;
        this.localPlayerName = localPlayerName;
        this.connectedPlayerNames = connectedPlayerNames;
    }

    /** Candidates for {@code typedCommandBody} (no leading slash). Recomputes on text change. */
    public List<String> candidatesFor(String typedCommandBody) {
        String text = typedCommandBody == null ? "" : typedCommandBody;
        Permission level = commands.localPermission();
        // Include permission in the cache key: a capability push can promote or revoke a player
        // while the field text stays unchanged, and the popup must update immediately.
        String key = text + '\u0000' + level.name();
        if (!key.equals(computedFor)) {
            computedFor = key;
            CommandContext context = new CommandContext(
                Math.max(0, localPlayerId.getAsInt()),
                String.valueOf(localPlayerName.get()),
                level,
                CommandSide.CLIENT,
                System.currentTimeMillis(),
                connectedPlayerNames.get());
            candidates = CompletionEngine.complete(text, commands.registry(), commands.cvars(), context);
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

    /** Selects one visible row, used by pointer/touch input. */
    public void select(int index) {
        if (index >= 0 && index < candidates.size()) {
            selected = index;
        }
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
