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
 * The registry's security posture: listing and completion <i>hide</i> what you cannot run, and
 * executing it anyway is indistinguishable from a typo.
 */
class CommandRegistryTest {

    private static CommandSpec command(String name, Permission permission, String... aliases) {
        CommandSpec.Builder builder =
            CommandSpec.builder(name).description(name + " description").permission(permission);
        for (String alias : aliases) {
            builder.alias(alias);
        }
        return builder.handler((ctx, args) -> CommandResult.info(name + " ran")).build();
    }

    private static CommandRegistry registry() {
        CommandRegistry registry = new CommandRegistry();
        registry.register(command("players", Permission.EVERYONE));
        registry.register(command("say", Permission.MODERATOR, "announce"));
        registry.register(command("kick", Permission.MODERATOR));
        registry.register(command("tickrate", Permission.ADMIN, "tps"));
        return registry;
    }

    @Test
    @DisplayName("names and aliases resolve case-insensitively to the one spec")
    void lookupIsCaseInsensitiveAndAliasAware() {
        CommandRegistry registry = registry();
        assertEquals("say", registry.find("SAY").name());
        assertEquals("say", registry.find("Announce").name());
        assertEquals("tickrate", registry.find("tps").name());
        assertNull(registry.find("ban"));
    }

    @Test
    @DisplayName("duplicate names and duplicate aliases are build errors, not overrides")
    void duplicatesThrow() {
        CommandRegistry registry = registry();
        assertThrows(IllegalStateException.class,
            () -> registry.register(command("kick", Permission.ADMIN)));
        assertThrows(IllegalStateException.class,
            () -> registry.register(command("boot", Permission.ADMIN, "say")));
    }

    @Test
    @DisplayName("permission filtering hides rather than marks: a player cannot see moderation commands")
    void listingIsFilteredNotFlagged() {
        CommandRegistry registry = registry();
        List<CommandSpec> visibleToPlayer = registry.visibleTo(Permission.PLAYER);
        assertEquals(1, visibleToPlayer.size());
        assertEquals("players", visibleToPlayer.get(0).name());

        assertEquals(3, registry.visibleTo(Permission.MODERATOR).size());
        assertEquals(4, registry.visibleTo(Permission.ADMIN).size());
    }

    @Test
    @DisplayName("name completion never offers a command the caller cannot run")
    void completionIsFiltered() {
        CommandRegistry registry = registry();
        assertEquals(List.of("players"), registry.completeNames("p", Permission.PLAYER));
        assertTrue(registry.completeNames("s", Permission.PLAYER).isEmpty());
        assertEquals(List.of("say"), registry.completeNames("s", Permission.MODERATOR));
        assertEquals(List.of("tickrate", "tps"), registry.completeNames("t", Permission.ADMIN));
    }

    @Test
    @DisplayName("a close typo suggests the right command, a distant one suggests nothing")
    void didYouMeanByEditDistance() {
        CommandRegistry registry = registry();
        assertEquals("kick", registry.suggestionFor("kikc", Permission.MODERATOR));
        assertEquals("players", registry.suggestionFor("playerz", Permission.PLAYER));
        assertNull(registry.suggestionFor("xylophone", Permission.ADMIN));
        // A suggestion must never leak a command above the caller's level.
        assertNull(registry.suggestionFor("kikc", Permission.PLAYER));
    }

    @Test
    @DisplayName("the refusal is identical for a typo and for a command above your level")
    void refusalCannotConfirmExistence() {
        CommandRegistry registry = registry();
        List<String> forTypo = registry.unknownLines("kikc", Permission.PLAYER);
        List<String> forHidden = registry.unknownLines("kick", Permission.PLAYER);
        assertEquals(forTypo.get(0), forHidden.get(0));
        assertTrue(forHidden.get(0).contains("kick"));
        assertFalse(forHidden.get(0).contains("permission"),
            "the refusal must not name the missing level");
    }
}
