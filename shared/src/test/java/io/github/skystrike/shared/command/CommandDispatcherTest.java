package io.github.skystrike.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shared execution path: command lookup before cvar lookup, permission-as-typo refusals,
 * usage on parse errors, and {@code null} for names known to neither registry (the caller's cue
 * to forward or refuse).
 */
class CommandDispatcherTest {

    private static final class Fixture {
        final CommandRegistry commands = new CommandRegistry();
        final CvarRegistry cvars = new CvarRegistry();
        final StringBuilder fired = new StringBuilder();

        Fixture() {
            commands.register(CommandSpec.builder("echo")
                .description("echoes back its argument")
                .arg("word", ArgTypes.STRING, "the word to echo")
                .handler((ctx, args) -> CommandResult.info("echo: " + args.getString("word")))
                .build());
            commands.register(CommandSpec.builder("kick")
                .description("kicks a player")
                .permission(Permission.MODERATOR)
                .arg("who", ArgTypes.STRING, "target")
                .handler((ctx, args) -> CommandResult.success("kicked"))
                .build());
            cvars.register(Cvar.builder("r_shadows", ArgTypes.BOOL, "true")
                .description("soft shadows").build());
        }

        CommandDispatcher dispatcher() {
            return new CommandDispatcher(commands, cvars);
        }
    }

    @Test
    @DisplayName("a known command runs with coerced arguments")
    void knownCommandRuns() {
        Fixture fixture = new Fixture();
        CommandResult result = fixture.dispatcher()
            .dispatch("echo hello", CommandContext.local(Permission.EVERYONE));
        assertTrue(result.ok());
        assertEquals(List.of("echo: hello"), result.lines());
    }

    @Test
    @DisplayName("a parse failure carries the message and the generated usage line")
    void parseFailureShowsUsage() {
        Fixture fixture = new Fixture();
        CommandResult result = fixture.dispatcher()
            .dispatch("echo", CommandContext.local(Permission.EVERYONE));
        assertFalse(result.ok());
        assertEquals("missing required argument <word>", result.lines().get(0));
        assertEquals("Usage: /echo <word>", result.lines().get(1));
    }

    @Test
    @DisplayName("an under-levelled caller gets the typo response, not a permission explanation")
    void underPermissionLooksLikeATypo() {
        Fixture fixture = new Fixture();
        CommandResult result = fixture.dispatcher()
            .dispatch("kick Nova", CommandContext.local(Permission.PLAYER));
        assertFalse(result.ok());
        assertTrue(result.lines().get(0).startsWith("Unknown command 'kick'"),
            result.lines().get(0));
    }

    @Test
    @DisplayName("a cvar with no value describes itself; with a value it sets and confirms")
    void cvarGetAndSet() {
        Fixture fixture = new Fixture();
        CommandDispatcher dispatcher = fixture.dispatcher();

        CommandResult described = dispatcher.dispatch("r_shadows", CommandContext.local(Permission.EVERYONE));
        assertTrue(described.ok());
        assertTrue(described.lines().get(0).contains("r_shadows = true"), described.lines().get(0));

        CommandResult set = dispatcher.dispatch("r_shadows off", CommandContext.local(Permission.EVERYONE));
        assertTrue(set.ok());
        assertEquals("r_shadows = false", set.lines().get(0));

        CommandResult bad = dispatcher.dispatch("r_shadows maybe", CommandContext.local(Permission.EVERYONE));
        assertFalse(bad.ok());
    }

    @Test
    @DisplayName("an unknown name yields null so the caller decides between forward and refusal")
    void unknownNameIsNull() {
        Fixture fixture = new Fixture();
        assertNull(fixture.dispatcher()
            .dispatch("frobnicate", CommandContext.local(Permission.EVERYONE)));
        assertNull(fixture.dispatcher().dispatch("", CommandContext.local(Permission.EVERYONE)));
    }

    @Test
    @DisplayName("a command and a cvar sharing one name cannot be constructed")
    void namespaceOverlapIsABuildError() {
        CommandRegistry commands = new CommandRegistry();
        CvarRegistry cvars = new CvarRegistry();
        commands.register(CommandSpec.builder("speed")
            .handler((ctx, args) -> CommandResult.info()).build());
        cvars.register(Cvar.builder("speed", ArgTypes.FLOAT(0f, 10f), "1.0").build());
        assertThrows(IllegalStateException.class, () -> new CommandDispatcher(commands, cvars));
    }

    @Test
    @DisplayName("a handler throwing unchecked becomes an error result, never a crash of the caller")
    void handlerExceptionsAreContained() {
        CommandRegistry commands = new CommandRegistry();
        commands.register(CommandSpec.builder("boom")
            .handler((ctx, args) -> {
                throw new IllegalStateException("kablam");
            })
            .build());
        CommandDispatcher dispatcher = new CommandDispatcher(commands, new CvarRegistry());
        CommandResult result = dispatcher.dispatch("boom", CommandContext.local(Permission.EVERYONE));
        assertFalse(result.ok());
        assertTrue(result.lines().get(0).contains("kablam"), result.lines().get(0));
    }

    @Test
    @DisplayName("a spec without a handler refuses execution (the client's forward-only copy)")
    void handlerlessSpecRefuses() {
        CommandRegistry commands = new CommandRegistry();
        commands.register(CommandSpec.builder("gimme")
            .description("description-only copy").side(CommandSide.SERVER).build());
        CommandDispatcher dispatcher = new CommandDispatcher(commands, new CvarRegistry());
        CommandResult result = dispatcher.dispatch("gimme", CommandContext.local(Permission.ADMIN));
        assertFalse(result.ok());
        assertTrue(result.lines().get(0).contains("not executable"), result.lines().get(0));
    }
}
