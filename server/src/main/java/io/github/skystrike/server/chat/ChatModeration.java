package io.github.skystrike.server.chat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Server-side mutes (console plan §10, {@code server/chat/ChatModeration}).
 *
 * <p>A mute is enforced where it cannot be bypassed: at the relay, before the message is scoped
 * or delivered to anybody. A client-side mute list is a convenience for the person who sets it;
 * this is the one that actually silences someone.
 *
 * <p>Profanity masking is deliberately not here yet — it belongs with the mobile/polish slice of
 * the build order, and a half-built word filter in the authoritative path is worse than none.
 *
 * <p><b>Threading.</b> Tick thread only, like every other packet-handling collaborator.
 */
public final class ChatModeration {

    private final Set<Integer> mutedPlayerIds = new LinkedHashSet<>();

    /** Silences a player. Returns true if this changed anything. */
    public boolean mute(int playerId) {
        return mutedPlayerIds.add(playerId);
    }

    /** Lifts a mute. Returns true if the player was muted. */
    public boolean unmute(int playerId) {
        return mutedPlayerIds.remove(playerId);
    }

    public boolean isMuted(int playerId) {
        return mutedPlayerIds.contains(playerId);
    }

    /** Muted ids, in the order they were muted. Read-only. */
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
