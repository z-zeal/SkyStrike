package io.github.skystrike.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.model.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the tokenising and coercion rules the console relies on: quotes, escapes, ranges, enums
 * and the greedy rest-of-line argument.
 */
class CommandParserTest {

    private static CommandSpec spec(CommandArg... args) {
        CommandSpec.Builder builder = CommandSpec.builder("test").description("test command");
        for (CommandArg arg : args) {
            builder.arg(arg);
        }
        return builder.handler((ctx, a) -> CommandResult.info()).build();
    }

    @Test
    @DisplayName("plain tokens split on runs of whitespace")
    void splitsWhitespaceRuns() throws CommandException {
        CommandSpec spec = spec(
            CommandArg.required("a", ArgTypes.STRING, ""),
            CommandArg.required("b", ArgTypes.STRING, ""));
        Arguments parsed = CommandParser.parseArguments(spec, "test   hello \t world  ");
        assertEquals("hello", parsed.getString("a"));
        assertEquals("world", parsed.getString("b"));
    }

    @Test
    @DisplayName("double quotes group a phrase and are removed from the token")
    void quotesGroupPhrases() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("who", ArgTypes.STRING, ""));
        Arguments parsed = CommandParser.parseArguments(spec, "test \"Nova Prime\"");
        assertEquals("Nova Prime", parsed.getString("who"));
    }

    @Test
    @DisplayName("backslash escapes the next character, inside and outside quotes")
    void escapesNextCharacter() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("a", ArgTypes.STRING, ""));
        assertEquals("a\"b", CommandParser.parseArguments(spec, "test a\\\"b").getString("a"));
        assertEquals("a b", CommandParser.parseArguments(spec, "test a\\ b").getString("a"));
    }

    @Test
    @DisplayName("an unterminated quote is an error, not a guess")
    void unterminatedQuoteFails() {
        CommandSpec spec = spec(CommandArg.required("a", ArgTypes.STRING, ""));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test \"oops"));
    }

    @Test
    @DisplayName("a missing required argument fails by name")
    void missingRequiredArgFails() {
        CommandSpec spec = spec(CommandArg.required("hp", ArgTypes.INT(1, 150), ""));
        CommandException thrown = assertThrows(
            CommandException.class, () -> CommandParser.parseArguments(spec, "test"));
        assertTrue(thrown.getMessage().contains("<hp>"), thrown.getMessage());
    }

    @Test
    @DisplayName("unexpected trailing input fails at the extra token")
    void extraInputFails() {
        CommandSpec spec = spec(CommandArg.required("a", ArgTypes.STRING, ""));
        CommandException thrown = assertThrows(
            CommandException.class, () -> CommandParser.parseArguments(spec, "test one two"));
        assertTrue(thrown.getMessage().contains("two"), thrown.getMessage());
    }

    @Test
    @DisplayName("integer ranges reject both format and bounds violations")
    void rangedIntValidates() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("hp", ArgTypes.INT(1, 150), ""));
        assertEquals(100, CommandParser.parseArguments(spec, "test 100").getInt("hp"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test 0"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test 151"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test abc"));
    }

    @Test
    @DisplayName("float ranges reject infinities and out-of-range values")
    void rangedFloatValidates() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("scale", ArgTypes.FLOAT(0.1f, 4f), ""));
        assertEquals(2.5f, CommandParser.parseArguments(spec, "test 2.5").getFloat("scale"), 1e-6f);
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test 0.05"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test NaN"));
    }

    @Test
    @DisplayName("every documented boolean spelling parses to the same two values")
    void boolSpellings() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("flag", ArgTypes.BOOL, ""));
        for (String yes : new String[] {"true", "on", "1", "yes", "TRUE", "On"}) {
            assertTrue(CommandParser.parseArguments(spec, "test " + yes).getBool("flag", false), yes);
        }
        for (String no : new String[] {"false", "off", "0", "no", "FALSE", "Off"}) {
            assertFalse(CommandParser.parseArguments(spec, "test " + no).getBool("flag", true), no);
        }
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test maybe"));
    }

    @Test
    @DisplayName("enums coerce case-insensitively and reject unknown constants")
    void enumCoercion() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("team", ArgTypes.ENUM(Team.class), ""));
        assertEquals(Team.TEAM_B, (Team) CommandParser.parseArguments(spec, "test team_b").get("team"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test team_c"));
    }

    @Test
    @DisplayName("the greedy argument takes the rest of the line with spacing intact")
    void greedyStringKeepsSpacing() throws CommandException {
        CommandSpec spec = spec(
            CommandArg.required("count", ArgTypes.INT(1, 10), ""),
            CommandArg.optional("rest", ArgTypes.GREEDY_STRING, ""));
        Arguments parsed = CommandParser.parseArguments(spec, "test 3   rotate  B   now ");
        assertEquals(3, parsed.getInt("count"));
        assertEquals("rotate  B   now", parsed.getString("rest"));
    }

    @Test
    @DisplayName("optional arguments absent from the line stay absent from the result")
    void optionalAbsenceIsDetectable() throws CommandException {
        CommandSpec spec = spec(
            CommandArg.required("a", ArgTypes.STRING, ""),
            CommandArg.optional("b", ArgTypes.STRING, ""));
        Arguments parsed = CommandParser.parseArguments(spec, "test x");
        assertTrue(parsed.contains("a"));
        assertFalse(parsed.contains("b"));
    }

    @Test
    @DisplayName("a spec may not declare a greedy argument anywhere but last")
    void greedyMustBeLast() {
        CommandSpec.Builder builder = CommandSpec.builder("broken");
        builder.arg(CommandArg.required("a", ArgTypes.GREEDY_STRING, ""));
        builder.arg(CommandArg.required("b", ArgTypes.STRING, ""));
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("durations parse suffixes into seconds")
    void durationUnits() throws CommandException {
        CommandSpec spec = spec(CommandArg.required("span", ArgTypes.DURATION, ""));
        assertEquals(30f, CommandParser.parseArguments(spec, "test 30s").getFloat("span"), 1e-6f);
        assertEquals(300f, CommandParser.parseArguments(spec, "test 5m").getFloat("span"), 1e-6f);
        assertEquals(3600f, CommandParser.parseArguments(spec, "test 1h").getFloat("span"), 1e-6f);
        assertEquals(45f, CommandParser.parseArguments(spec, "test 45").getFloat("span"), 1e-6f);
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test -5s"));
        assertThrows(CommandException.class, () -> CommandParser.parseArguments(spec, "test later"));
    }
}
