package io.github.skystrike.platform;

/** Actual keyboard visibility and bottom occlusion in physical pixels. */
public interface KeyboardProvider {
    void show(boolean visible);
    boolean isVisible();
    int bottomInsetPixels();
}
