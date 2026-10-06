package io.github.skystrike.settings;

import io.github.skystrike.shared.text.ChatTarget;

/**
 * The small pile of client-side settings that survives a restart (console plan §2: the chat
 * target is the only chat/console state the client owns and keeps). Nothing authoritative or
 * per-server ever lands here.
 *
 * <p>Backed by libGDX {@code Preferences} when an application exists; before application
 * startup — unit tests, headless tools — values silently live in memory for the lifetime of the
 * instance instead, so this class never throws for lack of a platform.
 */
public final class ClientPreferences {

    private static final String STORE_NAME = "skystrike";
    private static final String KEY_CHAT_TARGET = "chatTarget";

    /** In-memory shadow used until/unless the platform store is available. */
    private ChatTarget memoryChatTarget = ChatTarget.ALL;

    /** Load as early as the caller likes; reading merges the stored value, if any, in. */
    public ClientPreferences() {
    }

    public ChatTarget chatTarget() {
        com.badlogic.gdx.Preferences store = store();
        if (store == null) {
            return memoryChatTarget;
        }
        ChatTarget stored = parse(store.getString(KEY_CHAT_TARGET, null));
        ChatTarget resolved = stored == null ? memoryChatTarget : stored;
        memoryChatTarget = resolved;
        return resolved;
    }

    public void saveChatTarget(ChatTarget target) {
        ChatTarget safe = target == null ? ChatTarget.ALL : target;
        memoryChatTarget = safe;
        com.badlogic.gdx.Preferences store = store();
        if (store != null) {
            store.putString(KEY_CHAT_TARGET, safe.name());
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
