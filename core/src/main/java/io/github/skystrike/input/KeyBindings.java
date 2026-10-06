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
    private record Action(
        IntSupplier get,
        IntConsumer set,
        IntSupplier defaultCode,
        Runnable restoreDefault
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

    private final Map<String, Action> actions = new LinkedHashMap<>();

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
    }

    /** Single-key action: default is the only default. */
    private void recordSingle(
        String name, IntSupplier get, IntConsumer set, IntSupplier defaultCode) {
        actions.put(name, new Action(get, set, defaultCode,
            () -> set.accept(defaultCode.getAsInt())));
    }

    /** Action whose reset restores more than the primary key (primaries plus alternates). */
    private void recordPair(
        String name, IntSupplier get, IntConsumer set, IntSupplier defaultCode,
        Runnable restoreDefault) {
        actions.put(name, new Action(get, set, defaultCode, restoreDefault));
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
        int code = keycodeFor(keyName);
        if (code < 0) {
            return "unknown key '" + keyName + "'";
        }
        found.set().accept(code);
        return null;
    }

    /** Restores the factory binding(s) for {@code action}. */
    public String resetByName(String action) {
        Action found = find(action);
        if (found == null) {
            return "unknown action '" + action + "' (bindings: " + String.join(", ", actionNames()) + ")";
        }
        found.restoreDefault().run();
        return null;
    }

    /** The display name of {@code action}'s primary key, e.g. {@code ENTER}. */
    public String keyNameFor(String action) {
        Action found = find(action);
        return found == null ? "?" : Input.Keys.toString(found.get().getAsInt());
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
        return Gdx.input.isButtonPressed(fireButton);
    }

    public boolean isAdsPressed() {
        return Gdx.input.isButtonPressed(adsButton);
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
