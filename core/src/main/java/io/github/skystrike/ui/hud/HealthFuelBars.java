package io.github.skystrike.ui.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.skystrike.shared.hud.HudVitals;
import io.github.skystrike.shared.model.Player;
import java.util.Locale;

/**
 * The health and fuel bars, bottom left (playable build plan M4 §5).
 *
 * <p>Health runs to the 150 of {@code CombatConfig.MAX_HEALTH}; fuel runs to this player's own
 * tank, which a fuel-tank gadget enlarges. Neither number is computed here — {@link HudVitals}
 * owns the arithmetic in {@code shared}, where it is unit-tested, and this class only turns it
 * into rectangles.
 *
 * <p>Text sits <b>above</b> the bars on the console's panel fill, never on the bar fills
 * themselves: white-on-pale-green is the classic way a readout passes review on a dark test
 * scene and becomes unreadable the moment a player's health is full against a bright sky. On
 * the panel, {@code HudTheme.text} guarantees the console's 4.5:1 clamp.
 *
 * <p>The fuel bar also says <i>why</i> it is not moving. "Fuel only recharges while grounded"
 * is invisible in a bare percentage, so an airborne player with a part-empty tank is told
 * {@code LAND TO REFUEL} and a grounded one is told how long the refill needs.
 */
final class HealthFuelBars {

    private final HudTheme theme;
    private final GlyphLayout measurer = new GlyphLayout();

    /** Laid out in {@link #layout}, read by both passes so shapes and text cannot disagree. */
    private float x;
    private float y;
    private float width;
    private float height;
    private float gap;
    private float pad;
    private float scale;

    HealthFuelBars(HudTheme theme) {
        this.theme = theme;
    }

    /** Bottom-left corner, above the console's passive chat strip. */
    void layout(int screenWidth, int screenHeight, float scale, float bottomInset) {
        this.scale = scale;
        this.width = theme.barWidth * scale;
        this.height = theme.barHeight * scale;
        this.gap = theme.barGap * scale;
        this.pad = theme.innerPadding * scale * 0.7f;
        this.x = theme.margin * scale + pad;
        this.y = bottomInset + theme.margin * scale + pad;
    }

    private float fuelBarY() {
        return y;
    }

    private float healthBarY() {
        return y + height + gap;
    }

    /** Baseline of the HP/FUEL row, the first line above the bars. */
    private float valueBaseline(BitmapFont font) {
        return healthBarY() + height + pad * 0.5f + font.getLineHeight() * 0.8f;
    }

    /** Baseline of the status row (fuel note, or the respawn countdown), above the values. */
    private float statusBaseline(BitmapFont font) {
        return valueBaseline(font) + font.getLineHeight();
    }

    void drawShapes(ShapeRenderer shapes, BitmapFont font, HudFrame frame) {
        Player player = frame.player();
        if (player == null) {
            return;
        }
        // One plate behind bars and text alike: two rows are always reserved so the panel does
        // not grow and shrink as the fuel note comes and goes.
        float plateTop = statusBaseline(font) + font.getLineHeight() * 0.3f;
        shapes.setColor(theme.panelFill());
        shapes.rect(x - pad, y - pad, width + pad * 2f, plateTop - (y - pad));

        bar(shapes, healthBarY(), HudVitals.healthFraction(player),
            HudVitals.lowHealth(player) ? theme.healthLow : theme.healthFill);
        bar(shapes, fuelBarY(), HudVitals.fuelFraction(player), fuelColor(player));
    }

    private Color fuelColor(Player player) {
        if (HudVitals.fuelStalled(player)) {
            return theme.fuelStalled;
        }
        if (HudVitals.fuelRecharging(player)) {
            return theme.fuelRecharging;
        }
        return HudVitals.lowFuel(player) ? theme.fuelStalled : theme.fuelFill;
    }

    private void bar(ShapeRenderer shapes, float barY, float fraction, Color fill) {
        shapes.setColor(theme.barTrack);
        shapes.rect(x, barY, width, height);
        float filled = width * Math.min(1f, Math.max(0f, fraction));
        if (filled > 0f) {
            shapes.setColor(fill);
            shapes.rect(x, barY, filled, height);
        }
    }

    void drawText(SpriteBatch batch, BitmapFont font, HudFrame frame) {
        Player player = frame.player();
        if (player == null) {
            return;
        }
        float baseline = valueBaseline(font);

        String health = String.format(Locale.ROOT, "HP %.0f / %.0f",
            Math.max(0f, player.health), HudVitals.maxHealth());
        font.setColor(theme.text(HudVitals.lowHealth(player) ? theme.healthLow : theme.textPrimary));
        font.draw(batch, health, x, baseline);

        String fuel = String.format(Locale.ROOT, "FUEL %.0f%%", HudVitals.fuelFraction(player) * 100f);
        measurer.setText(font, fuel);
        font.setColor(theme.text(HudVitals.lowFuel(player) ? theme.textWarning : theme.textPrimary));
        font.draw(batch, fuel, x + width - measurer.width, baseline);

        String status = statusText(frame, player);
        if (!status.isEmpty()) {
            font.setColor(theme.text(
                frame.dead() || HudVitals.fuelStalled(player) ? theme.textWarning : theme.textDim));
            font.draw(batch, status, x, statusBaseline(font));
        }
        font.setColor(Color.WHITE);
    }

    /** The one thing worth saying about this player's state, or nothing. */
    private String statusText(HudFrame frame, Player player) {
        if (frame.dead()) {
            return String.format(Locale.ROOT, "DOWN - respawn in %.1fs",
                Math.max(0f, player.respawnTimer));
        }
        if (HudVitals.fuelStalled(player)) {
            return "LAND TO REFUEL";
        }
        if (HudVitals.fuelRecharging(player)) {
            return String.format(Locale.ROOT, "REFUELLING %.1fs", HudVitals.secondsToFull(player));
        }
        return "";
    }
}
