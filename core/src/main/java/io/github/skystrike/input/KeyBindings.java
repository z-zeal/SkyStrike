package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Key and button bindings per mechanics §9.
 */
public final class KeyBindings {

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

    /** Weapon cycling. Phase 4 gives these slots a real loadout; until then they walk the table. */
    public int weaponPrev = Input.Keys.LEFT_BRACKET;
    public int weaponNext = Input.Keys.RIGHT_BRACKET;

    public int gadgetQ = Input.Keys.Q;
    public int gadgetE = Input.Keys.E;
    public int viewCycle = Input.Keys.NUM_6;
    public int viewExit = Input.Keys.ESCAPE;

    public KeyBindings() {
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
}
