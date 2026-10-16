package io.github.skystrike.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector3;
import io.github.skystrike.gameplay.LoadoutController;
import io.github.skystrike.render.GameCamera;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.input.TouchSample;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;

/**
 * Polls gameplay keyboard and mouse state into a {@link PacketPlayerInput}, or, on a touch
 * device, copies one {@link TouchSample} onto it (AN-3).
 *
 * <p>Touch is the control path when a sample is passed. Then no keyboard or mouse binding is read
 * and aim comes from the touch aim stick, so a finger is never mistaken for a mouse click.
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

    /** Fire level of the last touch sample, so a held finger yields one press edge. */
    private boolean lastTouchFire;

    /** The fire press edge of the most recent sample, for the gun's empty-click sound. */
    private boolean firePressEdge;

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
        return sample(localPlayer, camera, aimAnchorX, aimAnchorY, null);
    }

    /**
     * As {@link #sample(Player, GameCamera, float, float)}, with the touch sample for this frame.
     * A non-null {@code touch} makes touch the control path: no keyboard or mouse binding is read,
     * and aim is {@link TouchSample#aimAngle()}. Pass {@code null} for the desktop bindings.
     * Loadout, view and door edges are not carried here; their controllers apply them, and
     * {@code stampPacket} puts them on the packet.
     */
    public PacketPlayerInput sample(
        Player localPlayer, GameCamera camera, float aimAnchorX, float aimAnchorY, TouchSample touch) {
        long sequence = sequenceCounter++;
        PacketPlayerInput packet;
        firePressEdge = false;
        if (!router.isGameplayActive() || localPlayer == null || camera == null || freecamActive) {
            lastTouchFire = false;
            packet = new PacketPlayerInput(
                sequence, 0f, false, false, false, false, false, 0f, PacketPlayerInput.NO_SLOT_PRESS);
        } else if (touch != null) {
            packet = touchPacket(sequence, touch);
        } else {
            lastTouchFire = false;
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
            firePressEdge = bindings.isFireJustPressed();

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

    /**
     * True when this sample's fire input went down on this frame. A touch fire press is the
     * rising edge of the fire level, because a finger has no per-frame press event of its own.
     */
    public boolean firePressedThisSample() {
        return firePressEdge;
    }

    /** Intent from the touch sample. Movement, jump, crouch, jetpack, ADS, fire and aim only. */
    private PacketPlayerInput touchPacket(long sequence, TouchSample touch) {
        firePressEdge = touch.fire() && !lastTouchFire;
        lastTouchFire = touch.fire();
        return new PacketPlayerInput(
            sequence,
            touch.moveX(),
            touch.jump(),
            touch.crouch(),
            touch.jetpack(),
            touch.ads(),
            touch.fire(),
            touch.aimAngle());
    }

    public KeyBindings bindings() {
        return bindings;
    }

    public InputRouter router() {
        return router;
    }
}
