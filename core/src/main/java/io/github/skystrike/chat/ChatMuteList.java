package io.github.skystrike.chat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A local, per-player mute list (console plan §10, {@code core/chat/ChatMuteList}).
 *
 * <p>Purely cosmetic and purely local: it hides lines after they arrive. It is not a moderation
 * tool and it is not a substitute for the authoritative mute, which stops the message being sent
 * at all. Both exist because they solve different problems — one person finding somebody
 * annoying, versus somebody actually breaking the rules.
 *
 * <p>Persistence is deferred to the platform storage provider; the set is deliberately ordered so
 * a future save writes a stable file.
 */
public final class ChatMuteList {

    private final Set<Integer> mutedPlayerIds = new LinkedHashSet<>();

    public boolean mute(int playerId) {
        return mutedPlayerIds.add(playerId);
    }

    public boolean unmute(int playerId) {
        return mutedPlayerIds.remove(playerId);
    }

    /** Flips the mute state and returns the new one. */
    public boolean toggle(int playerId) {
        if (!mutedPlayerIds.remove(playerId)) {
            mutedPlayerIds.add(playerId);
            return true;
        }
        return false;
    }

    public boolean isMuted(int playerId) {
        return mutedPlayerIds.contains(playerId);
    }

    public Set<Integer> mutedPlayerIds() {
        return Collections.unmodifiableSet(mutedPlayerIds);
    }

    public int count() {
        return mutedPlayerIds.size();
    }

    public void clear() {
        mutedPlayerIds.clear();
    }
}
