package io.github.skystrike.android;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import io.github.skystrike.Main;
import io.github.skystrike.android.platform.AndroidPlatformServices;

/** Launches the Android application. */
public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true; // Recommended, but not required.
        AndroidPlatformServices platform = new AndroidPlatformServices(this);
        initialize(new Main(null, platform.services()), configuration);
        platform.attach();
    }
}
