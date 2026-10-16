package io.github.skystrike.shared.input;

import io.github.skystrike.shared.config.TouchConfig;
import io.github.skystrike.shared.gadget.SurveillanceView;
import io.github.skystrike.shared.math.Angles;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.net.c2s.PacketPlayerInput;
import io.github.skystrike.shared.weapons.FireMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-touch pointer ownership and the intent it produces (Android plan §3, decisions AN1–AN3).
 *
 * <p>Coordinates are HUD y-up pixels, already converted by the caller. Pointer ids outside
 * {@code 0..9} are ignored. Each pointer owns at most one control, chosen once on
 * {@link #touchDown}: a button, else the movement zone, else the aim zone. A drag updates only
 * that owner, so a left thumb crossing the midline cannot steal {@code aimAngle}, and a third
 * finger on a slot cannot perturb either stick.
 *
 * <p>The movement stick is horizontal on foot. Downward drift does not crouch, because a fresh
 * crouch on a one-way platform drops through it (decision AN1). While
 * {@link SurveillanceView#DRONE} the same stick is a flight stick: vertical deflection past
 * {@link TouchConfig#DRONE_VERTICAL_THRESHOLD} sets jump (climb) or crouch (descend). Camera
 * view does not, so leaving a camera cannot inherit a drop-through press. The aim stick always
 * floats inside its zone — the zone is the whole right half, and a fixed corner origin would
 * snap aim when the thumb touches the middle. The movement stick floats unless the preference
 * says fixed. Releasing the aim stick retains the last angle outside the deadzone.
 *
 * <p>Scheme A fires from the Fire button and, for guns, from the outer ring when that setting
 * is on. Scheme B fires automatic guns while the aim stick is past
 * {@link TouchConfig#SCHEME_B_AUTO_FIRE_THRESHOLD}, and fires a single-shot gun, melee swing or
 * throwable when the stick is released outside {@link TouchConfig#SCHEME_B_CANCEL_RADIUS}.
 * Scheme C is not implemented (decision AN2). The optional Jump double-tap / slide-up jetpack
 * gesture is not implemented: the plan gives it no timing, and the decided jetpack control is
 * the dedicated button.
 *
 * <p>AN-3 calls {@link #sample()} once per input sample and copies the result onto
 * {@link PacketPlayerInput}. Edges are consumed by that call. A tap that starts and ends between
 * samples still latches one {@code true} level, because the server derives trigger and crouch
 * edges by comparing samples. Modal focus clears every pointer and produces zeroed movement and
 * fire intent; the retained aim and an ADS toggle survive, so opening chat does not snap the
 * reticle or forget that the player was scoped. AN-3 must still drop the sample while gameplay
 * input is inactive, the same way the desktop sampler does.
 */
public final class TouchControlModel {

    private static final int POINTER_COUNT = TouchConfig.MAX_POINTER + 1;
    private static final int EDGE_QUEUE_LIMIT = 8;

    /** What the equipped item does to stick-fire. The Fire button ignores this and always fires. */
    public enum CombatItem {
        NONE,
        GUN,
        MELEE,
        THROWABLE
    }

    private final Binding[] bindings = new Binding[POINTER_COUNT];
    private final ArrayDeque<Integer> slotEdges = new ArrayDeque<>();
    private final ArrayDeque<Integer> gadgetEdges = new ArrayDeque<>();
    private final ArrayDeque<Integer> viewEdges = new ArrayDeque<>();
    private final ArrayDeque<Integer> useEdges = new ArrayDeque<>();
    private final ArrayDeque<TouchAction> uiEdges = new ArrayDeque<>();
    private final List<TouchZone> zones = new ArrayList<>();

    private TouchPreferences preferences = TouchPreferences.defaults();
    private SurveillanceView view = SurveillanceView.SELF;
    private FireMode fireMode = FireMode.SEMI;
    private CombatItem item = CombatItem.NONE;

    private int movePointer = VirtualStickState.NO_POINTER;
    private int aimPointer = VirtualStickState.NO_POINTER;
    private float moveAnchorX;
    private float moveAnchorY;
    private float moveRadius;
    private float aimAnchorX;
    private float aimAnchorY;
    private float aimRadius;

    private float aimAngle;
    private boolean modal;
    private boolean adsEngaged;

    private boolean fireHeld;
    private boolean fireLatched;
    private boolean jumpHeld;
    private boolean jumpLatched;
    private boolean crouchHeld;
    private boolean crouchLatched;
    private boolean jetpackHeld;
    private boolean jetpackLatched;
    private boolean adsHeld;
    private boolean adsLatched;
    private boolean stickJump;
    private boolean stickCrouch;
    private boolean stickFire;
    private boolean stickJumpPulse;
    private boolean stickCrouchPulse;
    private boolean stickFirePulse;

    public TouchControlModel() {
        this(0f);
    }

    public TouchControlModel(float initialAimAngle) {
        for (int i = 0; i < bindings.length; i++) {
            bindings[i] = new Binding();
        }
        this.aimAngle = sanitizeAngle(initialAimAngle);
    }

    /**
     * Spawn facing, matching {@code SpawnService}: team index 0 (Team A) faces {@code 0°}, team
     * index 1 (Team B) faces {@code 180°}. Any other index, including Neutral, faces {@code 0°}.
     */
    public static float spawnAimDegrees(int teamIndex) {
        return teamIndex == Team.TEAM_B.index() ? 180f : 0f;
    }

    public void setPreferences(TouchPreferences preferences) {
        this.preferences = preferences == null ? TouchPreferences.defaults() : preferences.validated();
    }

    public TouchPreferences preferences() {
        return preferences;
    }

    /**
     * Replaces the claim regions. Active pointers are kept: AN-4 may rebuild an equal layout
     * every frame, and that must not cancel a thumb that is already down. The next
     * {@link #touchDown} uses the new regions. Pass {@code null} for no regions.
     */
    public void setZones(List<TouchZone> zones) {
        this.zones.clear();
        moveAnchorX = 0f;
        moveAnchorY = 0f;
        moveRadius = 0f;
        aimAnchorX = 0f;
        aimAnchorY = 0f;
        aimRadius = 0f;
        if (zones == null) {
            return;
        }
        for (TouchZone zone : zones) {
            if (zone == null) {
                continue;
            }
            this.zones.add(zone);
            if (zone.kind() == TouchZone.Kind.MOVE) {
                moveAnchorX = zone.anchorX();
                moveAnchorY = zone.anchorY();
                moveRadius = zone.radius();
            } else if (zone.kind() == TouchZone.Kind.AIM) {
                aimAnchorX = zone.anchorX();
                aimAnchorY = zone.anchorY();
                aimRadius = zone.radius();
            }
        }
    }

    public void setSurveillanceView(SurveillanceView view) {
        this.view = view == null ? SurveillanceView.SELF : view;
    }

    public SurveillanceView surveillanceView() {
        return view;
    }

    /**
     * What stick-fire is allowed to do. {@link CombatItem#NONE} until AN-3 knows the equipped
     * item, so a stick cannot fire into an empty hand. The Fire button is unaffected.
     */
    public void setCombatItem(FireMode fireMode, CombatItem item) {
        this.fireMode = fireMode == null ? FireMode.SEMI : fireMode;
        this.item = item == null ? CombatItem.NONE : item;
    }

    public CombatItem combatItem() {
        return item;
    }

    /**
     * Modal focus (chat, loadout picker, pause, settings) drops every pointer and zeroes action
     * intent. Aim angle and an ADS toggle are kept. While modal, new touches are ignored.
     */
    public void setModalFocus(boolean modal) {
        if (modal) {
            reset();
        }
        this.modal = modal;
    }

    public boolean modalFocus() {
        return modal;
    }

    /**
     * Clears pointers, latches and edges. Aim angle and ADS toggle survive. AN-7's
     * {@code pause()} will call this so a backgrounded client cannot keep firing.
     */
    public void reset() {
        for (Binding binding : bindings) {
            binding.clear();
        }
        movePointer = VirtualStickState.NO_POINTER;
        aimPointer = VirtualStickState.NO_POINTER;
        fireHeld = false;
        fireLatched = false;
        jumpHeld = false;
        jumpLatched = false;
        crouchHeld = false;
        crouchLatched = false;
        jetpackHeld = false;
        jetpackLatched = false;
        adsHeld = false;
        adsLatched = false;
        stickJump = false;
        stickCrouch = false;
        stickFire = false;
        stickJumpPulse = false;
        stickCrouchPulse = false;
        stickFirePulse = false;
        slotEdges.clear();
        gadgetEdges.clear();
        viewEdges.clear();
        useEdges.clear();
        uiEdges.clear();
    }

    /**
     * External aim correction, such as a respawn facing. Ignored while an aim finger is down, so
     * a snapshot cannot fight the stick. Applied when the stick is idle, including inside a
     * later deadzone — the deadzone retains the angle the stick last set, and this replaces it
     * only once the finger is up.
     */
    public void setAimAngle(float degrees) {
        if (aimPointer != VirtualStickState.NO_POINTER) {
            return;
        }
        aimAngle = sanitizeAngle(degrees);
    }

    public float aimAngle() {
        return aimAngle;
    }

    public boolean adsEngaged() {
        return adsEngaged;
    }

    public int movePointer() {
        return movePointer;
    }

    public int aimPointer() {
        return aimPointer;
    }

    /** Claims {@code pointer} if it lands in a zone. {@code pointer} must be {@code 0..9}. */
    public boolean touchDown(int pointer, float x, float y) {
        if (modal || !validPointer(pointer) || !Float.isFinite(x) || !Float.isFinite(y)) {
            return false;
        }
        if (bindings[pointer].active) {
            releasePointer(pointer, false);
        }
        TouchZone zone = claim(x, y);
        if (zone == null) {
            return false;
        }
        Binding binding = bindings[pointer];
        binding.active = true;
        binding.kind = zone.kind();
        binding.action = zone.action();
        binding.x = x;
        binding.y = y;
        binding.radius = zone.radius();
        if (zone.kind() == TouchZone.Kind.BUTTON) {
            binding.originX = zone.anchorX();
            binding.originY = zone.anchorY();
            pressButton(zone.action());
        } else if (zone.kind() == TouchZone.Kind.MOVE) {
            movePointer = pointer;
            if (preferences.floatingMoveStick()) {
                binding.originX = x;
                binding.originY = y;
            } else {
                binding.originX = zone.anchorX();
                binding.originY = zone.anchorY();
            }
            updateDeflection(binding);
        } else {
            aimPointer = pointer;
            binding.originX = x;
            binding.originY = y;
            updateDeflection(binding);
        }
        refreshStickIntent();
        return true;
    }

    /** Updates only the control {@code pointer} already owns. */
    public boolean touchDragged(int pointer, float x, float y) {
        if (!validPointer(pointer) || !bindings[pointer].active || !Float.isFinite(x) || !Float.isFinite(y)) {
            return false;
        }
        Binding binding = bindings[pointer];
        binding.x = x;
        binding.y = y;
        if (binding.kind != TouchZone.Kind.BUTTON) {
            updateDeflection(binding);
            refreshStickIntent();
        }
        return true;
    }

    /** Releases only {@code pointer}. A scheme-B release-to-fire is evaluated here. */
    public boolean touchUp(int pointer, float x, float y) {
        if (!validPointer(pointer) || !bindings[pointer].active) {
            return false;
        }
        if (Float.isFinite(x) && Float.isFinite(y)) {
            bindings[pointer].x = x;
            bindings[pointer].y = y;
            if (bindings[pointer].kind != TouchZone.Kind.BUTTON) {
                updateDeflection(bindings[pointer]);
            }
        }
        releasePointer(pointer, true);
        refreshStickIntent();
        return true;
    }

    /**
     * Clears every pointer. The plan's pointer-reuse hazard is a cancelled id coming back as a
     * different thumb; dropping only the named id would leave the other thumb owning a stale
     * binding. Coordinates are ignored.
     */
    public boolean touchCancelled(int pointer, float x, float y) {
        // A cancelled id is reused for a different thumb, so the named point is not enough.
        boolean named = validPointer(pointer) && Float.isFinite(x) && Float.isFinite(y);
        boolean active = anyActive();
        reset();
        return active || named;
    }

    /**
     * Consumes latched edges and one-frame pulses. Call once per input sample. While modal, the
     * result is zeroed action intent with the retained aim; an ADS toggle is not reported until
     * focus returns, and it is not cleared.
     */
    public TouchSample sample() {
        refreshStickIntent();
        if (modal) {
            slotEdges.clear();
            gadgetEdges.clear();
            viewEdges.clear();
            useEdges.clear();
            uiEdges.clear();
            clearLatches();
            stickJumpPulse = false;
            stickCrouchPulse = false;
            stickFirePulse = false;
            return TouchSample.idle(aimAngle);
        }
        boolean drone = view == SurveillanceView.DRONE;
        boolean jump = jumpHeld || jumpLatched || (drone && (stickJump || stickJumpPulse));
        boolean crouch = crouchHeld || crouchLatched || (drone && (stickCrouch || stickCrouchPulse));
        boolean jetpack = jetpackHeld || jetpackLatched;
        boolean fire = fireHeld || fireLatched || stickFire || stickFirePulse;
        boolean ads = preferences.adsMode() == TouchPreferences.AdsMode.HOLD
            ? adsHeld || adsLatched
            : adsEngaged;
        TouchSample sample = new TouchSample(
            moveOutputX(),
            jump,
            crouch,
            jetpack,
            ads,
            fire,
            aimAngle,
            poll(slotEdges, PacketPlayerInput.NO_SLOT_PRESS),
            poll(gadgetEdges, PacketPlayerInput.NO_GADGET_PRESS),
            poll(viewEdges, PacketPlayerInput.NO_VIEW_ACTION),
            poll(useEdges, PacketPlayerInput.NO_USE_PRESS),
            uiEdges.poll());
        clearLatches();
        stickJumpPulse = false;
        stickCrouchPulse = false;
        stickFirePulse = false;
        return sample;
    }

    public VirtualStickState moveStick() {
        if (movePointer == VirtualStickState.NO_POINTER) {
            return VirtualStickState.idle(moveAnchorX, moveAnchorY, moveRadius);
        }
        return bindings[movePointer].toState(movePointer);
    }

    public VirtualStickState aimStick() {
        if (aimPointer == VirtualStickState.NO_POINTER) {
            return VirtualStickState.idle(aimAnchorX, aimAnchorY, aimRadius);
        }
        return bindings[aimPointer].toState(aimPointer);
    }

    private float moveOutputX() {
        if (movePointer == VirtualStickState.NO_POINTER) {
            return 0f;
        }
        return bindings[movePointer].outputX;
    }

    private void releasePointer(int pointer, boolean schemeRelease) {
        Binding binding = bindings[pointer];
        if (pointer == aimPointer && schemeRelease) {
            maybeReleaseFire(binding.radial);
        }
        if (pointer == aimPointer && !schemeRelease) {
            stickFirePulse = false;
        }
        if (pointer == movePointer && !schemeRelease) {
            stickJumpPulse = false;
            stickCrouchPulse = false;
        }
        if (binding.kind == TouchZone.Kind.BUTTON) {
            releaseButton(binding.action);
        }
        if (pointer == movePointer) {
            movePointer = VirtualStickState.NO_POINTER;
        }
        if (pointer == aimPointer) {
            aimPointer = VirtualStickState.NO_POINTER;
        }
        binding.clear();
    }

    private void maybeReleaseFire(float radial) {
        if (preferences.aimScheme() != TouchPreferences.AimScheme.FLICK || !releaseToFireItem()) {
            return;
        }
        if (radial > TouchConfig.SCHEME_B_CANCEL_RADIUS) {
            fireLatched = true;
        }
    }

    private void refreshStickIntent() {
        stickJump = false;
        stickCrouch = false;
        stickFire = false;
        if (movePointer != VirtualStickState.NO_POINTER) {
            Binding move = bindings[movePointer];
            if (view == SurveillanceView.DRONE) {
                if (move.outputY > TouchConfig.DRONE_VERTICAL_THRESHOLD) {
                    stickJump = true;
                } else if (move.outputY < -TouchConfig.DRONE_VERTICAL_THRESHOLD) {
                    stickCrouch = true;
                }
            }
            // Still holding, but this view does not map the axis. Drop a pulse that belonged
            // to drone flight so leaving the drone cannot jump or drop the body.
            if (!stickJump) {
                stickJumpPulse = false;
            }
            if (!stickCrouch) {
                stickCrouchPulse = false;
            }
        }
        if (aimPointer != VirtualStickState.NO_POINTER) {
            Binding aim = bindings[aimPointer];
            if (aim.radial >= TouchConfig.AIM_DEADZONE) {
                float dx = aim.knobX - aim.originX;
                float dy = aim.knobY - aim.originY;
                if (dx != 0f || dy != 0f) {
                    aimAngle = Angles.ofVector(dx, dy);
                }
            }
            stickFire = stickShouldFire(aim.radial);
            if (!stickFire) {
                stickFirePulse = false;
            }
        }
        if (stickJump) {
            stickJumpPulse = true;
        }
        if (stickCrouch) {
            stickCrouchPulse = true;
        }
        if (stickFire) {
            stickFirePulse = true;
        }
    }

    private boolean stickShouldFire(float radial) {
        if (preferences.aimScheme() == TouchPreferences.AimScheme.FLICK
            && item == CombatItem.GUN
            && fireMode == FireMode.AUTO
            && radial >= TouchConfig.SCHEME_B_AUTO_FIRE_THRESHOLD) {
            return true;
        }
        return preferences.aimScheme() == TouchPreferences.AimScheme.STICK
            && preferences.outerRingFire()
            && item == CombatItem.GUN
            && radial >= TouchConfig.FIRE_RING_THRESHOLD;
    }

    private boolean releaseToFireItem() {
        if (item == CombatItem.THROWABLE || item == CombatItem.MELEE) {
            return true;
        }
        return item == CombatItem.GUN && fireMode.requiresTriggerRelease();
    }

    private TouchZone claim(float x, float y) {
        TouchZone button = null;
        TouchZone move = null;
        TouchZone aim = null;
        for (int i = 0; i < zones.size(); i++) {
            TouchZone zone = zones.get(i);
            if (!zone.contains(x, y)) {
                continue;
            }
            switch (zone.kind()) {
                case BUTTON -> {
                    if (button == null || zone.action().ordinal() < button.action().ordinal()) {
                        button = zone;
                    }
                }
                case MOVE -> {
                    if (move == null) {
                        move = zone;
                    }
                }
                case AIM -> {
                    if (aim == null) {
                        aim = zone;
                    }
                }
                default -> {
                }
            }
        }
        if (button != null) {
            return button;
        }
        if (move != null) {
            return move;
        }
        return aim;
    }

    private void pressButton(TouchAction action) {
        switch (action) {
            case FIRE -> {
                fireHeld = true;
                fireLatched = true;
            }
            case JUMP -> {
                jumpHeld = true;
                jumpLatched = true;
            }
            case JETPACK -> {
                jetpackHeld = true;
                jetpackLatched = true;
            }
            case CROUCH -> {
                crouchHeld = true;
                crouchLatched = true;
            }
            case ADS -> {
                adsHeld = true;
                adsLatched = true;
                if (preferences.adsMode() == TouchPreferences.AdsMode.TOGGLE) {
                    adsEngaged = !adsEngaged;
                }
            }
            case SLOT_1, SLOT_2, SLOT_3, SLOT_4, SLOT_5 -> offer(slotEdges, action.slotNumber());
            case GADGET_Q -> offer(gadgetEdges, PacketPlayerInput.GADGET_Q_PRESS);
            case GADGET_E -> offer(gadgetEdges, PacketPlayerInput.GADGET_E_PRESS);
            case USE -> offer(useEdges, PacketPlayerInput.USE_PRESS);
            case VIEW_CYCLE -> offer(viewEdges, PacketPlayerInput.VIEW_CYCLE);
            case EXIT_VIEW -> offer(viewEdges, PacketPlayerInput.VIEW_EXIT);
            case CHAT, QUICK_CHAT, LOADOUT, PAUSE -> offerAction(action);
            default -> {
            }
        }
    }

    private void releaseButton(TouchAction action) {
        switch (action) {
            case FIRE -> fireHeld = false;
            case JUMP -> jumpHeld = false;
            case JETPACK -> jetpackHeld = false;
            case CROUCH -> crouchHeld = false;
            case ADS -> adsHeld = false;
            default -> {
            }
        }
    }

    private void clearLatches() {
        fireLatched = false;
        jumpLatched = false;
        crouchLatched = false;
        jetpackLatched = false;
        adsLatched = false;
    }

    private static void offer(ArrayDeque<Integer> queue, int edge) {
        if (queue.size() >= EDGE_QUEUE_LIMIT) {
            return;
        }
        queue.offer(edge);
    }

    private void offerAction(TouchAction action) {
        if (uiEdges.size() >= EDGE_QUEUE_LIMIT) {
            return;
        }
        uiEdges.offer(action);
    }

    private static int poll(ArrayDeque<Integer> queue, int empty) {
        Integer edge = queue.poll();
        return edge == null ? empty : edge;
    }

    private void updateDeflection(Binding binding) {
        float dx = binding.x - binding.originX;
        float dy = binding.y - binding.originY;
        if (!(binding.radius > 0f) || !Float.isFinite(dx) || !Float.isFinite(dy)) {
            binding.knobX = binding.originX;
            binding.knobY = binding.originY;
            binding.outputX = 0f;
            binding.outputY = 0f;
            binding.radial = 0f;
            return;
        }
        float distance = (float) Math.hypot(dx, dy);
        float radial = distance / binding.radius;
        if (radial > 1f) {
            float scale = 1f / radial;
            dx *= scale;
            dy *= scale;
            radial = 1f;
        }
        binding.knobX = binding.originX + dx;
        binding.knobY = binding.originY + dy;
        binding.radial = radial;
        float deadzone = binding.kind == TouchZone.Kind.AIM
            ? TouchConfig.AIM_DEADZONE
            : TouchConfig.MOVE_DEADZONE;
        if (radial < deadzone) {
            binding.outputX = 0f;
            binding.outputY = 0f;
            return;
        }
        binding.outputX = clampUnit(dx / binding.radius);
        binding.outputY = clampUnit(dy / binding.radius);
    }

    private boolean anyActive() {
        for (Binding binding : bindings) {
            if (binding.active) {
                return true;
            }
        }
        return false;
    }

    private static boolean validPointer(int pointer) {
        return pointer >= TouchConfig.MIN_POINTER && pointer <= TouchConfig.MAX_POINTER;
    }

    private static float sanitizeAngle(float degrees) {
        return Float.isFinite(degrees) ? Angles.wrap(degrees) : 0f;
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, value));
    }

    private static final class Binding {
        private boolean active;
        private TouchZone.Kind kind = TouchZone.Kind.BUTTON;
        private TouchAction action;
        private float originX;
        private float originY;
        private float x;
        private float y;
        private float radius;
        private float knobX;
        private float knobY;
        private float outputX;
        private float outputY;
        private float radial;

        private void clear() {
            active = false;
            action = null;
            outputX = 0f;
            outputY = 0f;
            radial = 0f;
        }

        private VirtualStickState toState(int pointer) {
            return new VirtualStickState(
                true,
                pointer,
                originX,
                originY,
                knobX,
                knobY,
                outputX,
                outputY,
                radial,
                radius);
        }
    }
}
