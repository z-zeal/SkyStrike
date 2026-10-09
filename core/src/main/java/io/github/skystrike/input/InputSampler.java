package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import io.github.skystrike.gameplay.LoadoutController;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * Polls gameplay keyboard, mouse and touch state into a {@link PacketPlayerInput}.
 *
 * <p>Loadout slot selection is owned by the {@link LoadoutController}: the sampler only asks it
 * whether a slot press is still waiting for its acknowledgement, and if so carries it (with its
 * birth sequence) on the next packet. Repeating a level like "fire held" is trivially safe;
 * repeating the press edge is what the birth sequence exists for.
 */
public final class InputSampler {

    private final KeyBindings bindings;
    private final InputRouter router;
    private final LoadoutController loadoutController;
    private final Vector3 mouseScreenVec = new Vector3();
    private long sequenceCounter = 1L;

    /** {@code cl_freecam} (build plan M3 §4): while true, sampled input carries zeroed intent. */
    private boolean freecamActive;

    public InputSampler(KeyBindings bindings, InputRouter router, LoadoutController loadoutController) {
        this.bindings = bindings;
        this.router = router;
        this.loadoutController = loadoutController;
    }

    /**
     * Set once per frame from {@code cl_freecam}'s current value, before {@link #sample}. The
     * camera detaches and pans freely, but the player's own input packets must keep flowing with
     * no movement intent — the server stays authoritative over a player who is still "there".
     */
    public void setFreecamActive(boolean freecamActive) {
        this.freecamActive = freecamActive;
    }

    /**
     * Samples live input relative to {@code localPlayer} and {@code camera}, aiming from the
     * player's own eye.
     */
    public PacketPlayerInput sample(Player localPlayer, GameCamera camera) {
        return sample(
            localPlayer,
            camera,
            localPlayer == null ? 0f : localPlayer.eyeX(),
            localPlayer == null ? 0f : localPlayer.eyeY());
    }

    /**
     * Samples live input relative to {@code localPlayer} and {@code camera}, aiming from an
     * explicit anchor. While the player pilots a drone or views through a camera, the anchor is
     * the device's position — the aim is what steers the device's vision cone, so it must be
     * measured from the eyes the player is actually looking through, not from the locked body.
     */
    public PacketPlayerInput sample(Player localPlayer, GameCamera camera, float aimAnchorX, float aimAnchorY) {
        long sequence = sequenceCounter++;
        PacketPlayerInput packet;
        if (!router.isGameplayActive() || localPlayer == null || camera == null || freecamActive) {
            packet = new PacketPlayerInput(
                sequence, 0f, false, false, false, false, false, 0f, PacketPlayerInput.NO_SLOT_PRESS);
        } else {
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
                mouseScreenVec.x - aimAnchorX,
                mouseScreenVec.y - aimAnchorY);

            packet = new PacketPlayerInput(
                sequence, moveX, jump, crouch, jetpack, ads, fire, aimAngle);
        }

        if (loadoutController != null) {
            loadoutController.stampPacket(packet);
        }
        return packet;
    }

    public KeyBindings bindings() {
        return bindings;
    }

    public InputRouter router() {
        return router;
    }
}
