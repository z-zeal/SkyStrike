package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.HudLoadoutView;
import io.github.skystrike.shared.hud.TouchHudLayout;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.map.Rect;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.List;

/**
 * The always-visible half of the loadout HUD: five weapon slots and the two gadget slots,
 * bottom right (playable build plan M4 §5).
 *
 * <p>Everything drawn here comes from {@link HudLoadoutView} reading the <b>predicted</b>
 * {@link PlayerLoadout} in {@link HudFrame}. That is the milestone's gate: ammo, the reload
 * sweep, the quick-swap origin marker and the utility counts cannot disagree with prediction,
 * because there is no second place for them to come from. The slot number drawn in the corner
 * of each box is the key that selects it, so the bar also documents the controls.
 *
 * <p>Per slot: the key, the name of what is in it, and its count — {@code 24/90} for a gun,
 * {@code x2} for a throwable, {@code -} for melee or an empty slot. The active slot is lit and
 * outlined; the slot a tap-swap would bounce back to carries a marker strip, which is the only
 * on-screen trace of the quick-swap origin the loadout model tracks.
 *
 * <p>On a touch device the same boxes are the touch layout's slot and gadget buttons, so what is
 * drawn sits under the thumb that presses it. {@link #placeTouch} puts them there; without it the
 * desktop row stands.
 */
final class LoadoutBar {

    private static final TouchAction[] SLOT_ACTIONS = {
        TouchAction.SLOT_1, TouchAction.SLOT_2, TouchAction.SLOT_3, TouchAction.SLOT_4, TouchAction.SLOT_5
    };
    private static final TouchAction[] GADGET_ACTIONS = {TouchAction.GADGET_Q, TouchAction.GADGET_E};

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    private float x;
    private float y;
    private float slotWidth;
    private float slotHeight;
    private float slotGap;
    private float gadgetWidth;
    private float scale;

    /** Touch boxes for slots 1–5 and for Q and E, or {@code null} for the desktop row. */
    private Rect[] touchSlots;
    private Rect[] touchGadgets;

    LoadoutBar(HudTheme theme) {
        this.theme = theme;
    }

    /** Right-aligned along the bottom edge: five slots, a gap, then Q and E. */
    void layout(int screenWidth, int screenHeight, float scale, float bottomInset) {
        this.scale = scale;
        this.slotWidth = theme.slotWidth * scale;
        this.slotHeight = theme.slotHeight * scale;
        this.slotGap = theme.slotGap * scale;
        this.gadgetWidth = theme.gadgetSlotWidth * scale;
        float margin = theme.margin * scale;
        this.y = bottomInset + margin;
        this.x = screenWidth - margin - totalWidth();
    }

    float totalWidth() {
        return slotWidth * PlayerLoadout.SLOT_COUNT
            + slotGap * (PlayerLoadout.SLOT_COUNT - 1)
            + slotGap * 2f
            + gadgetWidth * 2f
            + slotGap;
    }

    /**
     * Places the dock on a touch layout's slot and gadget boxes, slot 1 leftmost. Returns false,
     * and leaves the dock to the caller to hide, when the layout lacks any of the seven: a partial
     * row would read as a broken one.
     */
    boolean placeTouch(TouchHudLayout layout) {
        Rect[] slots = new Rect[SLOT_ACTIONS.length];
        Rect[] gadgets = new Rect[GADGET_ACTIONS.length];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = layout.hit(SLOT_ACTIONS[i]);
            if (slots[i] == null) {
                clearTouch();
                return false;
            }
        }
        for (int i = 0; i < gadgets.length; i++) {
            gadgets[i] = layout.hit(GADGET_ACTIONS[i]);
            if (gadgets[i] == null) {
                clearTouch();
                return false;
            }
        }
        touchSlots = slots;
        touchGadgets = gadgets;
        return true;
    }

    /** Returns the dock to the desktop row. */
    void clearTouch() {
        touchSlots = null;
        touchGadgets = null;
    }

    private float slotX(int index) {
        return touchSlots != null ? touchSlots[index].x() : x + index * (slotWidth + slotGap);
    }

    private float slotY(int index) {
        return touchSlots != null ? touchSlots[index].y() : y;
    }

    private float slotW(int index) {
        return touchSlots != null ? touchSlots[index].width() : slotWidth;
    }

    private float slotH(int index) {
        return touchSlots != null ? touchSlots[index].height() : slotHeight;
    }

    private float gadgetX(int index) {
        if (touchGadgets != null) {
            return touchGadgets[index].x();
        }
        return x + PlayerLoadout.SLOT_COUNT * (slotWidth + slotGap)
            + slotGap + index * (gadgetWidth + slotGap);
    }

    private float gadgetY(int index) {
        return touchGadgets != null ? touchGadgets[index].y() : y;
    }

    private float gadgetW(int index) {
        return touchGadgets != null ? touchGadgets[index].width() : gadgetWidth;
    }

    private float gadgetH(int index) {
        return touchGadgets != null ? touchGadgets[index].height() : slotHeight;
    }

    /** The dock's top edge, which the captions sit above. */
    private float dockTop() {
        if (touchSlots == null) {
            return y + slotHeight;
        }
        float top = 0f;
        for (Rect slot : touchSlots) {
            top = Math.max(top, slot.top());
        }
        return top;
    }

    /** The dock's right edge, which the captions align to. */
    private float dockRight() {
        if (touchSlots == null) {
            return x + totalWidth();
        }
        float right = 0f;
        for (Rect slot : touchSlots) {
            right = Math.max(right, slot.right());
        }
        for (Rect gadget : touchGadgets) {
            right = Math.max(right, gadget.right());
        }
        return right;
    }

    void drawShapes(ShapeRenderer shapes, HudFrame frame) {
        PlayerLoadout loadout = frame.loadout();
        if (loadout == null) {
            return;
        }
        List<HudLoadoutView.SlotView> slots = HudLoadoutView.slots(loadout);
        float reload = HudLoadoutView.reloadProgress(loadout);

        for (int i = 0; i < slots.size(); i++) {
            HudLoadoutView.SlotView slot = slots.get(i);
            float boxX = slotX(i);
            float boxY = slotY(i);
            float boxW = slotW(i);
            float boxH = slotH(i);
            box(shapes, boxX, boxY, boxW, boxH, slot.active(), slot.filled());

            // The reload sweep belongs to the slot being reloaded, which is always the active
            // one: PlayerLoadout.startReload only ever arms the weapon in the hands.
            if (slot.active() && reload > 0f) {
                shapes.setColor(theme.reloadSweep);
                shapes.rect(boxX, boxY, boxW * reload, boxH);
            }

            // A thin magazine strip under a gun slot, so fullness reads without the numbers.
            if (slot.magazineSize() > 0) {
                float stripHeight = Math.max(1f, 2f * scale);
                shapes.setColor(theme.barTrack);
                shapes.rect(boxX, boxY, boxW, stripHeight);
                shapes.setColor(slot.dry() ? theme.dryWeapon : theme.magazineBar);
                shapes.rect(boxX, boxY, boxW * slot.magazineFraction(), stripHeight);
            }

            // The quick-swap origin: where a second tap of 3 (or of the melee key) returns to.
            if (slot.quickSwapOrigin()) {
                float markerHeight = Math.max(2f, 3f * scale);
                shapes.setColor(theme.quickSwapMarker);
                shapes.rect(boxX, boxY + boxH - markerHeight, boxW, markerHeight);
            }
        }

        List<HudLoadoutView.GadgetView> gadgets = HudLoadoutView.gadgets(loadout);
        for (int i = 0; i < gadgets.size(); i++) {
            HudLoadoutView.GadgetView gadget = gadgets.get(i);
            float boxX = gadgetX(i);
            float boxY = gadgetY(i);
            float boxW = gadgetW(i);
            box(shapes, boxX, boxY, boxW, gadgetH(i), gadget.active(), gadget.equipped());
            if (gadget.equipped() && gadget.maxDurability() > 0f) {
                float stripHeight = Math.max(1f, 2f * scale);
                shapes.setColor(theme.barTrack);
                shapes.rect(boxX, boxY, boxW, stripHeight);
                shapes.setColor(gadget.broken() ? theme.gadgetBroken : theme.gadgetOn);
                shapes.rect(boxX, boxY, boxW * gadget.durabilityFraction(), stripHeight);
            }
        }
    }

    private void box(
            ShapeRenderer shapes, float boxX, float boxY, float width, float height,
            boolean active, boolean filled) {
        shapes.setColor(active ? theme.slotFillActive : theme.slotFill);
        shapes.rect(boxX, boxY, width, height);
        float edge = Math.max(1f, 1.5f * scale);
        shapes.setColor(active ? theme.slotEdgeActive : theme.slotEdgeEmpty);
        shapes.rect(boxX, boxY + height - edge, width, edge);
        if (!filled) {
            // An empty slot is outlined all round rather than tinted, so "nothing here" never
            // reads as "something here, dimmed".
            shapes.rect(boxX, boxY, width, edge);
            shapes.rect(boxX, boxY, edge, height);
            shapes.rect(boxX + width - edge, boxY, edge, height);
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        PlayerLoadout loadout = frame.loadout();
        if (loadout == null) {
            return;
        }
        float pad = theme.innerPadding * scale * 0.5f;
        float lineHeight = font.getLineHeight();
        List<HudLoadoutView.SlotView> slots = HudLoadoutView.slots(loadout);

        for (int i = 0; i < slots.size(); i++) {
            HudLoadoutView.SlotView slot = slots.get(i);
            float boxX = slotX(i);
            float boxY = slotY(i);
            float boxW = slotW(i);
            float boxH = slotH(i);
            float keyBaseline = boxY + boxH - pad * 0.5f;

            font.setColor(theme.text(slot.active() ? theme.textAccent : theme.textDim));
            font.draw(batch, String.valueOf(slot.slot()), boxX + pad, keyBaseline);

            font.setColor(theme.text(slot.filled() ? theme.textPrimary : theme.textDim));
            drawClipped(batch, font, slot.label(), boxX + pad, keyBaseline - lineHeight * 0.85f,
                boxW - pad * 2f);

            Color countColor = slot.dry() ? theme.dryWeapon
                : slot.active() ? theme.textPrimary : theme.textDim;
            font.setColor(theme.text(countColor));
            measurer.setText(font, slot.countText());
            font.draw(batch, slot.countText(),
                boxX + boxW - pad - measurer.width, boxY + pad + lineHeight * 0.1f);
        }

        List<HudLoadoutView.GadgetView> gadgets = HudLoadoutView.gadgets(loadout);
        for (int i = 0; i < gadgets.size(); i++) {
            HudLoadoutView.GadgetView gadget = gadgets.get(i);
            float boxX = gadgetX(i);
            float boxY = gadgetY(i);
            float boxW = gadgetW(i);
            float keyBaseline = boxY + gadgetH(i) - pad * 0.5f;

            font.setColor(theme.text(gadget.active() ? theme.textAccent : theme.textDim));
            font.draw(batch, gadget.key(), boxX + pad, keyBaseline);

            font.setColor(theme.text(gadget.equipped() ? theme.textPrimary : theme.textDim));
            drawClipped(batch, font, gadget.label(), boxX + pad,
                keyBaseline - lineHeight * 0.85f, boxW - pad * 2f);

            Color statusColor = gadget.broken() ? theme.gadgetBroken
                : gadget.active() ? theme.gadgetOn : theme.textDim;
            font.setColor(theme.text(statusColor));
            measurer.setText(font, gadget.statusText());
            font.draw(batch, gadget.statusText(),
                boxX + boxW - pad - measurer.width, boxY + pad + lineHeight * 0.1f);
        }

        // Two transient captions above the bar: what is reloading, and where a swap came from.
        float captionY = dockTop() + lineHeight;
        float captionRight = dockRight();
        String reloadText = HudLoadoutView.reloadText(loadout);
        if (!reloadText.isEmpty()) {
            font.setColor(theme.text(theme.textWarning));
            measurer.setText(font, reloadText);
            font.draw(batch, reloadText, captionRight - measurer.width, captionY);
            captionY += lineHeight;
        }
        String swapText = HudLoadoutView.quickSwapText(loadout);
        if (!swapText.isEmpty()) {
            font.setColor(theme.text(theme.quickSwapMarker));
            measurer.setText(font, swapText);
            font.draw(batch, swapText, captionRight - measurer.width, captionY);
        }
    }

    /**
     * Slot labels are weapon names, which run long ("Spectre Vanguard Mk II"); a slot box is
     * 66 design pixels wide. Trim to what fits rather than letting a name run into its
     * neighbour.
     */
    private void drawClipped(
            SpriteBatch batch, BitmapFont font, String text, float textX, float baseline,
            float maxWidth) {
        String drawn = text == null ? "" : text;
        measurer.setText(font, drawn);
        if (measurer.width <= maxWidth) {
            font.draw(batch, drawn, textX, baseline);
            return;
        }
        while (drawn.length() > 1) {
            drawn = drawn.substring(0, drawn.length() - 1);
            measurer.setText(font, drawn + ".");
            if (measurer.width <= maxWidth) {
                break;
            }
        }
        font.draw(batch, drawn + ".", textX, baseline);
    }
}
