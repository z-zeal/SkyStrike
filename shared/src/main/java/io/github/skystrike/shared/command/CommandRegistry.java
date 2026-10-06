package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Name and alias lookup over every registered command (console plan §7.3).
 *
 * <p>Populated by explicit registration only — no scanning, no reflection — so the command set
 * is visible to static analysis and identical under native-image builds.
 *
 * <p>Two permission behaviours matter and are both pinned by tests: <b>listing and completion
 * are filtered</b> (a caller never sees a command they cannot run), and executing a command you
 * lack the level for produces the <i>same</i> refusal as a typo, so probing cannot confirm which
 * privileged commands exist.
 */
public final class CommandRegistry {

    private final Map<String, CommandSpec> byName = new HashMap<>();
    private final List<CommandSpec> specs = new ArrayList<>();

    /** Registers one spec. A duplicated name or alias anywhere in the registry is a bug. */
    public void register(CommandSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        claim(spec.name(), spec);
        for (String alias : spec.aliases()) {
            claim(alias, spec);
        }
        specs.add(spec);
    }

    private void claim(String key, CommandSpec spec) {
        String normalised = key.toLowerCase(Locale.ROOT);
        CommandSpec previous = byName.putIfAbsent(normalised, spec);
        if (previous != null && previous != spec) {
            throw new IllegalStateException(
                "'" + normalised + "' is already registered as " + previous.name());
        }
    }

    /** The spec for a name or alias, case-insensitive, or {@code null}. Unfiltered by level. */
    public CommandSpec find(String nameOrAlias) {
        if (nameOrAlias == null) {
            return null;
        }
        return byName.get(nameOrAlias.trim().toLowerCase(Locale.ROOT));
    }

    /** Every registered spec, sorted by primary name. Unfiltered by level. */
    public List<CommandSpec> all() {
        List<CommandSpec> sorted = new ArrayList<>(specs);
        sorted.sort(Comparator.comparing(CommandSpec::name));
        return sorted;
    }

    /** What {@code help} and completion may show {@code permission}: the runnable set, sorted. */
    public List<CommandSpec> visibleTo(Permission permission) {
        Permission level = permission == null ? Permission.EVERYONE : permission;
        List<CommandSpec> visible = new ArrayList<>();
        for (CommandSpec spec : specs) {
            if (level.atLeast(spec.permission())) {
                visible.add(spec);
            }
        }
        visible.sort(Comparator.comparing(CommandSpec::name));
        return visible;
    }

    /**
     * Names (and aliases) available to {@code permission} that start with {@code prefix} — the
     * name-position completion source for the input bar.
     */
    public List<String> completeNames(String prefix, Permission permission) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (CommandSpec spec : visibleTo(permission)) {
            if (spec.name().startsWith(lower)) {
                out.add(spec.name());
            }
            for (String alias : spec.aliases()) {
                if (alias.startsWith(lower)) {
                    out.add(alias);
                }
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    /**
     * The nearest runnable name within a sensible edit distance, or {@code null}. Distantly
     * typed short words stay silent on purpose — guessing at a two-letter typo is noise, not
     * help. This is the one feature that eliminates most "the console is broken" reports.
     */
    public String suggestionFor(String attempted, Permission permission) {
        String lower = attempted == null ? "" : attempted.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return null;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CommandSpec spec : visibleTo(permission)) {
            int nameDistance = editDistance(lower, spec.name());
            if (nameDistance < bestDistance) {
                bestDistance = nameDistance;
                best = spec.name();
            }
            for (String alias : spec.aliases()) {
                int aliasDistance = editDistance(lower, alias);
                if (aliasDistance < bestDistance) {
                    bestDistance = aliasDistance;
                    best = alias;
                }
            }
        }
        return best != null && bestDistance <= Math.max(1, best.length() / 3) ? best : null;
    }

    /**
     * The refusal for a name that resolves to nothing runnable. Deliberately identical whether
     * the command truly does not exist or merely exists above your level (console plan §7.3:
     * "you need admin" would merely confirm the command exists).
     */
    public List<String> unknownLines(String attempted, Permission permission) {
        String suggestion = suggestionFor(attempted, permission);
        // When something visible is near, keep the typed echo: "Unknown command 'kikc'. Did you
        // mean /kick?" reads naturally. When nothing visible is near the echo canonicalises
        // through the full pool instead, which renders a close typo of a hidden command and the
        // hidden command itself as the same string — the refusal cannot confirm existence.
        String echo = suggestion != null ? attempted : canonicalEcho(attempted);
        List<String> lines = new ArrayList<>();
        lines.add("Unknown command '" + echo + "'. Type /help for the commands available to you.");
        if (suggestion != null) {
            lines.add("Did you mean /" + suggestion + "?");
        }
        return lines;
    }

    /**
     * What the refusal echoes when nothing visible suggests itself. The echo resolves through
     * the full (unfiltered) pool: a close typo of a hidden command prints the same canonical
     * name as the command itself, so both inputs yield byte-identical refusals and the caller
     * cannot tell "above your level" from "merely unknown" by comparing echoes. When nothing at
     * all is near, the typed name comes back verbatim. Used only when {@link #suggestionFor}
     * found no visible near-match, so the hint never competes with this echo.
     */
    private String canonicalEcho(String attempted) {
        String lower = attempted == null ? "" : attempted.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return attempted == null ? "" : attempted;
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CommandSpec spec : specs) {
            int nameDistance = editDistance(lower, spec.name());
            if (nameDistance < bestDistance) {
                bestDistance = nameDistance;
                best = spec.name();
            }
            for (String alias : spec.aliases()) {
                int aliasDistance = editDistance(lower, alias);
                if (aliasDistance < bestDistance) {
                    bestDistance = aliasDistance;
                    best = alias;
                }
            }
        }
        if (best != null && bestDistance <= Math.max(1, best.length() / 3)) {
            return best;
        }
        return attempted;
    }

    /**
     * Optimal string alignment distance (Damerau–Levenshtein with adjacent transpositions):
     * the single most common console typo is a swapped pair — {@code kikc} — and that must
     * count as one mistake, not two. Command names are short, so the table is a few dozen cells.
     */
    static int editDistance(String a, String b) {
        int[] previous2 = null;
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                int best = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
                if (previous2 != null
                    && j > 1
                    && a.charAt(i - 1) == b.charAt(j - 2)
                    && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, previous2[j - 2] + 1);
                }
                current[j] = best;
            }
            previous2 = previous;
            previous = current;
        }
        return previous[b.length()];
    }
}
