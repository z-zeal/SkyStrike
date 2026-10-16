package io.github.skystrike.platform;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import io.github.skystrike.fx.FxBudget;
import java.util.Objects;
import java.util.Optional;

/** Immutable service bundle, supplied by the launcher before libGDX initializes. */
public record PlatformServices(
        DeviceInfo device, KeyboardProvider keyboard, StorageProvider storage, AuthProvider auth) {
    public PlatformServices {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(keyboard, "keyboard");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(auth, "auth");
    }

    /** Safe to construct without Gdx globals; legacy desktop callers retain their behavior. */
    public static PlatformServices defaults() {
        return new PlatformServices(new DeviceInfo() {
            public FormFactor formFactor() { return FormFactor.DESKTOP; }
            public boolean hasHardwareCursor() { return true; }
            public boolean isTouchPrimary() { return false; }
            public float density() { return Gdx.graphics == null ? 1f : Gdx.graphics.getDensity(); }
            public float ppiX() { return Gdx.graphics == null ? 96f : Gdx.graphics.getPpiX(); }
            public float ppiY() { return Gdx.graphics == null ? 96f : Gdx.graphics.getPpiY(); }
            public SafeAreaInsets safeInsets() { return SafeAreaInsets.ZERO; }
            public FxBudget.Tier recommendedFxTier() { return FxBudget.Tier.HIGH; }
        }, new KeyboardProvider() {
            public void show(boolean visible) {
                if (Gdx.input != null) Gdx.input.setOnscreenKeyboardVisible(visible);
            }
            public boolean isVisible() { return false; }
            public int bottomInsetPixels() { return 0; }
        }, new StorageProvider() {
            public Preferences preferences(String name) { return Gdx.app.getPreferences(name); }
            public FileHandle userMapsDir() {
                return Gdx.files.isExternalStorageAvailable()
                    ? Gdx.files.external(".skystrike/maps/") : Gdx.files.local("maps/");
            }
        }, new AuthProvider() {
            private String token;
            public Optional<String> loadRefreshToken() { return Optional.ofNullable(token); }
            public void saveRefreshToken(String value) { token = Objects.requireNonNull(value); }
            public void clearRefreshToken() { token = null; }
        });
    }
}
