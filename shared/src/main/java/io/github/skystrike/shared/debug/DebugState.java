package io.github.skystrike.shared.debug;

/**
 * Per-feature debug state behind one master switch (playable build plan M1 §2.1).
 *
 * <p>Plain mutable holders: setters always store, and <b>reads return the safe default whenever
 * the master switch is off</b>. That is the load-bearing property — a release build answers
 * {@code false} for "noclip" and {@code 1.0} for "timescale" even if something managed to record
 * a different intent, so every read site is inert by construction rather than by remembering to
 * check the master first.
 *
 * <p>Each process owns its instance: the client builds one for render-side toggles, the server's
 * instance mirrors nothing authoritative — per-player cheat state lives on the player session
 * and is enforced by the simulation, never by these values.
 */
public final class DebugState {

    /** The one safe value that is not {@code false}. */
    public static final float DEFAULT_TIMESCALE = 1f;

    private final boolean masterEnabled;

    private boolean overlay;
    private boolean hitboxes;
    private boolean sdfView;
    private boolean freecam;
    private boolean shadows;
    private boolean playerLight;
    private boolean playerLightShadows;
    private boolean infiniteAmmo;
    private boolean noclip;
    private boolean godmode;
    private boolean fxDebug;
    private boolean contrastTest;
    private float timescale = DEFAULT_TIMESCALE;

    public DebugState(boolean masterEnabled) {
        this.masterEnabled = masterEnabled;
    }

    /** The value the instance was built with; reads never consult the global switch again. */
    public boolean masterEnabled() {
        return masterEnabled;
    }

    public boolean overlay() {
        return masterEnabled && overlay;
    }

    public boolean hitboxes() {
        return masterEnabled && hitboxes;
    }

    public boolean sdfView() {
        return masterEnabled && sdfView;
    }

    public boolean freecam() {
        return masterEnabled && freecam;
    }

    public boolean shadows() {
        return masterEnabled && shadows;
    }

    public boolean playerLight() {
        return masterEnabled && playerLight;
    }

    public boolean playerLightShadows() {
        return masterEnabled && playerLightShadows;
    }

    public boolean infiniteAmmo() {
        return masterEnabled && infiniteAmmo;
    }

    public boolean noclip() {
        return masterEnabled && noclip;
    }

    public boolean godmode() {
        return masterEnabled && godmode;
    }

    public boolean fxDebug() {
        return masterEnabled && fxDebug;
    }

    /** The console plan §5.2 four-background legibility check ({@code ui_contrast_test} / F12). */
    public boolean contrastTest() {
        return masterEnabled && contrastTest;
    }

    public float timescale() {
        return masterEnabled ? timescale : DEFAULT_TIMESCALE;
    }

    // Raw setters: the stored intent survives a master-switch toggle, reads gate on the master.

    public DebugState setOverlay(boolean value) {
        overlay = value;
        return this;
    }

    public DebugState setHitboxes(boolean value) {
        hitboxes = value;
        return this;
    }

    public DebugState setSdfView(boolean value) {
        sdfView = value;
        return this;
    }

    public DebugState setFreecam(boolean value) {
        freecam = value;
        return this;
    }

    public DebugState setShadows(boolean value) {
        shadows = value;
        return this;
    }

    public DebugState setPlayerLight(boolean value) {
        playerLight = value;
        return this;
    }

    public DebugState setPlayerLightShadows(boolean value) {
        playerLightShadows = value;
        return this;
    }

    public DebugState setInfiniteAmmo(boolean value) {
        infiniteAmmo = value;
        return this;
    }

    public DebugState setNoclip(boolean value) {
        noclip = value;
        return this;
    }

    public DebugState setGodmode(boolean value) {
        godmode = value;
        return this;
    }

    public DebugState setFxDebug(boolean value) {
        fxDebug = value;
        return this;
    }

    public DebugState setContrastTest(boolean value) {
        contrastTest = value;
        return this;
    }

    public DebugState setTimescale(float value) {
        timescale = value > 0f && Float.isFinite(value) ? value : DEFAULT_TIMESCALE;
        return this;
    }
}
