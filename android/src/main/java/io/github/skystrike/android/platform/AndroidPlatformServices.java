package io.github.skystrike.android.platform;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import io.github.skystrike.fx.FxBudget;
import io.github.skystrike.platform.AuthProvider;
import io.github.skystrike.platform.DeviceInfo;
import io.github.skystrike.platform.KeyboardProvider;
import io.github.skystrike.platform.PlatformServices;
import io.github.skystrike.platform.SafeAreaInsets;
import io.github.skystrike.platform.StorageProvider;
import java.io.File;
import java.util.Objects;
import java.util.Optional;

/** Android-owned services. UI-thread inset snapshots are published to the render thread. */
public final class AndroidPlatformServices implements DeviceInfo, KeyboardProvider,
        StorageProvider, AuthProvider {
    private final Activity activity;
    private final SharedPreferences tokens;
    private volatile SafeAreaInsets safeInsets = SafeAreaInsets.ZERO;
    private volatile int keyboardBottom;
    private volatile boolean keyboardVisible;

    public AndroidPlatformServices(Activity activity) {
        this.activity = Objects.requireNonNull(activity);
        tokens = activity.getSharedPreferences("skystrike.auth", Context.MODE_PRIVATE);
    }

    public PlatformServices services() {
        return new PlatformServices(this, this, this, this);
    }

    /** Install once after initialize() has attached the libGDX view. Do not consume insets. */
    public void attach() {
        View decor = activity.getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                safeInsets = new SafeAreaInsets(safe.left, safe.right, safe.top, safe.bottom);
                keyboardVisible = insets.isVisible(WindowInsets.Type.ime());
                keyboardBottom = keyboardVisible ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0;
            } else {
                int left = insets.getStableInsetLeft();
                int right = insets.getStableInsetRight();
                int top = insets.getStableInsetTop();
                int bottom = insets.getStableInsetBottom();
                if (Build.VERSION.SDK_INT >= 28 && insets.getDisplayCutout() != null) {
                    android.view.DisplayCutout cutout = insets.getDisplayCutout();
                    left = Math.max(left, cutout.getSafeInsetLeft());
                    right = Math.max(right, cutout.getSafeInsetRight());
                    top = Math.max(top, cutout.getSafeInsetTop());
                    bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                }
                safeInsets = new SafeAreaInsets(left, right, top, bottom);
            }
            return view.onApplyWindowInsets(insets);
        });
        if (Build.VERSION.SDK_INT < 30) {
            // Older Android has no IME inset API. Ignore small system-bar-only changes.
            decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Rect frame = new Rect();
                decor.getWindowVisibleDisplayFrame(frame);
                int hidden = Math.max(0, decor.getRootView().getHeight() - frame.bottom);
                int threshold = Math.round(100f * density());
                keyboardVisible = hidden > Math.max(threshold, safeInsets.bottom());
                keyboardBottom = keyboardVisible ? hidden : 0;
            });
        }
        decor.requestApplyInsets();
    }

    public FormFactor formFactor() {
        return activity.getResources().getConfiguration().smallestScreenWidthDp >= 600
            ? FormFactor.TABLET : FormFactor.PHONE;
    }
    public boolean hasHardwareCursor() { return false; }
    public boolean isTouchPrimary() { return true; }
    public float density() { return activity.getResources().getDisplayMetrics().density; }
    public float ppiX() { return activity.getResources().getDisplayMetrics().xdpi; }
    public float ppiY() { return activity.getResources().getDisplayMetrics().ydpi; }
    public SafeAreaInsets safeInsets() { return safeInsets; }
    public FxBudget.Tier recommendedFxTier() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        return manager != null && (manager.isLowRamDevice()
            || manager.getDeviceConfigurationInfo().reqGlEsVersion < 0x30000)
            ? FxBudget.Tier.LOW : FxBudget.Tier.MID;
    }
    public void show(boolean visible) {
        activity.runOnUiThread(() -> {
            if (Gdx.input != null) Gdx.input.setOnscreenKeyboardVisible(visible);
        });
    }
    public boolean isVisible() { return keyboardVisible; }
    public int bottomInsetPixels() { return keyboardBottom; }
    public Preferences preferences(String name) { return Gdx.app.getPreferences(name); }
    public FileHandle userMapsDir() {
        File root = activity.getExternalFilesDir(null);
        if (root == null) root = activity.getFilesDir();
        return new FileHandle(new File(root, "maps"));
    }
    // Persistence hook only. Upgrade to encrypted storage before shipping real account tokens.
    public Optional<String> loadRefreshToken() {
        return Optional.ofNullable(tokens.getString("refreshToken", null));
    }
    public void saveRefreshToken(String token) {
        tokens.edit().putString("refreshToken", Objects.requireNonNull(token)).apply();
    }
    public void clearRefreshToken() { tokens.edit().remove("refreshToken").apply(); }
}
