package io.github.skystrike.shared.input;

import io.github.skystrike.shared.config.TouchConfig;
import java.util.Locale;

/**
 * The touch settings a match reads (Android plan §3, decisions AN1–AN4).
 *
 * <p>{@link io.github.skystrike.shared.settings.Settings} persists the same fields. This record is
 * the value the control model and the layout take, already clamped, so a bad preferences file
 * cannot hand them a NaN scale. Scheme C is intentionally absent: decision AN2 ships A and B.
 *
 * @param aimScheme         Scheme A ({@link AimScheme#STICK}) or Scheme B ({@link AimScheme#FLICK})
 * @param adsMode           toggle by default, hold when the player asks (decision AN3)
 * @param floatingMoveStick floating-origin movement stick; {@code false} pins the origin
 * @param buttonScale       requested scale of sticks and buttons, before the layout clamps it to fit
 * @param buttonOpacity     draw opacity for AN-4; layout math ignores it
 * @param leftHanded        mirror the thumb controls across the screen
 * @param outerRingFire     Scheme A guns also fire past {@link TouchConfig#FIRE_RING_THRESHOLD}
 * @param hudPreset         auto follows shortest width; phone or tablet forces that geometry
 */
public record TouchPreferences(
    AimScheme aimScheme,
    AdsMode adsMode,
    boolean floatingMoveStick,
    float buttonScale,
    float buttonOpacity,
    boolean leftHanded,
    boolean outerRingFire,
    HudPreset hudPreset
) {

    /** Right-thumb aim scheme. Tokens are the persisted spellings. */
    public enum AimScheme {
        /** Scheme A: aim stick retains its angle, dedicated Fire and ADS, optional outer ring. */
        STICK("a"),
        /** Scheme B: twin-stick aim, release-to-fire for single-shot weapons and throwables. */
        FLICK("b");

        private final String token;

        AimScheme(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static AimScheme parse(String raw) {
            if (raw == null) {
                return STICK;
            }
            String normalised = raw.trim().toLowerCase(Locale.ROOT);
            if ("b".equals(normalised) || "flick".equals(normalised) || "scheme-b".equals(normalised)) {
                return FLICK;
            }
            return STICK;
        }
    }

    /** ADS on touch. Tokens are the persisted spellings. */
    public enum AdsMode {
        TOGGLE("toggle"),
        HOLD("hold");

        private final String token;

        AdsMode(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static AdsMode parse(String raw) {
            if (raw == null) {
                return TOGGLE;
            }
            String normalised = raw.trim().toLowerCase(Locale.ROOT);
            return "hold".equals(normalised) ? HOLD : TOGGLE;
        }
    }

    /**
     * Which geometry {@code TouchHudLayout} builds. {@link #AUTO} classifies by shortest width.
     * The phone preset is the §4.4 option that parks tablet vitals at top-center.
     */
    public enum HudPreset {
        AUTO("auto"),
        PHONE("phone"),
        TABLET("tablet");

        private final String token;

        HudPreset(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static HudPreset parse(String raw) {
            if (raw == null) {
                return AUTO;
            }
            String normalised = raw.trim().toLowerCase(Locale.ROOT);
            if ("phone".equals(normalised)) {
                return PHONE;
            }
            if ("tablet".equals(normalised)) {
                return TABLET;
            }
            return AUTO;
        }
    }

    public TouchPreferences {
        if (aimScheme == null) {
            aimScheme = AimScheme.STICK;
        }
        if (adsMode == null) {
            adsMode = AdsMode.TOGGLE;
        }
        if (hudPreset == null) {
            hudPreset = HudPreset.AUTO;
        }
        buttonScale = TouchConfig.clampButtonScale(buttonScale);
        buttonOpacity = TouchConfig.clampButtonOpacity(buttonOpacity);
    }

    /** Decision defaults: Scheme A, ADS toggle, floating movement stick, right-handed. */
    public static TouchPreferences defaults() {
        return new TouchPreferences(
            AimScheme.STICK,
            AdsMode.TOGGLE,
            true,
            TouchConfig.BUTTON_SCALE_DEFAULT,
            TouchConfig.BUTTON_OPACITY_DEFAULT,
            false,
            true,
            HudPreset.AUTO);
    }

    /** Already sanitised by the canonical constructor; kept so callers can be obvious. */
    public TouchPreferences validated() {
        return this;
    }
}
