package io.github.skystrike.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the completion contract (console plan §7.3): lenient parsing of half-typed lines,
 * permission filtering identical to the registry's, and argument-index resolution with a
 * trailing space.
 */
class CompletionEngineTest {

    private enum Tier {
        LOW, MID, HIGH
    }

    private static CommandRegistry registry() {
        CommandRegistry commands = new CommandRegistry();
        commands.register(CommandSpec.builder("give")
            .permission(Permission.ADMIN)
            .arg("slot", ArgTypes.ENUM(Tier.class), "the quality tier")
            .arg("count", ArgTypes.INT(1, 99), "how many")
            .handler((c, a) -> null)
            .build());
        commands.register(CommandSpec.builder("get")
            .permission(Permission.EVERYONE)
            .handler((c, a) -> null)
            .build());
        return commands;
    }

    private static CvarRegistry cvars() {
        CvarRegistry variables = new CvarRegistry();
        variables.register(Cvar.builder("giftwrap", ArgTypes.BOOL, "off").build());
        return variables;
    }

    @Test
    void firstTokenCompletesNamesCallerMaySee() {
        CommandContext everyone = CommandContext.local(Permission.EVERYONE);
        List<String> candidates = CompletionEngine.complete("g", registry(), cvars(), everyone);
        assertEquals(List.of("get", "giftwrap"), candidates,
            "an EVERYONE caller sees the public command and the cvar, not the ADMIN one");
    }

    @Test
    void firstTokenShowsPrivilegedNamesToPrivilegedCaller() {
        CommandContext admin = CommandContext.local(Permission.ADMIN);
        List<String> candidates = CompletionEngine.complete("gi", registry(), cvars(), admin);
        assertTrue(candidates.contains("give") && candidates.contains("giftwrap"));
    }

    @Test
    void hiddenCommandsCompleteToNothingLikeTypos() {
        CommandContext everyone = CommandContext.local(Permission.EVERYONE);
        assertEquals(List.of(), CompletionEngine.complete("give ", registry(), cvars(), everyone),
            "a name the caller cannot see completes exactly like one that does not exist");
        assertEquals(List.of(), CompletionEngine.complete("zzz ", registry(), cvars(), everyone));
    }

    @Test
    void argumentsAdvanceWithTrailingSpace() {
        CommandContext admin = CommandContext.local(Permission.ADMIN);
        List<String> first = CompletionEngine.complete("give ", registry(), cvars(), admin);
        assertEquals(List.of("low", "mid", "high"), first,
            "a trailing space completes the first argument in enum declaration order");
        List<String> filtered = CompletionEngine.complete("give m", registry(), cvars(), admin);
        assertEquals(List.of("mid"), filtered);
    }

    @Test
    void secondArgumentFollowsTheFirst() {
        CommandContext admin = CommandContext.local(Permission.ADMIN);
        List<String> candidates = CompletionEngine.complete("give mid ", registry(), cvars(), admin);
        assertTrue(candidates.isEmpty(),
            "numeric arguments have no candidate list, but must not repeat the enum");
    }

    @Test
    void halfTypedQuotesCompletionNeverThrows() {
        CommandContext admin = CommandContext.local(Permission.ADMIN);
        // An unmatched quote mid-typing is a token in progress, not a parse error.
        CompletionEngine.complete("give \"unterminated", registry(), cvars(), admin);
        CompletionEngine.complete("give trailing\\", registry(), cvars(), admin);
    }
}
