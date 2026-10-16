package io.github.skystrike.platform;

import io.github.skystrike.fx.FxBudget;

/** Live device capabilities; implementations must not require GL during construction. */
public interface DeviceInfo {
    enum FormFactor { DESKTOP, PHONE, TABLET }

    FormFactor formFactor();
    boolean hasHardwareCursor();
    boolean isTouchPrimary();
    float density();
    float ppiX();
    float ppiY();
    SafeAreaInsets safeInsets();
    FxBudget.Tier recommendedFxTier();
}
