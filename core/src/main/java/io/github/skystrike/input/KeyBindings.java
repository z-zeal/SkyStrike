package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Key and button bindings per mechanics §9.
 *
 * <p>Names map to bindings through a small action table so the {@code bind}/{@code unbind}
 * console commands (and later the settings screen) edit the same fields gameplay polls — one
 * store, two front-ends. Binding an action sets its primary key and clears its secondaries;
 * resetting restores the factory set.
 */
public final class KeyBindings {

    /** One rebindable action: read/write access to the matching key fields plus the default. */
    private enum InputKind {
        KEY,
        BUTTON_OR_KEY
    }

    private record Action(
        IntSupplier get,
        IntConsumer set,
        IntSupplier defaultCode,
        Runnable restoreDefault,
        InputKind inputKind
    ) {
    }

    public int moveLeftPrimary = Input.Keys.A;
    public int moveLeftSecondary = Input.Keys.LEFT;

    public int moveRightPrimary = Input.Keys.D;
    public int moveRightSecondary = Input.Keys.RIGHT;

    public int jumpPrimary = Input.Keys.W;
    public int jumpSecondary = Input.Keys.UP;

    public int crouchPrimary = Input.Keys.S;
    public int crouchSecondary = Input.Keys.DOWN;
    public int crouchTertiary = Input.Keys.CONTROL_LEFT;

    public int jetpackKey = Input.Keys.SPACE;

    public int fireButton = Input.Buttons.LEFT;
    public int adsButton = Input.Buttons.RIGHT;

    public int slot1 = Input.Keys.NUM_1;
    public int slot2 = Input.Keys.NUM_2;
    public int slot3 = Input.Keys.NUM_3;
    public int slot4 = Input.Keys.NUM_4;
    public int slot5 = Input.Keys.NUM_5;

    /** Slot cycling — the keyboard stand-ins for the mouse wheel (same semantics). */
    public int weaponPrev = Input.Keys.LEFT_BRACKET;
    public int weaponNext = Input.Keys.RIGHT_BRACKET;

    public int gadgetQ = Input.Keys.Q;
    public int gadgetE = Input.Keys.E;
    public int viewCycle = Input.Keys.NUM_6;
    public int viewExit = Input.Keys.ESCAPE;

    /**
     * Opens the chat/console dialog (console plan §3.1: one key, default Enter, rebindable).
     * The dialog's own processor consumes it once open, so this only matters as the opener.
     */
    public int openChat = Input.Keys.ENTER;

    /**
     * Opens the loadout picker (build plan M4 §5: {@code ui_loadout}, default {@code L}). Like
     * the chat key this only matters as the opener — the picker's own processor owns the
     * keyboard once it has focus.
     */
    public int uiLoadout = Input.Keys.L;

    // Debug toolkit (build plan M3 §4): one key per cvar/command, bound exactly like any other
    // action. DebugKeyController polls these and submits the matching command line — nothing
    // here ever flips a debug boolean directly.
    public int clDebugOverlayKey = Input.Keys.F1;
    public int clFreecamKey = Input.Keys.F2;
    public int rShadowsKey = Input.Keys.F3;
    public int svInfiniteAmmoKey = Input.Keys.F4;
    public int respawnKey = Input.Keys.F5;
    public int rShowSdfKey = Input.Keys.F6;
    public int svNoclipKey = Input.Keys.F7;
    public int svGodmodeKey = Input.Keys.F8;
    public int rShowHitboxesKey = Input.Keys.F9;
    public int rPlayerLightKey = Input.Keys.F10;
    public int fxDebugKey = Input.Keys.F11;
    public int uiContrastTestKey = Input.Keys.F12;

    private final Map<String, Action> actions = new LinkedHashMap<>();

    /** Preferences namespace shared by the settings UI and console bind commands. */
    public static final String PREFERENCES_NAME = "skystrike.keybindings";

    public KeyBindings() {
        recordPair("moveLeft",
            () -> moveLeftPrimary, v -> moveLeftPrimary = v,
            () -> Input.Keys.A,
            () -> { moveLeftPrimary = Input.Keys.A; moveLeftSecondary = Input.Keys.LEFT; });
        recordPair("moveRight",
            () -> moveRightPrimary, v -> moveRightPrimary = v,
            () -> Input.Keys.D,
            () -> { moveRightPrimary = Input.Keys.D; moveRightSecondary = Input.Keys.RIGHT; });
        recordPair("jump",
            () -> jumpPrimary, v -> jumpPrimary = v,
            () -> Input.Keys.W,
            () -> { jumpPrimary = Input.Keys.W; jumpSecondary = Input.Keys.UP; });
        recordPair("crouch",
            () -> crouchPrimary, v -> crouchPrimary = v,
            () -> Input.Keys.S,
            () -> {
                crouchPrimary = Input.Keys.S;
                crouchSecondary = Input.Keys.DOWN;
                crouchTertiary = Input.Keys.CONTROL_LEFT;
            });
        recordSingle("jetpack", () -> jetpackKey, v -> jetpackKey = v, () -> Input.Keys.SPACE);
        // Mouse actions are included in the same registry so the controls screen can enumerate
        // every gameplay action; bind commands may still assign a keyboard key to them.
        recordPointerOrKey("fire", () -> fireButton, v -> fireButton = v,
            () -> Input.Buttons.LEFT);
        recordPointerOrKey("ads", () -> adsButton, v -> adsButton = v,
            () -> Input.Buttons.RIGHT);
        recordSingle("slot1", () -> slot1, v -> slot1 = v, () -> Input.Keys.NUM_1);
        recordSingle("slot2", () -> slot2, v -> slot2 = v, () -> Input.Keys.NUM_2);
        recordSingle("slot3", () -> slot3, v -> slot3 = v, () -> Input.Keys.NUM_3);
        recordSingle("slot4", () -> slot4, v -> slot4 = v, () -> Input.Keys.NUM_4);
        recordSingle("slot5", () -> slot5, v -> slot5 = v, () -> Input.Keys.NUM_5);
        recordSingle("weaponPrev", () -> weaponPrev, v -> weaponPrev = v,
            () -> Input.Keys.LEFT_BRACKET);
        recordSingle("weaponNext", () -> weaponNext, v -> weaponNext = v,
            () -> Input.Keys.RIGHT_BRACKET);
        recordSingle("gadgetQ", () -> gadgetQ, v -> gadgetQ = v, () -> Input.Keys.Q);
        recordSingle("gadgetE", () -> gadgetE, v -> gadgetE = v, () -> Input.Keys.E);
        recordSingle("viewCycle", () -> viewCycle, v -> viewCycle = v, () -> Input.Keys.NUM_6);
        recordSingle("viewExit", () -> viewExit, v -> viewExit = v, () -> Input.Keys.ESCAPE);
        recordSingle("openChat", () -> openChat, v -> openChat = v, () -> Input.Keys.ENTER);
        recordSingle("uiLoadout", () -> uiLoadout, v -> uiLoadout = v, () -> Input.Keys.L);

        recordSingle("clDebugOverlay", () -> clDebugOverlayKey, v -> clDebugOverlayKey = v,
            () -> Input.Keys.F1);
        recordSingle("clFreecam", () -> clFreecamKey, v -> clFreecamKey = v, () -> Input.Keys.F2);
        recordSingle("rShadows", () -> rShadowsKey, v -> rShadowsKey = v, () -> Input.Keys.F3);
        recordSingle("svInfiniteAmmo", () -> svInfiniteAmmoKey, v -> svInfiniteAmmoKey = v,
            () -> Input.Keys.F4);
        recordSingle("respawn", () -> respawnKey, v -> respawnKey = v, () -> Input.Keys.F5);
        recordSingle("rShowSdf", () -> rShowSdfKey, v -> rShowSdfKey = v, () -> Input.Keys.F6);
        recordSingle("svNoclip", () -> svNoclipKey, v -> svNoclipKey = v, () -> Input.Keys.F7);
        recordSingle("svGodmode", () -> svGodmodeKey, v -> svGodmodeKey = v, () -> Input.Keys.F8);
        recordSingle("rShowHitboxes", () -> rShowHitboxesKey, v -> rShowHitboxesKey = v,
            () -> Input.Keys.F9);
        recordSingle("rPlayerLight", () -> rPlayerLightKey, v -> rPlayerLightKey = v,
            () -> Input.Keys.F10);
        recordSingle("fxDebug", () -> fxDebugKey, v -> fxDebugKey = v, () -> Input.Keys.F11);
        recordSingle("uiContrastTest", () -> uiContrastTestKey, v -> uiContrastTestKey = v,
            () -> Input.Keys.F12);
        loadPreferences();
    }

    /** Loads user bindings; malformed or unknown entries are ignored safely. */
    public void loadPreferences() {
        com.badlogic.gdx.Preferences preferences = Gdx.app == null ? null
            : Gdx.app.getPreferences(PREFERENCES_NAME);
        if (preferences == null) return;
        for (String name : actions.keySet()) {
            if (preferences.contains(name)) {
                int code = preferences.getInteger(name, -1);
                if (code >= 0) actions.get(name).set().accept(code);
            }
        }
    }

    /** Persists every action in the same store used by settings and console commands. */
    public void savePreferences() {
        if (Gdx.app == null) return;
        com.badlogic.gdx.Preferences preferences = Gdx.app.getPreferences(PREFERENCES_NAME);
        for (Map.Entry<String, Action> entry : actions.entrySet()) {
            preferences.putInteger(entry.getKey(), entry.getValue().get().getAsInt());
        }
        preferences.flush();
    }

    /** Small concrete reset avoids a second persistence code path for settings and console. */
    private static final class ResetSingle implements Runnable {
        private final IntConsumer set;
        private final IntSupplier defaultCode;

        ResetSingle(IntConsumer set, IntSupplier defaultCode) {
            this.set = set;
            this.defaultCode = defaultCode;
        }

        @Override
        public void run() {
            set.accept(defaultCode.getAsInt());
        }
    }

    /** Single-key action: default is the only default. */
    private void recordSingle(
        String name, IntSupplier get, IntConsumer set, IntSupplier defaultCode) {
        actions.put(name, new Action(get, set, defaultCode,
            new ResetSingle(set, defaultCode), InputKind.KEY));
    }

    /** Mouse actions may keep a mouse button or be rebound to a keyboard key. */
    private void recordPointerOrKey(
        String name, IntSupplier get, IntConsumer set, IntSupplier defaultCode) {
        actions.put(name, new Action(get, set, defaultCode,
            new ResetSingle(set, defaultCode), InputKind.BUTTON_OR_KEY));
    }

    /** Action whose reset restores more than the primary key (primaries plus alternates). */
    private void recordPair(
        String name, IntSupplier get, IntConsumer set, IntSupplier defaultCode,
        Runnable restoreDefault) {
        actions.put(name, new Action(get, set, defaultCode, restoreDefault, InputKind.KEY));
    }

    private Action find(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Action> entry : actions.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Whether {@code name} names a rebindable action, case-insensitively. */
    public boolean hasAction(String name) {
        return find(name) != null;
    }

    /** Action names for completion and the {@code bind} argument description. */
    public List<String> actionNames() {
        return new ArrayList<>(actions.keySet());
    }

    /**
     * Binds {@code action} to the key named {@code keyName}.
     *
     * @return {@code null} on success, or the player-readable reason it failed
     */
    public String bindByName(String action, String keyName) {
        Action found = find(action);
        if (found == null) {
            return "unknown action '" + action + "' (bindings: " + String.join(", ", actionNames()) + ")";
        }
        int code = found.inputKind() == InputKind.BUTTON_OR_KEY ? buttonCodeFor(keyName) : -1;
        if (code < 0) {
            code = keycodeFor(keyName);
        }
        return bind(found, action, code, keyName);
    }

    /**
     * Binds an action to an already captured libGDX keyboard code. This is the settings screen's
     * counterpart to the console's name-based bind command, and both persist through this class.
     */
    public String bindKeycode(String action, int keycode) {
        Action found = find(action);
        return found == null ? unknownAction(action) : bind(found, action, keycode, null);
    }

    private String bind(Action found, String action, int code, String keyName) {
        if (code < 0) {
            return "unknown key '" + keyName + "'";
        }
        found.set().accept(code);
        savePreferences();
        return null;
    }

    private String unknownAction(String action) {
        return "unknown action '" + action + "' (bindings: "
            + String.join(", ", actionNames()) + ")";
    }

    /** Restores the factory binding(s) for {@code action}. */
    public String resetByName(String action) {
        Action found = find(action);
        if (found == null) {
            return unknownAction(action);
        }
        found.restoreDefault().run();
        savePreferences();
        return null;
    }

    /** The display name of {@code action}'s primary key, e.g. {@code ENTER}. */
    public String keyNameFor(String action) {
        Action found = find(action);
        if (found == null) {
            return "?";
        }
        int code = found.get().getAsInt();
        if (found.inputKind() == InputKind.BUTTON_OR_KEY && isMouseButton(code)) {
            return mouseButtonName(code);
        }
        String name = Input.Keys.toString(code);
        return name == null ? "?" : name;
    }

    private static int buttonCodeFor(String buttonName) {
        if (buttonName == null) {
            return -1;
        }
        return switch (buttonName.trim().toUpperCase(Locale.ROOT)) {
            case "MOUSE_LEFT", "LEFT_MOUSE", "LMB" -> Input.Buttons.LEFT;
            case "MOUSE_RIGHT", "RIGHT_MOUSE", "RMB" -> Input.Buttons.RIGHT;
            case "MOUSE_MIDDLE", "MIDDLE_MOUSE", "MMB" -> Input.Buttons.MIDDLE;
            case "MOUSE_BACK", "BACK_MOUSE" -> Input.Buttons.BACK;
            case "MOUSE_FORWARD", "FORWARD_MOUSE" -> Input.Buttons.FORWARD;
            default -> -1;
        };
    }

    private static boolean isMouseButton(int code) {
        return code >= Input.Buttons.LEFT && code <= Input.Buttons.FORWARD;
    }

    private static String mouseButtonName(int code) {
        return switch (code) {
            case Input.Buttons.LEFT -> "MOUSE_LEFT";
            case Input.Buttons.RIGHT -> "MOUSE_RIGHT";
            case Input.Buttons.MIDDLE -> "MOUSE_MIDDLE";
            case Input.Buttons.BACK -> "MOUSE_BACK";
            case Input.Buttons.FORWARD -> "MOUSE_FORWARD";
            default -> "?";
        };
    }

    /** Parses a key name tolerantly: exact, then upper-case; negative when unresolvable. */
    private static int keycodeFor(String keyName) {
        if (keyName == null) {
            return -1;
        }
        String trimmed = keyName.trim();
        int code = Input.Keys.valueOf(trimmed);
        return code >= 0 ? code : Input.Keys.valueOf(trimmed.toUpperCase(Locale.ROOT));
    }

    public boolean isMoveLeftPressed() {
        return Gdx.input.isKeyPressed(moveLeftPrimary) || Gdx.input.isKeyPressed(moveLeftSecondary);
    }

    public boolean isMoveRightPressed() {
        return Gdx.input.isKeyPressed(moveRightPrimary) || Gdx.input.isKeyPressed(moveRightSecondary);
    }

    public boolean isJumpPressed() {
        return Gdx.input.isKeyPressed(jumpPrimary) || Gdx.input.isKeyPressed(jumpSecondary);
    }

    public boolean isCrouchPressed() {
        return Gdx.input.isKeyPressed(crouchPrimary)
            || Gdx.input.isKeyPressed(crouchSecondary)
            || Gdx.input.isKeyPressed(crouchTertiary);
    }

    public boolean isJetpackPressed() {
        return Gdx.input.isKeyPressed(jetpackKey);
    }

    public boolean isFirePressed() {
        return isPointerOrKeyPressed(fireButton);
    }

    /** Edge-triggered: true only on the frame the fire binding goes down. */
    public boolean isFireJustPressed() {
        return isMouseButton(fireButton)
            ? Gdx.input.isButtonJustPressed(fireButton)
            : Gdx.input.isKeyJustPressed(fireButton);
    }

    public boolean isAdsPressed() {
        return isPointerOrKeyPressed(adsButton);
    }

    private static boolean isPointerOrKeyPressed(int code) {
        return isMouseButton(code) ? Gdx.input.isButtonPressed(code) : Gdx.input.isKeyPressed(code);
    }

    /** Edge-triggered: true only on the frame the key goes down. */
    public boolean isWeaponPrevJustPressed() {
        return Gdx.input.isKeyJustPressed(weaponPrev);
    }

    /** Edge-triggered: true only on the frame the key goes down. */
    public boolean isWeaponNextJustPressed() {
        return Gdx.input.isKeyJustPressed(weaponNext);
    }

    /** Edge-triggered: true only on the frame Q goes down. */
    public boolean isGadgetQJustPressed() {
        return Gdx.input.isKeyJustPressed(gadgetQ);
    }

    /** Edge-triggered: true only on the frame E goes down. */
    public boolean isGadgetEJustPressed() {
        return Gdx.input.isKeyJustPressed(gadgetE);
    }

    /** Edge-triggered: true only on the frame the chat key goes down. */
    public boolean isOpenChatJustPressed() {
        return Gdx.input.isKeyJustPressed(openChat);
    }

    /** Edge-triggered: true only on the frame the loadout-picker key goes down. */
    public boolean isUiLoadoutJustPressed() {
        return Gdx.input.isKeyJustPressed(uiLoadout);
    }

    // --- Debug toolkit (build plan M3 §4), each edge-triggered like every other action ---------

    public boolean isClDebugOverlayJustPressed() {
        return Gdx.input.isKeyJustPressed(clDebugOverlayKey);
    }

    public boolean isClFreecamJustPressed() {
        return Gdx.input.isKeyJustPressed(clFreecamKey);
    }

    public boolean isRShadowsJustPressed() {
        return Gdx.input.isKeyJustPressed(rShadowsKey);
    }

    public boolean isSvInfiniteAmmoJustPressed() {
        return Gdx.input.isKeyJustPressed(svInfiniteAmmoKey);
    }

    public boolean isRespawnJustPressed() {
        return Gdx.input.isKeyJustPressed(respawnKey);
    }

    public boolean isRShowSdfJustPressed() {
        return Gdx.input.isKeyJustPressed(rShowSdfKey);
    }

    public boolean isSvNoclipJustPressed() {
        return Gdx.input.isKeyJustPressed(svNoclipKey);
    }

    public boolean isSvGodmodeJustPressed() {
        return Gdx.input.isKeyJustPressed(svGodmodeKey);
    }

    public boolean isRShowHitboxesJustPressed() {
        return Gdx.input.isKeyJustPressed(rShowHitboxesKey);
    }

    public boolean isRPlayerLightJustPressed() {
        return Gdx.input.isKeyJustPressed(rPlayerLightKey);
    }

    public boolean isFxDebugJustPressed() {
        return Gdx.input.isKeyJustPressed(fxDebugKey);
    }

    public boolean isUiContrastTestJustPressed() {
        return Gdx.input.isKeyJustPressed(uiContrastTestKey);
    }

    /** Edge-triggered: true only on the frame a loadout slot key (1–5) goes down. */
    public boolean isSlotJustPressed(int slot) {
        return switch (slot) {
            case 1 -> Gdx.input.isKeyJustPressed(slot1);
            case 2 -> Gdx.input.isKeyJustPressed(slot2);
            case 3 -> Gdx.input.isKeyJustPressed(slot3);
            case 4 -> Gdx.input.isKeyJustPressed(slot4);
            case 5 -> Gdx.input.isKeyJustPressed(slot5);
            default -> false;
        };
    }
}
