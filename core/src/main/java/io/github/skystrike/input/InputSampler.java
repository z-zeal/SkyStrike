package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.WeaponId;

/**
 * Polls gameplay keyboard, mouse and touch state into a {@link PacketPlayerInput}.
 *
 * <p>The sampler also owns the <b>requested</b> weapon. Every packet carries it rather than only
 * the packets where it changed: input is unreliable, and a dropped "I switched to the AWP" is a
 * player holding the wrong gun until they press the key again. The server ignores a request for
 * the weapon already in hand, so repeating it costs nothing.
 */
public final class InputSampler {

    private final KeyBindings bindings;
    private final InputRouter router;
    private final Vector3 mouseScreenVec = new Vector3();
    private long sequenceCounter = 1L;
    private int selectedWeapon = WeaponId.DEFAULT.ordinal();

    public InputSampler(KeyBindings bindings, InputRouter router) {
        this.bindings = bindings;
        this.router = router;
    }

    /**
     * Samples live input relative to {@code localPlayer} and {@code camera}.
     */
    public PacketPlayerInput sample(Player localPlayer, GameCamera camera) {
        long sequence = sequenceCounter++;
        if (!router.isGameplayActive() || localPlayer == null || camera == null) {
            return new PacketPlayerInput(
                sequence, 0f, false, false, false, false, false, 0f, selectedWeapon);
        }

        pollWeaponCycle();

        float moveX = 0f;
        if (bindings.isMoveLeftPressed()) {
            moveX -= 1f;
        }
        if (bindings.isMoveRightPressed()) {
            moveX += 1f;
        }

        boolean jump = bindings.isJumpPressed();
        boolean crouch = bindings.isCrouchPressed();
        boolean jetpack = bindings.isJetpackPressed();
        boolean ads = bindings.isAdsPressed();
        boolean fire = bindings.isFirePressed();

        mouseScreenVec.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.raw().unproject(mouseScreenVec);
        float aimAngle = Angles.ofVector(
            mouseScreenVec.x - localPlayer.eyeX(),
            mouseScreenVec.y - localPlayer.eyeY());

        return new PacketPlayerInput(
            sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle, selectedWeapon);
    }

    private void pollWeaponCycle() {
        int count = WeaponId.values().length;
        if (bindings.isWeaponNextJustPressed()) {
            selectedWeapon = (selectedWeapon + 1) % count;
        }
        if (bindings.isWeaponPrevJustPressed()) {
            selectedWeapon = (selectedWeapon + count - 1) % count;
        }
    }

    /** The weapon this client is asking for. */
    public WeaponId selectedWeapon() {
        return WeaponId.fromOrdinal(selectedWeapon);
    }

    /** Forces the requested weapon, e.g. when the server corrects the client. */
    public void setSelectedWeapon(WeaponId weaponId) {
        if (weaponId != null) {
            this.selectedWeapon = weaponId.ordinal();
        }
    }

    public KeyBindings bindings() {
        return bindings;
    }

    public InputRouter router() {
        return router;
    }
}
