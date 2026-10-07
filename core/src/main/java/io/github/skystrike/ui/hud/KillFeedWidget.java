package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.KillFeedModel;
import java.util.List;

/**
 * The kill feed as a real widget, top right (playable build plan M4 §5).
 *
 * <p>It replaces the debug-text version {@code GameScreen.statusLines()} used to append, which
 * was only visible with the overlay on and never aged. The text itself is unchanged and
 * unchangeable here: {@link KillFeedModel} stores the kill event's own {@code feedLine()} verbatim,
 * so the server's wording is the only wording.
 *
 * <p>Lines hold, fade and expire on the wall clock inside the model; the widget draws whatever
 * {@link KillFeedModel#visible(long)} hands it, right-aligned, each on its own backing plate so
 * the text keeps the console's contrast guarantee over a bright sky or a dark wall alike.
 */
final class KillFeedWidget {

    private final HudTheme theme;
    private final KillFeedModel model;
    private final GlyphLayout measurer = new GlyphLayout();

    private float right;
    private float top;
    private float maxWidth;
    private float scale;

    KillFeedWidget(HudTheme theme, KillFeedModel model) {
        this.theme = theme;
        this.model = model;
    }

    void layout(int screenWidth, int screenHeight, float scale, float topInset) {
        this.scale = scale;
        this.right = screenWidth - theme.margin * scale;
        this.top = screenHeight - topInset - theme.margin * scale;
        this.maxWidth = theme.killFeedWidth * scale;
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        List<KillFeedModel.Entry> entries = model.visible(frame.nowMillis());
        if (entries.isEmpty()) {
            return;
        }
        float lineHeight = font.getLineHeight();
        float pad = theme.innerPadding * scale * 0.5f;
        float y = top;
        Color panel = theme.panelFill();
        for (KillFeedModel.Entry entry : entries) {
            measurer.setText(font, entry.line());
            float width = Math.min(maxWidth, measurer.width) + pad * 2f;
            float alpha = entry.alpha(frame.nowMillis());
            shapes.setColor(panel.r, panel.g, panel.b, panel.a * alpha);
            shapes.rect(right - width, y - lineHeight, width, lineHeight + pad);
            if (entry.involvesLocal()) {
                Color edge = entry.localVictim() ? theme.killFeedDeath : theme.killFeedLocal;
                float strip = Math.max(1f, 2f * scale);
                shapes.setColor(edge.r, edge.g, edge.b, edge.a * alpha);
                shapes.rect(right - width, y - lineHeight, strip, lineHeight + pad);
            }
            y -= lineHeight + theme.lineGap * scale;
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        List<KillFeedModel.Entry> entries = model.visible(frame.nowMillis());
        if (entries.isEmpty()) {
            return;
        }
        float lineHeight = font.getLineHeight();
        float pad = theme.innerPadding * scale * 0.5f;
        float y = top;
        for (KillFeedModel.Entry entry : entries) {
            measurer.setText(font, entry.line());
            float width = Math.min(maxWidth, measurer.width);
            float alpha = entry.alpha(frame.nowMillis());
            Color base = entry.localVictim() ? theme.killFeedDeath
                : entry.localKiller() ? theme.killFeedLocal : theme.killFeedText;
            Color clamped = theme.text(base);
            font.setColor(clamped.r, clamped.g, clamped.b, clamped.a * alpha);
            font.draw(batch, entry.line(), right - pad - width, y);
            y -= lineHeight + theme.lineGap * scale;
        }
        font.setColor(Color.WHITE);
    }
}
