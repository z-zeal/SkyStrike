package io.github.skystrike.lwjgl3.platform;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import io.github.skystrike.platform.AuthProvider;
import io.github.skystrike.platform.PlatformServices;
import java.util.Objects;
import java.util.Optional;

/** Desktop adapters use libGDX storage lazily, after the backend has initialized. */
public final class DesktopPlatformServices {
    private DesktopPlatformServices() { }

    public static PlatformServices create() {
        PlatformServices defaults = PlatformServices.defaults();
        return new PlatformServices(defaults.device(), defaults.keyboard(), defaults.storage(),
            new AuthProvider() {
                // Stub storage, not a credential vault; accounts work must harden this first.
                public Optional<String> loadRefreshToken() {
                    return Optional.ofNullable(Gdx.app.getPreferences("skystrike.auth")
                        .getString("refreshToken", null));
                }
                public void saveRefreshToken(String token) {
                    Gdx.app.getPreferences("skystrike.auth")
                        .putString("refreshToken", Objects.requireNonNull(token)).flush();
                }
                public void clearRefreshToken() {
                    // remove() returns void, unlike putString(), so flush cannot be chained.
                    Preferences preferences = Gdx.app.getPreferences("skystrike.auth");
                    preferences.remove("refreshToken");
                    preferences.flush();
                }
            });
    }
}
