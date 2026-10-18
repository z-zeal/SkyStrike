package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.TouchHudLayout;
import io.github.skystrike.shared.input.TouchAction;
import io.github.skystrike.shared.input.VirtualStickState;
import io.github.skystrike.shared.map.Rect;
import java.util.EnumSet;
import java.util.Set;

/**
 * The touch controls drawn over the HUD (Android plan §4, AN-4): the two virtual sticks, and the
 * action buttons that are not in the loadout dock, which includes the contextual USE and EXIT VIEW.
 *
 * <p>Every position comes from the {@link TouchHudLayout} in the frame; nothing here decides where
 * a control goes. It joins the HUD's two passes, shapes and then text, over the one batch, so it
 * opens no batch of its own.
 *
 * <p>CHAT, QUICK CHAT and LOADOUT are drawn dim. Their overlays have no touch close path yet
 * (AN-5/AN-6), so a player is shown they are not available instead of being shown a live control
 * that would trap them.
 */
final class TouchHudOverlay {

    /** The buttons drawn here. Slots and Q/E belong to the loadout dock, drawn by {@link LoadoutBar}. */
    private static final TouchAction[] PLATES = {
        TouchAction.FIRE, TouchAction.JUMP, TouchAction.JETPACK, TouchAction.CROUCH, TouchAction.ADS,
        TouchAction.USE, TouchAction.EXIT_VIEW, TouchAction.VIEW_CYCLE,
        TouchAction.CHAT, TouchAction.QUICK_CHAT, TouchAction.LOADOUT, TouchAction.PAUSE
    };

    /** Not yet operable on touch (AN-5/AN-6): drawn dim and not lit. */
    private static final Set<TouchAction> UNWIRED =
        EnumSet.of(TouchAction.CHAT, TouchAction.QUICK_CHAT, TouchAction.LOADOUT);

    private static final int CIRCLE_SEGMENTS = 32;
    private static final float KNOB_RATIO = 0.42f;
    private static final float DIM = 0.4f;

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    TouchHudOverlay(HudTheme theme) {
        this.theme = theme;
    }

    void drawShapes(ShapeRenderer shapes, TouchHudFrame touch) {
        if (touch == null) {
            return;
        }
        TouchHudLayout layout = touch.layout();
        float opacity = layout.buttonOpacity();
        drawStick(shapes, touch.move(), opacity);
        drawStick(shapes, touch.aim(), opacity);
        for (TouchAction action : PLATES) {
            Rect hit = layout.hit(action);
            if (hit != null) {
                drawPlate(shapes, hit, touch.highlighted().contains(action), UNWIRED.contains(action), opacity);
            }
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, TouchHudFrame touch) {
        if (touch == null) {
            return;
        }
        TouchHudLayout layout = touch.layout();
        float opacity = layout.buttonOpacity();
        float lineHeight = font.getLineHeight();
        for (TouchAction action : PLATES) {
            Rect hit = layout.hit(action);
            if (hit == null) {
                continue;
            }
            boolean unwired = UNWIRED.contains(action);
            Color base = unwired ? theme.textDim
                : touch.highlighted().contains(action) ? theme.textAccent : theme.textPrimary;
            Color color = theme.text(base);
            float alpha = opacity * (unwired ? DIM + 0.2f : 1f);
            font.setColor(color.r, color.g, color.b, color.a * alpha);
            String caption = fitting(font, action, hit.width() - hit.height() * 0.2f);
            measurer.setText(font, caption);
            font.draw(batch, caption,
                hit.x() + (hit.width() - measurer.width) / 2f,
                hit.y() + hit.height() / 2f + lineHeight * 0.35f);
        }
    }

    /** A stick is a base disc and a knob. Idle, it shows where a thumb would start; active, where it is. */
    private void drawStick(ShapeRenderer shapes, VirtualStickState stick, float opacity) {
        float radius = stick.radius();
        if (!(radius > 0f)) {
            return;
        }
        Color edge = theme.slotEdgeActive;
        float edgeAlpha = (stick.active() ? 0.55f : 0.30f) * opacity;
        float ringWidth = Math.max(1f, radius * 0.05f);
        shapes.setColor(edge.r, edge.g, edge.b, edge.a * edgeAlpha);
        shapes.circle(stick.originX(), stick.originY(), radius, CIRCLE_SEGMENTS);
        Color base = theme.barTrack;
        float baseAlpha = (stick.active() ? 0.45f : 0.28f) * opacity;
        shapes.setColor(base.r, base.g, base.b, base.a * baseAlpha);
        shapes.circle(stick.originX(), stick.originY(), Math.max(0f, radius - ringWidth), CIRCLE_SEGMENTS);

        float knobAlpha = (stick.active() ? 0.75f : 0.40f) * opacity;
        shapes.setColor(edge.r, edge.g, edge.b, edge.a * knobAlpha);
        shapes.circle(stick.knobX(), stick.knobY(), radius * KNOB_RATIO, CIRCLE_SEGMENTS);
    }

    private void drawPlate(
            ShapeRenderer shapes, Rect hit, boolean lit, boolean unwired, float opacity) {
        float alpha = opacity * (unwired ? DIM : 1f);
        Color fill = lit ? theme.slotFillActive : theme.slotFill;
        shapes.setColor(fill.r, fill.g, fill.b, fill.a * alpha);
        shapes.rect(hit.x(), hit.y(), hit.width(), hit.height());
        Color edge = lit ? theme.slotEdgeActive : theme.slotEdgeEmpty;
        float edgeHeight = Math.max(1f, hit.height() * 0.04f);
        shapes.setColor(edge.r, edge.g, edge.b, edge.a * alpha);
        shapes.rect(hit.x(), hit.top() - edgeHeight, hit.width(), edgeHeight);
    }

    /** The widest caption that fits its plate; the shortest is used when none does. */
    private String fitting(BitmapFont font, TouchAction action, float maxWidth) {
        String[] options = captions(action);
        for (String option : options) {
            measurer.setText(font, option);
            if (measurer.width <= maxWidth) {
                return option;
            }
        }
        return options[options.length - 1];
    }

    /** Widest first, so the last option is the one that always fits the smallest plate. */
    private static String[] captions(TouchAction action) {
        return switch (action) {
            case FIRE -> new String[] {"FIRE"};
            case JUMP -> new String[] {"JUMP"};
            case JETPACK -> new String[] {"JETPACK", "JET"};
            case CROUCH -> new String[] {"CROUCH", "CRCH"};
            case ADS -> new String[] {"ADS"};
            case USE -> new String[] {"USE DOOR", "USE"};
            case EXIT_VIEW -> new String[] {"EXIT VIEW", "EXIT"};
            case VIEW_CYCLE -> new String[] {"VIEW", "6"};
            case CHAT -> new String[] {"CHAT"};
            case QUICK_CHAT -> new String[] {"QUICK CHAT", "QUICK", "QC"};
            case LOADOUT -> new String[] {"LOADOUT", "GEAR"};
            case PAUSE -> new String[] {"PAUSE", "II"};
            default -> new String[] {""};
        };
    }
}
