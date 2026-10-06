package io.github.skystrike.command;

import io.github.skystrike.input.KeyBindings;
import io.github.skystrike.shared.command.Cvar;
import io.github.skystrike.shared.config.DebugFlags;

/**
 * Polls the F1–F12 debug toolkit keys (build plan M3 §4) and submits the exact command line a
 * player typing it would — never flips a debug boolean directly. A cvar key reads the cvar's
 * current value and submits the flipped one (a bare {@code /cvar} only prints info, it never
 * toggles); a server-command key submits the bare command, whose omitted-argument form is
 * already a toggle for the caller in {@code ServerCommandCatalog}.
 *
 * <p>Entirely inert while the master debug switch is off: every row is also invisible at the
 * command/cvar registration layer, so even a stray call here would have nothing to submit to.
 */
public final class DebugKeyController {

    private final KeyBindings bindings;
    private final ClientCommandService commandService;

    public DebugKeyController(KeyBindings bindings, ClientCommandService commandService) {
        if (bindings == null || commandService == null) {
            throw new IllegalArgumentException("bindings and commandService are required");
        }
        this.bindings = bindings;
        this.commandService = commandService;
    }

    /** Call once per gameplay frame; every row no-ops unless the master debug switch is on. */
    public void update() {
        if (!DebugFlags.enabled()) {
            return;
        }
        pollCvar(bindings.isClDebugOverlayJustPressed(), "cl_debug_overlay");
        pollCvar(bindings.isClFreecamJustPressed(), "cl_freecam");
        pollCvar(bindings.isRShadowsJustPressed(), "r_shadows");
        pollCommand(bindings.isSvInfiniteAmmoJustPressed(), "infiniteammo");
        pollCommand(bindings.isRespawnJustPressed(), "respawn");
        pollCvar(bindings.isRShowSdfJustPressed(), "r_show_sdf");
        pollCommand(bindings.isSvNoclipJustPressed(), "noclip");
        pollCommand(bindings.isSvGodmodeJustPressed(), "godmode");
        pollCvar(bindings.isRShowHitboxesJustPressed(), "r_show_hitboxes");
        pollCvar(bindings.isRPlayerLightJustPressed(), "r_player_light");
        pollCvar(bindings.isFxDebugJustPressed(), "fx_debug");
        pollCvar(bindings.isUiContrastTestJustPressed(), "ui_contrast_test");
    }

    /** Flips a boolean cvar by reading its current value first; a bare set only ever prints it. */
    private void pollCvar(boolean justPressed, String cvarName) {
        if (!justPressed) {
            return;
        }
        Cvar cvar = commandService.cvars().find(cvarName);
        boolean current = cvar != null && Boolean.parseBoolean(cvar.value());
        commandService.submit("/" + cvarName + " " + !current);
    }

    /** Submits the bare command: the catalog's omitted-argument form already toggles. */
    private void pollCommand(boolean justPressed, String commandName) {
        if (!justPressed) {
            return;
        }
        commandService.submit("/" + commandName);
    }
}
