package io.github.skystrike.shared.config;

/**
 * Master debug switch plus per-feature toggles.
 *
 * <p>Phase 0 only needs the master switch; overlays and hitbox rendering are gated on it from
 * Phase 11.
 */
public final class DebugFlags {

    /** When false, every other flag here must be treated as false. */
    public static final boolean ENABLED = false;

    private DebugFlags() {
    }
}
