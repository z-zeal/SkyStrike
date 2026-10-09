package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.GadgetPanelModel;
import io.github.skystrike.shared.model.PlayerLoadout;
import java.util.ArrayList;
import java.util.List;

/**
 * The gadget panel: durability and cooldowns for the two gadget slots, bottom right, stacked
 * above the loadout bar (roadmap Phase 7 HUD, mechanics §7).
 *
 * <p>The loadout bar's 46-pixel gadget boxes already carry a wear strip and a short status. The
 * panel is the roomier companion: {@code 112/150} instead of a strip, {@code FRONT}/{@code REAR}
 * instead of {@code ON}, a countdown while a cooldown runs. Its words and numbers come from
 * {@link GadgetPanelModel}, which reads the same predicted {@code PlayerLoadout} the bar does, so
 * the two cannot disagree.
 *
 * <p>Layout is two passes. The first measures every string and derives the plate's width and
 * each row's height; the second places rows from the top down. The plate exists only while a
 * real gadget is carried — two empty slots are already the bar's "Empty" boxes.
 *
 * <p>Each gadget takes a name line; a gadget with a durability pool takes a second line with its
 * bar and count, and a running cooldown appears on that line beside the count.
 */
final class GadgetPanel {

    private static final float MIN_WIDTH_DESIGN = 150f;
    private static final float MIN_BAR_DESIGN = 60f;
    private static final float BAR_DESIGN_HEIGHT = 4f;

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    private float right;
    private float bottom;
    private float scale;

    GadgetPanel(HudTheme theme) {
        this.theme = theme;
    }

    /** Right-aligned; {@code bottomInset} is the height of the loadout bar it sits above. */
    void layout(int screenWidth, int screenHeight, float scale, float bottomInset) {
        this.scale = scale;
        this.right = screenWidth - theme.margin * scale;
        this.bottom = bottomInset;
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        Plate plate = plate(font, frame.loadout());
        if (plate == null) {
            return;
        }
        float left = right - plate.width;
        Color panel = theme.panelFill();
        shapes.setColor(panel.r, panel.g, panel.b, panel.a);
        shapes.rect(left, bottom, plate.width, plate.height);
        float edge = Math.max(1f, 1.5f * scale);
        shapes.setColor(theme.slotEdgeEmpty);
        shapes.rect(left, bottom + plate.height - edge, plate.width, edge);

        for (Row row : plate.rows) {
            if (!row.hasBar()) {
                continue;
            }
            shapes.setColor(theme.barTrack);
            shapes.rect(left + row.barX(), bottom + row.barY(), row.barWidth(), row.barHeight());
            Color fill = row.slot().broken() ? theme.gadgetBroken : theme.gadgetOn;
            shapes.setColor(fill.r, fill.g, fill.b, fill.a);
            shapes.rect(left + row.barX(), bottom + row.barY(),
                row.barWidth() * row.slot().durabilityFraction(), row.barHeight());
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        Plate plate = plate(font, frame.loadout());
        if (plate == null) {
            return;
        }
        float left = right - plate.width;
        float pad = plate.pad;
        for (Row row : plate.rows) {
            GadgetPanelModel.SlotPanel slot = row.slot();

            font.setColor(theme.text(theme.textAccent));
            font.draw(batch, slot.key(), left + pad, bottom + row.baselineA());

            font.setColor(theme.text(theme.textPrimary));
            font.draw(batch, slot.name(), left + row.nameX(), bottom + row.baselineA());

            font.setColor(theme.text(stateColor(slot)));
            font.draw(batch, slot.stateText(),
                left + plate.width - pad - row.stateWidth(), bottom + row.baselineA());

            if (!row.secondLine()) {
                continue;
            }
            // The second line reads right to left: durability count, then the cooldown beside it.
            float cursor = left + plate.width - pad;
            if (row.hasBar()) {
                font.setColor(theme.text(slot.broken() ? theme.gadgetBroken : theme.textPrimary));
                cursor -= row.durWidth();
                font.draw(batch, slot.durabilityText(), cursor, bottom + row.baselineB());
            }
            if (slot.onCooldown()) {
                font.setColor(theme.text(theme.textWarning));
                cursor -= (row.hasBar() ? theme.lineGap * scale : 0f) + row.cdWidth();
                font.draw(batch, slot.cooldownText(), cursor, bottom + row.baselineB());
            }
        }
    }

    private Color stateColor(GadgetPanelModel.SlotPanel slot) {
        if (slot.broken()) {
            return theme.gadgetBroken;
        }
        if (!slot.behavior().respondsToPress() || !slot.equipped()) {
            return theme.textDim;
        }
        return slot.active() ? theme.gadgetOn : theme.textDim;
    }

    /** Two-pass layout for this frame, or null when there is nothing to show. */
    private Plate plate(BitmapFont font, PlayerLoadout loadout) {
        if (loadout == null || !GadgetPanelModel.visible(loadout)) {
            return null;
        }
        float pad = theme.innerPadding * scale * 0.5f;
        float lineHeight = font.getLineHeight();
        float rowGap = theme.lineGap * scale;
        float barHeight = Math.max(2f, BAR_DESIGN_HEIGHT * scale);
        float secondHeight = Math.max(barHeight, lineHeight);
        float keyGap = lineHeight * 0.4f;
        float stateGap = lineHeight * 0.6f;

        // Pass one: measure. Widths only; positions come after the plate's width is known.
        List<Measured> measured = new ArrayList<>(2);
        float widest = MIN_WIDTH_DESIGN * scale;
        for (GadgetPanelModel.SlotPanel slot : GadgetPanelModel.slots(loadout)) {
            if (!slot.equipped()) {
                continue;
            }
            float keyWidth = width(font, slot.key());
            float nameWidth = width(font, slot.name());
            float stateWidth = width(font, slot.stateText());
            float durWidth = slot.hasDurabilityPool() ? width(font, slot.durabilityText()) : 0f;
            float cdWidth = slot.onCooldown() ? width(font, slot.cooldownText()) : 0f;
            boolean secondLine = slot.hasDurabilityPool() || slot.onCooldown();

            float nameLine = pad + keyWidth + keyGap + nameWidth + stateGap + stateWidth + pad;
            widest = Math.max(widest, nameLine);
            if (secondLine) {
                float group = durWidth
                    + (slot.onCooldown() && slot.hasDurabilityPool() ? rowGap : 0f) + cdWidth;
                float minBar = slot.hasDurabilityPool() ? MIN_BAR_DESIGN * scale : 0f;
                // pad | bar | gap | group | pad
                widest = Math.max(widest, pad + minBar + rowGap + group + pad);
            }
            measured.add(new Measured(slot, keyWidth + keyGap, nameWidth, stateWidth,
                durWidth, cdWidth, secondLine));
        }
        if (measured.isEmpty()) {
            return null;
        }
        float width = widest;

        // Pass two: place. Heights are known per row, so the plate's height comes first and the
        // rows are then laid from its top edge down.
        float height = pad * 2f;
        for (int i = 0; i < measured.size(); i++) {
            height += lineHeight;
            if (measured.get(i).secondLine) {
                height += rowGap + secondHeight;
            }
            if (i < measured.size() - 1) {
                height += rowGap * 2f;
            }
        }

        List<Row> rows = new ArrayList<>(measured.size());
        float top = height - pad;
        for (int i = 0; i < measured.size(); i++) {
            Measured m = measured.get(i);
            float baselineA = top - lineHeight * 0.85f;
            top -= lineHeight;
            float baselineB = 0f;
            float barY = 0f;
            float barX = pad;
            float barWidth = 0f;
            if (m.secondLine) {
                top -= rowGap;
                float groupWidth = m.durWidth
                    + (m.slot.onCooldown() && m.slot.hasDurabilityPool() ? rowGap : 0f) + m.cdWidth;
                // The second line's text baseline sits so the glyphs centre on the bar's band.
                baselineB = top - secondHeight + (secondHeight - lineHeight * 0.7f) / 2f;
                barY = top - secondHeight + (secondHeight - barHeight) / 2f;
                if (m.slot.hasDurabilityPool()) {
                    barWidth = Math.max(MIN_BAR_DESIGN * scale,
                        width - pad - rowGap - groupWidth - pad);
                }
                top -= secondHeight;
            }
            if (i < measured.size() - 1) {
                top -= rowGap * 2f;
            }
            rows.add(new Row(m.slot, pad + m.keyWidth, baselineA, baselineB, m.secondLine,
                m.slot.hasDurabilityPool(), barX, barY, barWidth, barHeight,
                m.stateWidth, m.durWidth, m.cdWidth));
        }
        return new Plate(width, height, pad, rows);
    }

    private float width(BitmapFont font, String text) {
        measurer.setText(font, text == null ? "" : text);
        return measurer.width;
    }

    /** Widths measured in pass one. */
    private record Measured(
        GadgetPanelModel.SlotPanel slot,
        float keyWidth,
        float nameWidth,
        float stateWidth,
        float durWidth,
        float cdWidth,
        boolean secondLine
    ) {
    }

    /**
     * One gadget's placed row. Horizontal positions are relative to the plate's left edge
     * (pass two has the width now); vertical ones are relative to its bottom edge.
     */
    private record Row(
        GadgetPanelModel.SlotPanel slot,
        float nameX,
        float baselineA,
        float baselineB,
        boolean secondLine,
        boolean hasBar,
        float barX,
        float barY,
        float barWidth,
        float barHeight,
        float stateWidth,
        float durWidth,
        float cdWidth
    ) {
    }

    /** The plate: its size and placed rows. */
    private record Plate(float width, float height, float pad, List<Row> rows) {
    }
}
