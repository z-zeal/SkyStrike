package io.github.skystrike.shared.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Quote-aware tokeniser plus spec-driven coercion (console plan §7.3).
 *
 * <p>Tokenising rules, all of which {@code CommandParserTest} pins: whitespace separates tokens;
 * double quotes group a phrase into one token and are removed; a backslash escapes the next
 * character anywhere; an unterminated quote is an error, not a guess. A {@code GREEDY_STRING}
 * argument consumes the rest of the raw line with its spacing intact, which is why the tokeniser
 * tracks each token's position in the source.
 */
public final class CommandParser {

    /** One token plus where it came from, so a trailing greedy argument can take the rest verbatim. */
    record Token(String text, int startIndex) {
    }

    private CommandParser() {
    }

    /** Tokenises {@code line}, honouring quotes and escapes. */
    static List<Token> tokenise(String line) throws CommandException {
        List<Token> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        int tokenStart = -1;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                if (i + 1 < line.length()) {
                    if (!hasToken) {
                        tokenStart = i;
                        hasToken = true;
                    }
                    current.append(line.charAt(++i));
                } else {
                    if (!hasToken) {
                        tokenStart = i;
                        hasToken = true;
                    }
                    current.append(c);
                }
                continue;
            }
            if (c == '"') {
                if (!hasToken) {
                    tokenStart = i;
                    hasToken = true;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    tokens.add(new Token(current.toString(), tokenStart));
                    current.setLength(0);
                    hasToken = false;
                    tokenStart = -1;
                }
                continue;
            }
            if (!hasToken) {
                tokenStart = i;
                hasToken = true;
            }
            current.append(c);
        }
        if (inQuotes) {
            throw new CommandException("unterminated quote in input");
        }
        if (hasToken) {
            tokens.add(new Token(current.toString(), tokenStart));
        }
        return tokens;
    }

    /** The leading token, lower-cased, or {@code ""} when the line has no tokens. */
    public static String leadingName(String line) throws CommandException {
        List<Token> tokens = tokenise(line);
        return tokens.isEmpty() ? "" : tokens.get(0).text().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Coerces the tokens after the command name into typed {@link Arguments} per {@code spec}.
     *
     * @throws CommandException naming the failing argument and what it expected
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Arguments parseArguments(CommandSpec spec, String line) throws CommandException {
        List<Token> tokens = tokenise(line);
        // tokens.get(0) is the command name itself; arguments start after it.
        Arguments out = new Arguments();
        List<CommandArg> declared = spec.args();
        int cursor = 1;

        for (int argIndex = 0; argIndex < declared.size(); argIndex++) {
            CommandArg arg = declared.get(argIndex);
            if (arg.type() == ArgTypes.GREEDY_STRING) {
                if (cursor < tokens.size()) {
                    String rest = line.substring(tokens.get(cursor).startIndex()).trim();
                    out.put(arg.name(), ArgTypes.GREEDY_STRING.parse(rest));
                }
                continue;
            }
            if (cursor >= tokens.size()) {
                break; // remaining args are absent; required-ness is checked below
            }
            String tokenText = tokens.get(cursor).text();
            try {
                out.put(arg.name(), ((ArgType) arg.type()).parse(tokenText));
            } catch (CommandException bad) {
                throw new CommandException("argument <" + arg.name() + ">: " + bad.getMessage());
            }
            cursor++;
        }

        if (cursor < tokens.size()) {
            throw new CommandException(
                "unexpected extra input starting at '" + tokens.get(cursor).text() + "'");
        }
        for (CommandArg arg : declared) {
            if (arg.required() && !out.contains(arg.name())) {
                throw new CommandException("missing required argument <" + arg.name() + ">");
            }
        }
        return out;
    }
}
