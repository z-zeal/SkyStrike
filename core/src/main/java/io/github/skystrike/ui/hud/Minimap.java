package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.MinimapModel;

/**
 * The arena minimap, top left (roadmap Phase 7 HUD; mechanics §4's teammate toggle).
 *
 * <p>The whole arena, not a radar centred on the player: the map is one fixed 3000 × 2000 layout
 * that a player learns, so the useful question is "where is the fight, and where am I in it" —
 * which a cropped radar answers worse, and which makes a deployed drone or a stuck camera legible
 * as the observation post it is. Nothing is cropped, so a jetpack fight in open sky is on the map
 * too.
 *
 * <p>Like every other widget here this class decides nothing. {@link MinimapModel} owns which
 * markers exist and where they sit in normalised arena space — including the rule that a marker
 * only appears where the fog actually lights its target — and {@code View.fit} owns the frame's
 * aspect. What is left is shapes: terrain as filled rectangles, markers as squares for bodies and
 * circles for devices, and a hollow marker for a remembered sighting rather than a live one, so a
 * ghost can never be mistaken for something you can currently see.
 *
 * <p>Shapes only, no text pass: there is nothing on a map this size worth a glyph, and every
 * string this HUD draws would have to go through the contrast clamp for no gain.
 */
final class Minimap {

    private final HudTheme theme;

    private float boxX;
    private float boxY;
    private float boxWidth;
    private float boxHeight;
    private float scale;

    Minimap(HudTheme theme) {
        this.theme = theme;
    }

    /** Top-left corner, clear of the margin, in the HUD's y-up projection. */
    void layout(int screenWidth, int screenHeight, float scale) {
        this.scale = scale;
        this.boxWidth = theme.minimapWidth * scale;
        this.boxHeight = theme.minimapHeight * scale;
        this.boxX = theme.margin * scale;
        this.boxY = screenHeight - theme.margin * scale - this.boxHeight;
    }

    /**
     * How much of the top-left corner this widget occupies, so the debug panel can be inset below
     * it instead of drawing through it. Zero when the map is off, which puts the readout back in
     * the corner it had before.
     */
    float occupiedHeight(boolean visible) {
        return visible ? boxHeight + theme.innerPadding * scale : 0f;
    }

    void drawShapes(ShapeRenderer shapes, HudFrame frame) {
        MinimapModel.View view = frame.minimap();
        if (view == null) {
            return;
        }
        MinimapModel.Box map = view.fit(boxX, boxY, boxWidth, boxHeight);
        if (map.width() <= 0f || map.height() <= 0f) {
            return;
        }
        float pad = theme.innerPadding * scale * 0.5f;
        float edge = Math.max(1f, 1.5f * scale);

        // The panel the map sits on, which is also the console's, so the HUD keeps one backing.
        shapes.setColor(theme.panelFill());
        shapes.rect(boxX - pad, boxY - pad, boxWidth + pad * 2f, boxHeight + pad * 2f);

        shapes.setColor(theme.mapTerrain);
        for (MinimapModel.Box solid : view.terrain()) {
            // A 14-unit tunnel roof is a fraction of a pixel at whole-arena zoom; without the
            // floor of one pixel the arena's thinnest geometry simply vanishes from the map.
            float width = Math.max(1f, solid.width() * map.width());
            float height = Math.max(1f, solid.height() * map.height());
            shapes.rect(
                map.x() + solid.x() * map.width(),
                map.y() + solid.y() * map.height(),
                width,
                height);
        }

        if (view.viewport() != null) {
            MinimapModel.Box viewport = view.viewport();
            outline(
                shapes,
                theme.mapViewport,
                map.x() + viewport.x() * map.width(),
                map.y() + viewport.y() * map.height(),
                Math.max(1f, viewport.width() * map.width()),
                Math.max(1f, viewport.height() * map.height()),
                Math.max(1f, edge));
        }

        for (MinimapModel.Blip blip : view.blips()) {
            float size = theme.mapBlipSize * scale
                * (blip.allegiance() == MinimapModel.Allegiance.SELF ? 1.4f : 1f);
            float x = map.x() + blip.x() * map.width();
            float y = map.y() + blip.y() * map.height();
            Color colour = colourOf(blip);
            shapes.setColor(colour.r, colour.g, colour.b, colour.a * blip.alpha());

            if (blip.stale()) {
                // Hollow: a memory of where they were, not a claim about where they are.
                outline(shapes, colour, x - size / 2f, y - size / 2f, size, size,
                    Math.max(1f, edge * 0.5f));
            } else if (blip.kind() == MinimapModel.Kind.PLAYER) {
                shapes.rect(x - size / 2f, y - size / 2f, size, size);
            } else {
                // A device reads as a dot: smaller, round, and never mistaken for a body.
                shapes.circle(x, y, size * 0.4f);
            }
        }

        Color edgeColour = theme.panelEdge();
        outline(shapes, edgeColour, boxX - pad, boxY - pad,
            boxWidth + pad * 2f, boxHeight + pad * 2f, edge);
    }

    /** A rectangle's outline as four thin filled rects: the shapes pass is open in Filled mode. */
    private static void outline(
            ShapeRenderer shapes,
            Color colour,
            float x,
            float y,
            float width,
            float height,
            float thickness) {
        shapes.setColor(colour);
        shapes.rect(x, y, width, thickness);
        shapes.rect(x, y + height - thickness, width, thickness);
        shapes.rect(x, y, thickness, height);
        shapes.rect(x + width - thickness, y, thickness, height);
    }

    private Color colourOf(MinimapModel.Blip blip) {
        return switch (blip.allegiance()) {
            case SELF -> theme.mapSelf;
            case ALLY -> theme.mapAlly;
            case ENEMY -> theme.mapEnemy;
        };
    }
}
