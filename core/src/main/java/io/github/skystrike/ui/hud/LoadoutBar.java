package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.HudLoadoutView;
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
 */
final class LoadoutBar {

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    private float x;
    private float y;
    private float slotWidth;
    private float slotHeight;
    private float slotGap;
    private float gadgetWidth;
    private float scale;

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

    private float slotX(int index) {
        return x + index * (slotWidth + slotGap);
    }

    private float gadgetX(int index) {
        return x + PlayerLoadout.SLOT_COUNT * (slotWidth + slotGap)
            + slotGap + index * (gadgetWidth + slotGap);
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
            box(shapes, boxX, slotWidth, slot.active(), slot.filled());

            // The reload sweep belongs to the slot being reloaded, which is always the active
            // one: PlayerLoadout.startReload only ever arms the weapon in the hands.
            if (slot.active() && reload > 0f) {
                shapes.setColor(theme.reloadSweep);
                shapes.rect(boxX, y, slotWidth * reload, slotHeight);
            }

            // A thin magazine strip under a gun slot, so fullness reads without the numbers.
            if (slot.magazineSize() > 0) {
                float stripHeight = Math.max(1f, 2f * scale);
                shapes.setColor(theme.barTrack);
                shapes.rect(boxX, y, slotWidth, stripHeight);
                shapes.setColor(slot.dry() ? theme.dryWeapon : theme.magazineBar);
                shapes.rect(boxX, y, slotWidth * slot.magazineFraction(), stripHeight);
            }

            // The quick-swap origin: where a second tap of 3 (or of the melee key) returns to.
            if (slot.quickSwapOrigin()) {
                float markerHeight = Math.max(2f, 3f * scale);
                shapes.setColor(theme.quickSwapMarker);
                shapes.rect(boxX, y + slotHeight - markerHeight, slotWidth, markerHeight);
            }
        }

        List<HudLoadoutView.GadgetView> gadgets = HudLoadoutView.gadgets(loadout);
        for (int i = 0; i < gadgets.size(); i++) {
            HudLoadoutView.GadgetView gadget = gadgets.get(i);
            float boxX = gadgetX(i);
            box(shapes, boxX, gadgetWidth, gadget.active(), gadget.equipped());
            if (gadget.equipped() && gadget.maxDurability() > 0f) {
                float stripHeight = Math.max(1f, 2f * scale);
                shapes.setColor(theme.barTrack);
                shapes.rect(boxX, y, gadgetWidth, stripHeight);
                shapes.setColor(gadget.broken() ? theme.gadgetBroken : theme.gadgetOn);
                shapes.rect(boxX, y, gadgetWidth * gadget.durabilityFraction(), stripHeight);
            }
        }
    }

    private void box(ShapeRenderer shapes, float boxX, float width, boolean active, boolean filled) {
        shapes.setColor(active ? theme.slotFillActive : theme.slotFill);
        shapes.rect(boxX, y, width, slotHeight);
        float edge = Math.max(1f, 1.5f * scale);
        shapes.setColor(active ? theme.slotEdgeActive : theme.slotEdgeEmpty);
        shapes.rect(boxX, y + slotHeight - edge, width, edge);
        if (!filled) {
            // An empty slot is outlined all round rather than tinted, so "nothing here" never
            // reads as "something here, dimmed".
            shapes.rect(boxX, y, width, edge);
            shapes.rect(boxX, y, edge, slotHeight);
            shapes.rect(boxX + width - edge, y, edge, slotHeight);
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
            float keyBaseline = y + slotHeight - pad * 0.5f;

            font.setColor(theme.text(slot.active() ? theme.textAccent : theme.textDim));
            font.draw(batch, String.valueOf(slot.slot()), boxX + pad, keyBaseline);

            font.setColor(theme.text(slot.filled() ? theme.textPrimary : theme.textDim));
            drawClipped(batch, font, slot.label(), boxX + pad, keyBaseline - lineHeight * 0.85f,
                slotWidth - pad * 2f);

            Color countColor = slot.dry() ? theme.dryWeapon
                : slot.active() ? theme.textPrimary : theme.textDim;
            font.setColor(theme.text(countColor));
            measurer.setText(font, slot.countText());
            font.draw(batch, slot.countText(),
                boxX + slotWidth - pad - measurer.width, y + pad + lineHeight * 0.1f);
        }

        List<HudLoadoutView.GadgetView> gadgets = HudLoadoutView.gadgets(loadout);
        for (int i = 0; i < gadgets.size(); i++) {
            HudLoadoutView.GadgetView gadget = gadgets.get(i);
            float boxX = gadgetX(i);
            float keyBaseline = y + slotHeight - pad * 0.5f;

            font.setColor(theme.text(gadget.active() ? theme.textAccent : theme.textDim));
            font.draw(batch, gadget.key(), boxX + pad, keyBaseline);

            font.setColor(theme.text(gadget.equipped() ? theme.textPrimary : theme.textDim));
            drawClipped(batch, font, gadget.label(), boxX + pad,
                keyBaseline - lineHeight * 0.85f, gadgetWidth - pad * 2f);

            Color statusColor = gadget.broken() ? theme.gadgetBroken
                : gadget.active() ? theme.gadgetOn : theme.textDim;
            font.setColor(theme.text(statusColor));
            measurer.setText(font, gadget.statusText());
            font.draw(batch, gadget.statusText(),
                boxX + gadgetWidth - pad - measurer.width, y + pad + lineHeight * 0.1f);
        }

        // Two transient captions above the bar: what is reloading, and where a swap came from.
        float captionY = y + slotHeight + lineHeight;
        String reloadText = HudLoadoutView.reloadText(loadout);
        if (!reloadText.isEmpty()) {
            font.setColor(theme.text(theme.textWarning));
            measurer.setText(font, reloadText);
            font.draw(batch, reloadText, x + totalWidth() - measurer.width, captionY);
            captionY += lineHeight;
        }
        String swapText = HudLoadoutView.quickSwapText(loadout);
        if (!swapText.isEmpty()) {
            font.setColor(theme.text(theme.quickSwapMarker));
            measurer.setText(font, swapText);
            font.draw(batch, swapText, x + totalWidth() - measurer.width, captionY);
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
