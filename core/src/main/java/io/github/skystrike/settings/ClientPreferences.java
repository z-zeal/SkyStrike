package io.github.skystrike.settings;

import io.github.skystrike.shared.settings.Settings;
import io.github.skystrike.shared.text.ChatTarget;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The small pile of client-side settings that survives a restart. Nothing authoritative or
 * per-server ever lands here.
 *
 * <p>Backed by libGDX {@code Preferences} when an application exists; before application
 * startup - unit tests and headless tools - values silently live in memory for the lifetime of
 * the instance instead, so this class never throws for lack of a platform.
 */
public final class ClientPreferences {

    /** One namespace for chat target and M5 video/audio settings. */
    private static final String STORE_NAME = "skystrike";
    private static final String KEY_CHAT_TARGET = Settings.KEY_CHAT_TARGET;

    /** In-memory shadow used until/unless the platform store is available. */
    private ChatTarget memoryChatTarget = ChatTarget.ALL;
    private final Map<String, String> memorySettings = new LinkedHashMap<>();

    public ClientPreferences() {
    }

    public ChatTarget chatTarget() {
        com.badlogic.gdx.Preferences store = store();
        String storedValue = store == null
            ? memorySettings.get(KEY_CHAT_TARGET)
            : store.getString(KEY_CHAT_TARGET, null);
        ChatTarget stored = parse(storedValue);
        ChatTarget resolved = stored == null ? memoryChatTarget : stored;
        memoryChatTarget = resolved;
        return resolved;
    }

    public void saveChatTarget(ChatTarget target) {
        ChatTarget safe = target == null ? ChatTarget.ALL : target;
        memoryChatTarget = safe;
        memorySettings.put(KEY_CHAT_TARGET, safe.name().toLowerCase(java.util.Locale.ROOT));
        com.badlogic.gdx.Preferences store = store();
        if (store != null) {
            store.putString(KEY_CHAT_TARGET, safe.name());
            store.flush();
        }
    }

    /** Loads the shared validated settings model from the same client preferences namespace. */
    public void loadSettings(Settings settings) {
        if (settings == null) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>(memorySettings);
        com.badlogic.gdx.Preferences store = store();
        if (store != null) {
            for (String key : settings.toValues().keySet()) {
                if (store.contains(key)) {
                    values.put(key, store.getString(key));
                }
            }
        }
        settings.loadFrom(values);
        memorySettings.putAll(settings.toValues());
    }

    /** Persists only values validated by the shared settings model. */
    public void saveSettings(Settings settings) {
        if (settings == null) {
            return;
        }
        Map<String, String> values = settings.toValues();
        memorySettings.putAll(values);
        ChatTarget target = parse(values.get(KEY_CHAT_TARGET));
        if (target != null) {
            memoryChatTarget = target;
        }
        com.badlogic.gdx.Preferences store = store();
        if (store != null) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                store.putString(entry.getKey(), entry.getValue());
            }
            store.flush();
        }
    }

    private static ChatTarget parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ChatTarget.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static com.badlogic.gdx.Preferences store() {
        com.badlogic.gdx.Application app = com.badlogic.gdx.Gdx.app;
        return app == null ? null : app.getPreferences(STORE_NAME);
    }
}
