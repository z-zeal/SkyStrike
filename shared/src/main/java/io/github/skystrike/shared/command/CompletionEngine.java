package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab-completion for a partially typed command line (console plan §7.3).
 *
 * <p>Shared rather than client-side, because the rules are the parser's rule family in lenient
 * mode: the line being completed is by definition not parseable yet — an unterminated quote is a
 * token in progress, not an error — and the two tokenisers must stay in lockstep by living next
 * to each other.
 *
 * <p>Permission filtering applies exactly as in the registry: a name the caller may not see
 * completes to nothing, indistinguishable from a typo.
 */
public final class CompletionEngine {

    private CompletionEngine() {
    }

    /**
     * Candidates for the line so far.
     *
     * @param commandText the command body without the leading slash
     * @param registry    command metadata for names and argument types
     * @param cvars       optional cvar registry; contributes name completion when first token
     * @param context     the caller; permission filters the result
     */
    public static List<String> complete(
        String commandText,
        CommandRegistry registry,
        CvarRegistry cvars,
        CommandContext context
    ) {
        if (registry == null || context == null) {
            return List.of();
        }
        String text = commandText == null ? "" : commandText;
        boolean endsWithSpace = !text.isEmpty() && Character.isWhitespace(text.charAt(text.length() - 1));
        List<String> tokens = lenientTokens(text);
        if (text.isBlank()) {
            List<String> names = new ArrayList<>(registry.completeNames("", context.permission()));
            if (cvars != null) {
                names.addAll(cvars.completeNames("", context.permission()));
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }

        String name = tokens.get(0);
        if (tokens.size() == 1 && !endsWithSpace) {
            // Completing the command (or cvar) name itself.
            List<String> names = new ArrayList<>(registry.completeNames(name, context.permission()));
            if (cvars != null) {
                for (String cvarName : cvars.completeNames(name, context.permission())) {
                    if (!names.contains(cvarName)) {
                        names.add(cvarName);
                    }
                }
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }

        CommandSpec spec = registry.find(name);
        if (spec == null) {
            // Names the caller cannot see and names that do not exist both complete silently.
            Cvar cvar = cvars == null ? null : cvars.find(name);
            if (cvar == null || !context.permission().atLeast(cvar.permission())) {
                return List.of();
            }
            return List.of();
        }
        if (!context.permission().atLeast(spec.permission())) {
            return List.of();
        }

        List<CommandArg> args = spec.args();
        if (args.isEmpty()) {
            return List.of();
        }
        int rawIndex = tokens.size() - 1 + (endsWithSpace ? 1 : 0);
        int argIndex = Math.min(Math.max(rawIndex - 1, 0), args.size() - 1);
        CommandArg arg = args.get(argIndex);
        String prefix = endsWithSpace ? "" : tokens.get(tokens.size() - 1);
        return arg.type().complete(prefix, context);
    }

    /**
     * Lenient tokenisation for completion: quote grouping honoured, unmatched quotes treated as
     * "token still open", a trailing backslash kept literal. Never throws.
     */
    static List<String> lenientTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                hasToken = true;
                current.append(text.charAt(++i));
            } else if (c == '"') {
                hasToken = true;
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                hasToken = true;
                current.append(c);
            }
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
