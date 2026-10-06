package io.github.skystrike.shared.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cvar behaviour: value clamping to the declared type, canonical storage of bool spellings, and
 * a change hook that fires exactly once per effective change.
 */
class CvarRegistryTest {

    @Test
    @DisplayName("setting an out-of-range value fails and leaves the old value in place")
    void rangeViolationKeepsOldValue() throws CommandException {
        Cvar scale = Cvar.builder("timescale", ArgTypes.FLOAT(0.1f, 4f), "1.0").build();
        assertThrows(CommandException.class, () -> scale.set("9"));
        assertEquals("1.0", scale.value());
        scale.set("2");
        assertEquals("2.0", scale.value());
    }

    @Test
    @DisplayName("every bool spelling stores the same canonical value")
    void boolValuesCanonicalise() throws CommandException {
        Cvar shadows = Cvar.builder("r_shadows", ArgTypes.BOOL, "true").build();
        shadows.set("off");
        assertEquals("false", shadows.value());
        shadows.set("1");
        assertEquals("true", shadows.value());
        shadows.set("no");
        assertEquals("false", shadows.value());
    }

    @Test
    @DisplayName("the hook fires once per effective change and never for a no-op write")
    void hookFiresOncePerChange() throws CommandException {
        List<String> events = new ArrayList<>();
        Cvar quality = Cvar.builder("quality", ArgTypes.ENUM(Tier.class), "mid")
            .onChange((before, after) -> events.add(before + "->" + after))
            .build();

        assertFalse(quality.set("mid"), "writing the current value is a no-op");
        assertTrue(events.isEmpty());

        assertTrue(quality.set("HIGH"));
        assertFalse(quality.set("HIGH"), "re-setting the current value changes nothing");
        assertEquals(List.of("mid->high"), events);
    }

    @Test
    @DisplayName("a default that violates its own type is rejected at build time")
    void badDefaultFailsAtBuild() {
        Cvar.Builder builder = Cvar.builder("hp", ArgTypes.INT(1, 150), "0");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("listing and completion honour the cvar's permission")
    void permissionFilteringMatchesCommands() {
        CvarRegistry registry = new CvarRegistry();
        registry.register(Cvar.builder("quality", ArgTypes.ENUM(Tier.class), "mid").build());
        registry.register(Cvar.builder("sv_timescale", ArgTypes.FLOAT(0.1f, 4f), "1.0")
            .permission(Permission.ADMIN).build());

        assertEquals(1, registry.visibleTo(Permission.PLAYER).size());
        assertEquals(2, registry.visibleTo(Permission.ADMIN).size());
        assertEquals(List.of("quality"), registry.completeNames("", Permission.PLAYER));
    }

    @Test
    @DisplayName("a no-value read describes current, default and meaning")
    void describeLinesCarryCurrentDefaultDescription() {
        Cvar shadows = Cvar.builder("r_shadows", ArgTypes.BOOL, "true")
            .description("SDF soft shadows").build();
        List<String> lines = shadows.describeLines();
        assertTrue(lines.get(0).contains("r_shadows = true"), lines.get(0));
        assertTrue(lines.get(0).contains("default: true"), lines.get(0));
        assertTrue(lines.get(1).contains("SDF soft shadows"), lines.get(1));
    }

    private enum Tier {
        LOW, MID, HIGH
    }
}
