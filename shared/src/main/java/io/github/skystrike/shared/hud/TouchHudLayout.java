package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.config.TouchConfig;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.input.TouchPreferences;
import io.github.skystrike.shared.input.TouchZone;
import io.github.skystrike.shared.map.Rect;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phone and tablet touch HUD geometry (Android plan §4, decision AN4).
 *
 * <p>Desktop layout is not this class. On a touch screen the thumbs occupy both bottom corners,
 * so the loadout dock moves to the gap between them, vitals leave the bottom-left, and the
 * action buttons sit in the aim-hand corner. Every interactive hit box is at least 48 dp, with
 * at least 6 dp between neighbours. Safe insets are added to every outer margin; they are
 * physical pixels, the same unit {@code SafeAreaInsets} publishes.
 *
 * <p>The movement stick floats inside its zone unless the preference pins it. The aim stick's
 * idle ring is anchored here; while a finger is down the control model floats that ring at the
 * touch, because the aim zone is the whole half of the screen and a fixed corner origin would
 * snap aim. Left-handed mode mirrors the thumb controls (sticks, crouch, action arc, dock) and
 * leaves the top utility bar and the informational widgets where the plan drew them — those
 * sit above thumb reach on both sides. Tablet vitals, which sit above the movement stick, mirror
 * with that stick.
 *
 * <p>Button scale is a request. If the requested scale cannot keep the 48 dp floor and the 6 dp
 * gaps, the layout steps the scale down until it fits or reports {@link #fitsTouchRules()}
 * false. Opacity is carried through for the drawer; it does not move boxes.
 */
public final class TouchHudLayout {

    /**
     * Which contextual buttons the match wants. Absent flags omit the button rather than leaving
     * a dead target. Exit wins over use: a piloting player is not standing at a door.
     */
    public record TouchHudContext(
        boolean showUse,
        boolean showExitView,
        boolean showViewCycle,
        boolean showSurveillanceBanner,
        boolean showDebugPanel
    ) {
        public static TouchHudContext none() {
            return new TouchHudContext(false, false, false, false, false);
        }
    }

    /** Phone or tablet. Desktop is not a touch form factor; callers map it before asking. */
    public enum TouchFormFactor {
        PHONE,
        TABLET
    }

    private final float width;
    private final float height;
    private final float density;
    private final TouchFormFactor formFactor;
    private final float appliedScale;
    private final float buttonOpacity;
    private final boolean leftHanded;
    private final boolean fits;
    private final Map<TouchAction, Rect> hits;
    private final Rect moveStick;
    private final Rect aimStick;
    private final float moveAnchorX;
    private final float moveAnchorY;
    private final float aimAnchorX;
    private final float aimAnchorY;
    private final float stickRadius;
    private final Rect moveZone;
    private final Rect aimZone;
    private final Rect minimap;
    private final Rect vitals;
    private final Rect killFeed;
    private final Rect passiveChat;
    private final Rect surveillanceBanner;
    private final Rect gadgetStrip;
    private final Rect debugPanel;

    private TouchHudLayout(
        float width,
        float height,
        float density,
        TouchFormFactor formFactor,
        float appliedScale,
        float buttonOpacity,
        boolean leftHanded,
        boolean fits,
        Map<TouchAction, Rect> hits,
        Rect moveStick,
        Rect aimStick,
        float moveAnchorX,
        float moveAnchorY,
        float aimAnchorX,
        float aimAnchorY,
        float stickRadius,
        Rect moveZone,
        Rect aimZone,
        Rect minimap,
        Rect vitals,
        Rect killFeed,
        Rect passiveChat,
        Rect surveillanceBanner,
        Rect gadgetStrip,
        Rect debugPanel
    ) {
        this.width = width;
        this.height = height;
        this.density = density;
        this.formFactor = formFactor;
        this.appliedScale = appliedScale;
        this.buttonOpacity = buttonOpacity;
        this.leftHanded = leftHanded;
        this.fits = fits;
        this.hits = hits;
        this.moveStick = moveStick;
        this.aimStick = aimStick;
        this.moveAnchorX = moveAnchorX;
        this.moveAnchorY = moveAnchorY;
        this.aimAnchorX = aimAnchorX;
        this.aimAnchorY = aimAnchorY;
        this.stickRadius = stickRadius;
        this.moveZone = moveZone;
        this.aimZone = aimZone;
        this.minimap = minimap;
        this.vitals = vitals;
        this.killFeed = killFeed;
        this.passiveChat = passiveChat;
        this.surveillanceBanner = surveillanceBanner;
        this.gadgetStrip = gadgetStrip;
        this.debugPanel = debugPanel;
    }

    /**
     * Classifies by shortest side, in dp. {@code >= 600} is a tablet, matching
     * {@code AndroidDeviceInfo}. Non-positive density is treated as 1.
     */
    public static TouchFormFactor classify(float widthPx, float heightPx, float density) {
        float d = TouchConfig.saneDensity(density);
        float shortest = Math.min(Math.abs(widthPx), Math.abs(heightPx)) / d;
        return shortest >= TouchConfig.TABLET_SHORTEST_WIDTH_DP
            ? TouchFormFactor.TABLET
            : TouchFormFactor.PHONE;
    }

    /** Layout with no safe insets and no contextual buttons. */
    public static TouchHudLayout layout(
        float widthPx,
        float heightPx,
        float density,
        TouchPreferences preferences
    ) {
        return layout(widthPx, heightPx, density, 0, 0, 0, 0, preferences, TouchHudContext.none());
    }

    /**
     * Builds one layout. Insets are physical pixels and must be non-negative; negatives are
     * treated as zero. A null preference or context uses the documented defaults.
     */
    public static TouchHudLayout layout(
        float widthPx,
        float heightPx,
        float density,
        int safeLeft,
        int safeRight,
        int safeTop,
        int safeBottom,
        TouchPreferences preferences,
        TouchHudContext context
    ) {
        TouchPreferences prefs = preferences == null ? TouchPreferences.defaults() : preferences.validated();
        TouchHudContext ctx = context == null ? TouchHudContext.none() : context;
        float d = TouchConfig.saneDensity(density);
        int left = Math.max(0, safeLeft);
        int right = Math.max(0, safeRight);
        int top = Math.max(0, safeTop);
        int bottom = Math.max(0, safeBottom);
        if (!Float.isFinite(widthPx) || !Float.isFinite(heightPx) || widthPx < 1f || heightPx < 1f
            || left + right >= widthPx || top + bottom >= heightPx) {
            return empty(widthPx, heightPx, d, prefs);
        }
        TouchFormFactor factor = switch (prefs.hudPreset()) {
            case PHONE -> TouchFormFactor.PHONE;
            case TABLET -> TouchFormFactor.TABLET;
            case AUTO -> classify(widthPx, heightPx, d);
        };
        float requested = prefs.buttonScale();
        int requestedHundredths = Math.round(requested * 100f);
        int minHundredths = Math.round(TouchConfig.BUTTON_SCALE_MIN * 100f);
        Placement best = null;
        for (int hundredths = requestedHundredths; hundredths >= minHundredths; hundredths -= 5) {
            Placement attempt = new Placement(
                widthPx, heightPx, d, factor, hundredths / 100f, prefs, ctx, left, right, top, bottom);
            attempt.place();
            best = attempt;
            if (attempt.fits) {
                break;
            }
        }
        return best == null ? empty(widthPx, heightPx, d, prefs) : best.freeze();
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float density() {
        return density;
    }

    public TouchFormFactor formFactor() {
        return formFactor;
    }

    /** The scale actually used. May be lower than the request when the request would overlap. */
    public float appliedButtonScale() {
        return appliedScale;
    }

    public float buttonOpacity() {
        return buttonOpacity;
    }

    public boolean leftHanded() {
        return leftHanded;
    }

    /**
     * True when every interactive box is on screen, at least 48 dp, and at least 6 dp from its
     * neighbours. Informational rects are not part of this promise.
     */
    public boolean fitsTouchRules() {
        return fits;
    }

    public Rect hit(TouchAction action) {
        return action == null ? null : hits.get(action);
    }

    public Rect moveStickBounds() {
        return moveStick;
    }

    public Rect aimStickBounds() {
        return aimStick;
    }

    public float moveAnchorX() {
        return moveAnchorX;
    }

    public float moveAnchorY() {
        return moveAnchorY;
    }

    public float aimAnchorX() {
        return aimAnchorX;
    }

    public float aimAnchorY() {
        return aimAnchorY;
    }

    public float stickRadius() {
        return stickRadius;
    }

    public Rect moveZone() {
        return moveZone;
    }

    public Rect aimZone() {
        return aimZone;
    }

    public Rect minimap() {
        return minimap;
    }

    public Rect vitals() {
        return vitals;
    }

    /** Provisional reservation to the right of the utility buttons. Not a hit target. */
    public Rect killFeed() {
        return killFeed;
    }

    public Rect passiveChat() {
        return passiveChat;
    }

    public Rect surveillanceBanner() {
        return surveillanceBanner;
    }

    public Rect gadgetStrip() {
        return gadgetStrip;
    }

    public Rect debugPanel() {
        return debugPanel;
    }

    /** Button hits plus the two stick bounds, in a stable order. */
    public List<Rect> interactiveBounds() {
        List<Rect> bounds = new ArrayList<>();
        if (moveStick != null) {
            bounds.add(moveStick);
        }
        if (aimStick != null) {
            bounds.add(aimStick);
        }
        for (TouchAction action : TouchAction.values()) {
            Rect hit = hits.get(action);
            if (hit != null) {
                bounds.add(hit);
            }
        }
        return List.copyOf(bounds);
    }

    /** Claim regions for {@code TouchControlModel}, buttons first. */
    public List<TouchZone> zones() {
        List<TouchZone> zones = new ArrayList<>();
        for (TouchAction action : TouchAction.values()) {
            Rect hit = hits.get(action);
            if (hit != null) {
                zones.add(TouchZone.button(action, hit));
            }
        }
        if (moveZone != null && stickRadius > 0f) {
            zones.add(TouchZone.stick(
                TouchZone.Kind.MOVE, moveZone, moveAnchorX, moveAnchorY, stickRadius));
        }
        if (aimZone != null && stickRadius > 0f) {
            zones.add(TouchZone.stick(
                TouchZone.Kind.AIM, aimZone, aimAnchorX, aimAnchorY, stickRadius));
        }
        return List.copyOf(zones);
    }

    private static TouchHudLayout empty(float width, float height, float density, TouchPreferences prefs) {
        return new TouchHudLayout(
            Float.isFinite(width) ? width : 0f,
            Float.isFinite(height) ? height : 0f,
            density,
            TouchFormFactor.PHONE,
            prefs.buttonScale(),
            prefs.buttonOpacity(),
            prefs.leftHanded(),
            false,
            Map.of(),
            null,
            null,
            0f,
            0f,
            0f,
            0f,
            0f,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    /** True when two interactive rects are at least {@code gap} apart, including diagonally. */
    static boolean separated(Rect a, Rect b, float gap) {
        if (a == null || b == null) {
            return true;
        }
        float xGap = axisGap(a.left(), a.right(), b.left(), b.right());
        float yGap = axisGap(a.bottom(), a.top(), b.bottom(), b.top());
        if (xGap < 0f && yGap < 0f) {
            return false;
        }
        if (xGap < 0f) {
            return yGap + 0.05f >= gap;
        }
        if (yGap < 0f) {
            return xGap + 0.05f >= gap;
        }
        return Math.hypot(xGap, yGap) + 0.05f >= gap;
    }

    private static float axisGap(float a0, float a1, float b0, float b1) {
        if (a1 <= b0) {
            return b0 - a1;
        }
        if (b1 <= a0) {
            return a0 - b1;
        }
        return -Math.min(a1 - b0, b1 - a0);
    }

    private static final class Placement {
        private final float width;
        private final float height;
        private final float density;
        private final TouchFormFactor formFactor;
        private final float scale;
        private final TouchPreferences prefs;
        private final TouchHudContext context;
        private final int safeLeft;
        private final int safeRight;
        private final int safeTop;
        private final int safeBottom;
        private final float pad;
        private final float gap;
        private final float contentLeft;
        private final float contentRight;
        private final float contentBottom;
        private final float contentTop;
        private final boolean tablet;

        private final Map<TouchAction, Rect> hits = new EnumMap<>(TouchAction.class);
        private Rect moveStick;
        private Rect aimStick;
        private float moveAnchorX;
        private float moveAnchorY;
        private float aimAnchorX;
        private float aimAnchorY;
        private float stickRadius;
        private Rect moveZone;
        private Rect aimZone;
        private Rect minimap;
        private Rect vitals;
        private Rect killFeed;
        private Rect passiveChat;
        private Rect surveillanceBanner;
        private Rect gadgetStrip;
        private Rect debugPanel;
        private boolean fits;

        private Placement(
            float width,
            float height,
            float density,
            TouchFormFactor formFactor,
            float scale,
            TouchPreferences prefs,
            TouchHudContext context,
            int safeLeft,
            int safeRight,
            int safeTop,
            int safeBottom
        ) {
            this.width = width;
            this.height = height;
            this.density = density;
            this.formFactor = formFactor;
            this.scale = scale;
            this.prefs = prefs;
            this.context = context;
            this.safeLeft = safeLeft;
            this.safeRight = safeRight;
            this.safeTop = safeTop;
            this.safeBottom = safeBottom;
            this.pad = TouchConfig.HUD_PAD_DP * density;
            this.gap = TouchConfig.MIN_TOUCH_GAP_DP * density;
            this.contentLeft = safeLeft + pad;
            this.contentRight = width - safeRight - pad;
            this.contentBottom = safeBottom + pad;
            this.contentTop = height - safeTop - pad;
            this.tablet = formFactor == TouchFormFactor.TABLET;
        }

        private void place() {
            if (contentRight - contentLeft < TouchConfig.MIN_TOUCH_TARGET_DP * density
                || contentTop - contentBottom < TouchConfig.MIN_TOUCH_TARGET_DP * density) {
                fits = false;
                return;
            }
            float fire = hit(TouchConfig.FIRE_BUTTON_DP);
            float jump = hit(TouchConfig.JUMP_BUTTON_DP);
            float jet = hit(TouchConfig.JETPACK_BUTTON_DP);
            float small = hit(TouchConfig.MIN_TOUCH_TARGET_DP);
            float slotPref = hit(tablet ? TouchConfig.TABLET_SLOT_WIDTH_DP : TouchConfig.PHONE_SLOT_WIDTH_DP);
            float slotH = hit(tablet ? TouchConfig.TABLET_SLOT_HEIGHT_DP : TouchConfig.PHONE_SLOT_HEIGHT_DP);
            int slots = context.showViewCycle() ? 8 : 7;
            float minSlot = TouchConfig.MIN_TOUCH_TARGET_DP * density;
            float minRadius = TouchConfig.STICK_RADIUS_MIN_DP * density;
            stickRadius = Math.max(TouchConfig.STICK_RADIUS_DP * density * scale, minRadius);

            while (stickRadius > minRadius + 0.1f && !dockFits(stickRadius, small, slots, minSlot)) {
                stickRadius = Math.max(minRadius, stickRadius - density);
            }
            anchor(stickRadius);
            float dockLeft = moveAnchorX + stickRadius + gap + small + gap;
            float dockRight = aimAnchorX - stickRadius - gap;
            float avail = dockRight - dockLeft;
            float slotW = slotPref;
            float neededAtFloor = slots * minSlot + (slots - 1) * gap;
            if (avail + 0.1f >= neededAtFloor) {
                float fitW = (avail - (slots - 1) * gap) / slots;
                slotW = Math.min(slotPref, Math.max(minSlot, fitW));
            } else {
                slotW = minSlot;
            }
            float needed = slots * slotW + (slots - 1) * gap;
            float dockX = dockLeft + Math.max(0f, avail - needed) / 2f;

            moveStick = circle(moveAnchorX, moveAnchorY, stickRadius);
            aimStick = circle(aimAnchorX, aimAnchorY, stickRadius);
            hits.put(TouchAction.CROUCH, new Rect(moveAnchorX + stickRadius + gap, contentBottom, small, small));

            float x = dockX;
            TouchAction[] dock = dockActions();
            for (TouchAction action : dock) {
                hits.put(action, new Rect(x, contentBottom, slotW, slotH));
                x += slotW + gap;
            }

            float colW = Math.max(fire, Math.max(jump, jet));
            float colRight = aimAnchorX - stickRadius - gap;
            float colLeft = colRight - colW;
            float jumpY = contentBottom + slotH + gap;
            float fireY = jumpY + jump + gap;
            float jetY = fireY + fire + gap;
            hits.put(TouchAction.JUMP, new Rect(colLeft + (colW - jump) / 2f, jumpY, jump, jump));
            hits.put(TouchAction.FIRE, new Rect(colLeft + (colW - fire) / 2f, fireY, fire, fire));
            hits.put(TouchAction.JETPACK, new Rect(colLeft + (colW - jet) / 2f, jetY, jet, jet));

            float utility = small;
            float utilityBottom = contentTop - utility;
            Rect adsAbove = new Rect(
                aimAnchorX + stickRadius - small, aimAnchorY + stickRadius + gap, small, small);
            Rect adsSide = new Rect(colLeft - gap - small, jetY + (jet - small) / 2f, small, small);
            boolean adsFitsAbove = adsAbove.top() <= utilityBottom + 0.1f && adsAbove.x() >= contentLeft;
            hits.put(TouchAction.ADS, adsFitsAbove ? adsAbove : adsSide);

            if (context.showExitView() || context.showUse()) {
                TouchAction contextual = context.showExitView() ? TouchAction.EXIT_VIEW : TouchAction.USE;
                Rect besideJet = new Rect(colLeft - gap - small, jetY + (jet - small) / 2f, small, small);
                Rect besideFire = new Rect(colLeft - gap - small, fireY + (fire - small) / 2f, small, small);
                Rect chosen = overlaps(besideJet, hits.get(TouchAction.ADS), gap)
                    || overlaps(besideJet, hits.get(TouchAction.JETPACK), gap)
                    ? besideFire : besideJet;
                if (chosen.x() < 0f) {
                    chosen = new Rect(contentLeft, fireY, small, small);
                }
                hits.put(contextual, chosen);
            }

            placeTopBar(utility);
            if (prefs.leftHanded()) {
                mirrorThumbControls();
            }
            placeInformational(utility);
            buildZones(utilityBottom);
            fits = interactiveOk(slots);
        }

        private boolean dockFits(float radius, float crouch, int slots, float minSlot) {
            anchor(radius);
            float dockLeft = moveAnchorX + radius + gap + crouch + gap;
            float dockRight = aimAnchorX - radius - gap;
            return dockRight - dockLeft + 0.1f >= slots * minSlot + (slots - 1) * gap;
        }

        private void anchor(float radius) {
            if (tablet) {
                moveAnchorX = safeLeft + TouchConfig.TABLET_STICK_CENTER_X_DP * density;
                moveAnchorY = safeBottom + TouchConfig.TABLET_STICK_CENTER_Y_DP * density;
                aimAnchorX = width - safeRight - TouchConfig.TABLET_STICK_CENTER_X_DP * density;
                aimAnchorY = safeBottom + TouchConfig.TABLET_STICK_CENTER_Y_DP * density;
            } else {
                moveAnchorX = contentLeft + radius;
                moveAnchorY = contentBottom + radius;
                aimAnchorX = contentRight - radius;
                aimAnchorY = contentBottom + radius;
            }
            if (contentRight - contentLeft >= 2f * radius && contentTop - contentBottom >= 2f * radius) {
                moveAnchorX = clamp(moveAnchorX, contentLeft + radius, contentRight - radius);
                aimAnchorX = clamp(aimAnchorX, contentLeft + radius, contentRight - radius);
                moveAnchorY = clamp(moveAnchorY, contentBottom + radius, contentTop - radius);
                aimAnchorY = clamp(aimAnchorY, contentBottom + radius, contentTop - radius);
            }
        }

        private void placeTopBar(float utility) {
            float killW = (tablet
                ? TouchConfig.TABLET_KILL_FEED_WIDTH_DP
                : TouchConfig.PHONE_KILL_FEED_WIDTH_DP) * density;
            killFeed = new Rect(contentRight - killW, contentTop - utility, killW, utility);
            float x = killFeed.x() - gap - utility;
            float y = contentTop - utility;
            for (TouchAction action : new TouchAction[] {
                TouchAction.PAUSE, TouchAction.LOADOUT, TouchAction.QUICK_CHAT, TouchAction.CHAT
            }) {
                hits.put(action, new Rect(x, y, utility, utility));
                x -= utility + gap;
            }
            float miniW = (tablet
                ? TouchConfig.TABLET_MINIMAP_WIDTH_DP
                : TouchConfig.PHONE_MINIMAP_WIDTH_DP) * density;
            float miniH = (tablet
                ? TouchConfig.TABLET_MINIMAP_HEIGHT_DP
                : TouchConfig.PHONE_MINIMAP_HEIGHT_DP) * density;
            minimap = new Rect(contentLeft, contentTop - miniH, miniW, miniH);
        }

        private void mirrorThumbControls() {
            float slotW = 0f;
            float slotH = 0f;
            Rect slot = hits.get(TouchAction.SLOT_1);
            if (slot != null) {
                slotW = slot.width();
                slotH = slot.height();
            }
            for (TouchAction action : TouchAction.values()) {
                Rect rect = hits.get(action);
                if (rect == null || isStationaryUtility(action) || isDock(action)) {
                    continue;
                }
                hits.put(action, mirror(rect));
            }
            moveAnchorX = width - moveAnchorX;
            aimAnchorX = width - aimAnchorX;
            moveStick = circle(moveAnchorX, moveAnchorY, stickRadius);
            aimStick = circle(aimAnchorX, aimAnchorY, stickRadius);
            // Re-flow the dock left-to-right in the mirrored gap. Mirroring each slot rect would
            // reverse 1..5, which is a reading-order change the plan does not ask for.
            Rect crouch = hits.get(TouchAction.CROUCH);
            if (slot == null || crouch == null || aimStick == null) {
                return;
            }
            float dockLeft = aimStick.right() + gap;
            float dockRight = crouch.x() - gap;
            TouchAction[] dock = dockActions();
            float needed = dock.length * slotW + (dock.length - 1) * gap;
            float avail = dockRight - dockLeft;
            float dockX = dockLeft + Math.max(0f, avail - needed) / 2f;
            float x = dockX;
            for (TouchAction action : dock) {
                hits.put(action, new Rect(x, contentBottom, slotW, slotH));
                x += slotW + gap;
            }
        }

        private void placeInformational(float utility) {
            Rect chat = hits.get(TouchAction.CHAT);
            if (!tablet || prefs.hudPreset() == TouchPreferences.HudPreset.PHONE) {
                float preferred = TouchConfig.PHONE_VITALS_WIDTH_DP * density;
                float vitalsH = TouchConfig.PHONE_VITALS_HEIGHT_DP * density;
                float leftLimit = minimap == null ? contentLeft : minimap.right() + gap;
                float rightLimit = chat == null ? contentRight : chat.x() - gap;
                float avail = rightLimit - leftLimit;
                float w = Math.min(preferred, Math.max(0f, avail));
                float x = leftLimit + Math.max(0f, avail - w) / 2f;
                vitals = w > 1f ? new Rect(x, contentTop - vitalsH, w, vitalsH) : null;
            } else {
                float w = TouchConfig.TABLET_VITALS_WIDTH_DP * density;
                float h = TouchConfig.TABLET_VITALS_HEIGHT_DP * density;
                float x = contentLeft;
                float y = safeBottom + TouchConfig.TABLET_VITALS_BOTTOM_DP * density;
                if (prefs.leftHanded()) {
                    x = width - contentLeft - w;
                }
                vitals = new Rect(x, y, w, h);
            }
            shrinkMinimapClearOfThumbs();
            int lines = tablet ? TouchConfig.TABLET_PASSIVE_CHAT_LINES : TouchConfig.PHONE_PASSIVE_CHAT_LINES;
            float stackCursor = minimap == null ? contentTop : minimap.y();
            if (context.showDebugPanel() && minimap != null) {
                float debugH = 72f * density;
                Rect debug = new Rect(minimap.x(), stackCursor - gap - debugH, minimap.width(), debugH);
                if (!overlapsAnyInteractive(debug) && debug.y() >= 0f) {
                    debugPanel = debug;
                    stackCursor = debug.y();
                }
            }
            if (minimap != null) {
                float chatH = lines * TouchConfig.PASSIVE_CHAT_LINE_DP * density;
                passiveChat = new Rect(minimap.x(), stackCursor - gap - chatH, minimap.width(), chatH);
                if (overlapsAnyInteractive(passiveChat) || passiveChat.y() < 0f) {
                    passiveChat = null;
                }
            }
            if (context.showSurveillanceBanner() && vitals != null) {
                float bannerH = 28f * density;
                Rect banner = new Rect(vitals.x(), vitals.y() - gap - bannerH, vitals.width(), bannerH);
                if (!overlapsAnyInteractive(banner) && banner.y() >= 0f) {
                    surveillanceBanner = banner;
                }
            }
            Rect q = hits.get(TouchAction.GADGET_Q);
            Rect e = hits.get(TouchAction.GADGET_E);
            if (q != null && e != null) {
                float stripH = (tablet ? 36f : 18f) * density;
                Rect strip = new Rect(q.x(), q.top() + gap, e.right() - q.x(), stripH);
                if (!overlapsAnyInteractive(strip)) {
                    gadgetStrip = strip;
                }
            }
            if (overlapsAnyInteractive(vitals)) {
                vitals = null;
            }
            if (overlapsAnyInteractive(killFeed)) {
                killFeed = null;
            }
        }

        private void shrinkMinimapClearOfThumbs() {
            if (minimap == null) {
                return;
            }
            float highestThumb = Float.NEGATIVE_INFINITY;
            boolean conflict = false;
            for (Rect box : interactiveThumbBoxes()) {
                if (box == null || !xOverlaps(minimap, box)) {
                    continue;
                }
                if (box.top() > minimap.y()) {
                    conflict = true;
                    highestThumb = Math.max(highestThumb, box.top());
                }
            }
            if (!conflict) {
                return;
            }
            float newY = highestThumb + gap;
            float newH = minimap.top() - newY;
            float floor = TouchConfig.MIN_TOUCH_TARGET_DP * density;
            if (newH >= floor) {
                minimap = new Rect(minimap.x(), newY, minimap.width(), newH);
            }
        }

        private void buildZones(float utilityBottom) {
            float moveBottom = safeBottom;
            float fractionTop = Math.min(height * TouchConfig.MOVE_ZONE_HEIGHT_FRACTION, utilityBottom);
            float moveTop = Math.max(fractionTop, moveStick.top());
            if (moveTop < moveBottom) {
                moveTop = moveBottom;
            }
            float moveLeft;
            float moveRight;
            float aimLeft;
            float aimRight;
            if (prefs.leftHanded()) {
                moveRight = width - safeRight;
                moveLeft = Math.min(
                    width * (1f - TouchConfig.MOVE_ZONE_WIDTH_FRACTION), moveStick.left());
                aimLeft = 0f;
                aimRight = Math.max(
                    width * (1f - TouchConfig.AIM_ZONE_LEFT_FRACTION), aimStick.right());
            } else {
                moveLeft = safeLeft;
                moveRight = Math.max(width * TouchConfig.MOVE_ZONE_WIDTH_FRACTION, moveStick.right());
                aimLeft = Math.min(width * TouchConfig.AIM_ZONE_LEFT_FRACTION, aimStick.left());
                aimRight = width;
            }
            moveZone = span(moveLeft, moveBottom, moveRight, moveTop);
            float aimTop = Math.max(utilityBottom, aimStick.top());
            aimZone = span(aimLeft, 0f, aimRight, aimTop);
        }

        private boolean interactiveOk(int slots) {
            if (moveStick == null || aimStick == null) {
                return false;
            }
            List<Rect> boxes = new ArrayList<>();
            boxes.add(moveStick);
            boxes.add(aimStick);
            for (TouchAction action : TouchAction.values()) {
                if (isStationaryUtility(action) || requiredThumb(action, slots)) {
                    if (hits.get(action) == null) {
                        return false;
                    }
                }
                Rect hit = hits.get(action);
                if (hit != null) {
                    boxes.add(hit);
                }
            }
            float min = TouchConfig.MIN_TOUCH_TARGET_DP * density - 0.5f;
            for (Rect box : boxes) {
                if (box.width() < min || box.height() < min) {
                    return false;
                }
                if (box.x() < -0.5f || box.y() < -0.5f
                    || box.right() > width + 0.5f || box.top() > height + 0.5f) {
                    return false;
                }
            }
            for (int i = 0; i < boxes.size(); i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    if (!separated(boxes.get(i), boxes.get(j), gap)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean requiredThumb(TouchAction action, int slots) {
            if (action == TouchAction.VIEW_CYCLE) {
                return slots == 8;
            }
            if (action == TouchAction.USE) {
                return context.showUse() && !context.showExitView();
            }
            if (action == TouchAction.EXIT_VIEW) {
                return context.showExitView();
            }
            return action == TouchAction.FIRE
                || action == TouchAction.JUMP
                || action == TouchAction.JETPACK
                || action == TouchAction.CROUCH
                || action == TouchAction.ADS
                || action.slotNumber() > 0
                || action == TouchAction.GADGET_Q
                || action == TouchAction.GADGET_E;
        }

        private List<Rect> interactiveThumbBoxes() {
            List<Rect> boxes = new ArrayList<>();
            boxes.add(moveStick);
            boxes.add(aimStick);
            for (TouchAction action : TouchAction.values()) {
                if (!isStationaryUtility(action)) {
                    boxes.add(hits.get(action));
                }
            }
            return boxes;
        }

        private boolean overlapsAnyInteractive(Rect rect) {
            if (rect == null) {
                return false;
            }
            if (overlaps(rect, moveStick, 0f) || overlaps(rect, aimStick, 0f)) {
                return true;
            }
            for (Rect hit : hits.values()) {
                if (overlaps(rect, hit, 0f)) {
                    return true;
                }
            }
            return false;
        }

        private TouchHudLayout freeze() {
            return new TouchHudLayout(
                width,
                height,
                density,
                formFactor,
                scale,
                prefs.buttonOpacity(),
                prefs.leftHanded(),
                fits,
                Map.copyOf(hits),
                moveStick,
                aimStick,
                moveAnchorX,
                moveAnchorY,
                aimAnchorX,
                aimAnchorY,
                stickRadius,
                moveZone,
                aimZone,
                minimap,
                vitals,
                killFeed,
                passiveChat,
                surveillanceBanner,
                gadgetStrip,
                debugPanel);
        }

        private float hit(float baseDp) {
            return Math.max(baseDp * density * scale, TouchConfig.MIN_TOUCH_TARGET_DP * density);
        }

        private Rect mirror(Rect rect) {
            return new Rect(width - rect.right(), rect.y(), rect.width(), rect.height());
        }

        private static boolean isStationaryUtility(TouchAction action) {
            return action == TouchAction.CHAT
                || action == TouchAction.QUICK_CHAT
                || action == TouchAction.LOADOUT
                || action == TouchAction.PAUSE;
        }

        private static boolean isDock(TouchAction action) {
            return action.slotNumber() > 0
                || action == TouchAction.GADGET_Q
                || action == TouchAction.GADGET_E
                || action == TouchAction.VIEW_CYCLE;
        }

        private TouchAction[] dockActions() {
            if (context.showViewCycle()) {
                return new TouchAction[] {
                    TouchAction.SLOT_1, TouchAction.SLOT_2, TouchAction.SLOT_3, TouchAction.SLOT_4,
                    TouchAction.SLOT_5, TouchAction.GADGET_Q, TouchAction.GADGET_E, TouchAction.VIEW_CYCLE
                };
            }
            return new TouchAction[] {
                TouchAction.SLOT_1, TouchAction.SLOT_2, TouchAction.SLOT_3, TouchAction.SLOT_4,
                TouchAction.SLOT_5, TouchAction.GADGET_Q, TouchAction.GADGET_E
            };
        }
    }

    private static Rect circle(float cx, float cy, float radius) {
        return new Rect(cx - radius, cy - radius, radius * 2f, radius * 2f);
    }

    private static Rect span(float left, float bottom, float right, float top) {
        float width = Math.max(0f, right - left);
        float height = Math.max(0f, top - bottom);
        return new Rect(left, bottom, width, height);
    }

    private static boolean overlaps(Rect a, Rect b, float gap) {
        return !separated(a, b, gap);
    }

    private static boolean xOverlaps(Rect a, Rect b) {
        return a != null && b != null && a.left() < b.right() && b.left() < a.right();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
