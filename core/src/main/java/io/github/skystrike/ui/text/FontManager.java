package io.github.skystrike.ui.text;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;
import java.util.Objects;

/**
 * Regenerates the dialog font at a DPI-aware size, with its outline and shadow baked into the
 * glyphs rather than redrawing each string several times.
 *
 * <p>The caller supplies a real TTF/OTF source when wiring the final HUD theme. This class keeps
 * that platform-sensitive generation in one place and exposes a small font-plus-measurement API
 * to the dialog and wrapper.
 */
public final class FontManager implements Disposable {

    /** Approximately 2.2% of viewport height, bounded for desktop and touch readability. */
    public static final float VIEWPORT_HEIGHT_FRACTION = 0.022f;
    public static final int MIN_PHYSICAL_PIXELS = 12;
    public static final int MAX_PHYSICAL_PIXELS = 28;

    private static final float OUTLINE_PIXELS = 1f;
    private static final float SHADOW_ALPHA = 0.50f;
    private static final String DIALOG_CHARACTERS = latin1AndUiCharacters();

    private final FileHandle source;
    private final String internalFontPath;
    private BitmapFont font;
    private int generatedPhysicalPixels = -1;

    /**
     * Creates a manager for an internal TTF/OTF source. Generation is lazy so construction is
     * safe before the application/window has been initialised.
     */
    public FontManager(String internalFontPath) {
        if (internalFontPath == null || internalFontPath.isBlank()) {
            throw new IllegalArgumentException("internal font path is required");
        }
        this.source = null;
        this.internalFontPath = internalFontPath;
    }

    /**
     * Creates a manager for a caller-provided TTF/OTF source. The file must be readable when
     * {@link #resize(int, float)} first generates the font.
     */
    public FontManager(FileHandle source) {
        this.source = Objects.requireNonNull(source, "source");
        this.internalFontPath = null;
    }

    /**
     * Regenerates only when the target physical glyph size changes. Pass the logical viewport
     * height plus the display density so the 12-pixel accessibility floor is physical, not dp.
     */
    public void resize(int viewportHeight, float displayDensity) {
        int target = targetPhysicalPixels(viewportHeight, displayDensity);
        if (target == generatedPhysicalPixels && font != null) {
            return;
        }
        regenerate(target);
    }

    /** Calculates the clamped target physical font size without touching graphics resources. */
    public static int targetPhysicalPixels(int viewportHeight, float displayDensity) {
        float density = Float.isFinite(displayDensity) ? Math.max(1f, displayDensity) : 1f;
        float logicalHeight = Math.max(1, viewportHeight);
        int requested = Math.round(logicalHeight * density * VIEWPORT_HEIGHT_FRACTION);
        return Math.max(MIN_PHYSICAL_PIXELS, Math.min(MAX_PHYSICAL_PIXELS, requested));
    }

    /** The generated font. Call {@link #resize(int, float)} before retrieving it. */
    public BitmapFont font() {
        if (font == null) {
            throw new IllegalStateException("font has not been generated; call resize first");
        }
        return font;
    }

    public int generatedPhysicalPixels() {
        return generatedPhysicalPixels;
    }

    private void regenerate(int targetPhysicalPixels) {
        FileHandle file = resolveSource();
        if (!file.exists()) {
            throw new IllegalStateException("dialog font source does not exist: " + file);
        }

        if (font != null) {
            font.dispose();
            font = null;
        }

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(file);
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameter =
                new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = targetPhysicalPixels;
            parameter.borderWidth = OUTLINE_PIXELS;
            parameter.borderColor = Color.BLACK;
            parameter.shadowOffsetX = 1;
            parameter.shadowOffsetY = 1;
            parameter.shadowColor = new Color(0f, 0f, 0f, SHADOW_ALPHA);
            parameter.characters = DIALOG_CHARACTERS;
            parameter.incremental = true;
            font = generator.generateFont(parameter);
            font.setUseIntegerPositions(true);
            generatedPhysicalPixels = targetPhysicalPixels;
        } finally {
            generator.dispose();
        }
    }

    private FileHandle resolveSource() {
        // Resolve internal assets only once a libGDX Files service exists. Calling resize before
        // application startup is an integration error; construction itself remains safe.
        if (internalFontPath != null) {
            if (Gdx.files == null) {
                throw new IllegalStateException("cannot resolve an internal font before libGDX starts");
            }
            return Gdx.files.internal(internalFontPath);
        }
        return source;
    }

    private static String latin1AndUiCharacters() {
        StringBuilder characters = new StringBuilder(0x00FF - 0x20 + 3);
        for (int codePoint = 0x20; codePoint <= 0x00FF; codePoint++) {
            characters.appendCodePoint(codePoint);
        }
        return characters.append("→□").toString();
    }

    @Override
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
        generatedPhysicalPixels = -1;
    }
}
