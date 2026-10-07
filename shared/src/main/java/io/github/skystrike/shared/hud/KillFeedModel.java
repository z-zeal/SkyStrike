package io.github.skystrike.shared.hud;

import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The kill feed as data (playable build plan M4 §5), so the widget only has to draw it.
 *
 * <p>Until M4 the feed was a handful of lines appended to the debug readout — visible only with
 * the overlay on, never ageing, never highlighting your own kills. This keeps the same single
 * source for the text ({@link PacketKillEvent#feedLine()}, so the server's wording and the
 * client's cannot diverge) and adds the three things a real feed needs: a cap, an age, and the
 * knowledge of whether the local player is in the line.
 *
 * <p>Entries expire by wall clock, not by frame count, so a frame spike cannot clear the feed
 * and a paused client cannot hoard it. Ageing is evaluated on read: nothing here needs ticking.
 */
public final class KillFeedModel {

    /** At most this many lines are drawn, newest first. */
    public static final int MAX_VISIBLE = 5;

    /** How long a line stays fully lit before it starts to fade. */
    public static final float HOLD_SECONDS = 6.5f;

    /** How long the fade itself takes. */
    public static final float FADE_SECONDS = 1.5f;

    /** Total life of one line. */
    public static final float LIFETIME_SECONDS = HOLD_SECONDS + FADE_SECONDS;

    /** Entries kept in memory: a little more than is ever drawn, so a burst still ages out. */
    private static final int CAPACITY = 16;

    /**
     * One feed line.
     *
     * @param line           the text, straight from the kill event
     * @param headshot       whether the shot was a headshot
     * @param friendlyFire   whether it was a team kill
     * @param selfInflicted  whether the victim did it to themselves
     * @param localKiller    whether the local player got the kill
     * @param localVictim    whether the local player died
     * @param timestampMillis when it arrived, for ageing
     */
    public record Entry(
        String line,
        boolean headshot,
        boolean friendlyFire,
        boolean selfInflicted,
        boolean localKiller,
        boolean localVictim,
        long timestampMillis
    ) {

        /** True when the local player is either end of the line, and it should stand out. */
        public boolean involvesLocal() {
            return localKiller || localVictim;
        }

        /** Age in seconds at {@code nowMillis}; never negative. */
        public float ageSeconds(long nowMillis) {
            return Math.max(0f, (nowMillis - timestampMillis) / 1000f);
        }

        /** 1 while held, falling to 0 across the fade, 0 once expired. */
        public float alpha(long nowMillis) {
            float age = ageSeconds(nowMillis);
            if (age <= HOLD_SECONDS) {
                return 1f;
            }
            if (age >= LIFETIME_SECONDS) {
                return 0f;
            }
            return 1f - (age - HOLD_SECONDS) / FADE_SECONDS;
        }
    }

    private final Deque<Entry> entries = new ArrayDeque<>();
    private int localPlayerId = -1;

    /** The id used to decide {@code localKiller}/{@code localVictim} on later additions. */
    public void setLocalPlayerId(int localPlayerId) {
        this.localPlayerId = localPlayerId;
    }

    public int localPlayerId() {
        return localPlayerId;
    }

    /** Files one kill. Null events and events with no text are ignored. */
    public void add(PacketKillEvent kill, long nowMillis) {
        if (kill == null) {
            return;
        }
        String line = kill.feedLine();
        if (line == null || line.isBlank()) {
            return;
        }
        boolean known = localPlayerId >= 0;
        // A suicide credits nobody: the victim is you, the killer is not.
        boolean localKiller = known && kill.killerId == localPlayerId && !kill.selfInflicted;
        boolean localVictim = known && kill.victimId == localPlayerId;
        entries.addLast(new Entry(
            line,
            kill.headshot,
            kill.friendlyFire,
            kill.selfInflicted,
            localKiller,
            localVictim,
            nowMillis));
        while (entries.size() > CAPACITY) {
            entries.removeFirst();
        }
    }

    /**
     * The lines to draw at {@code nowMillis}, <b>newest first</b> and at most
     * {@link #MAX_VISIBLE}. Expired lines are dropped as they are passed, so reading the feed
     * is also what retires it — there is no second clean-up path to forget to call.
     */
    public List<Entry> visible(long nowMillis) {
        entries.removeIf(entry -> entry.alpha(nowMillis) <= 0f);
        List<Entry> out = new ArrayList<>(Math.min(MAX_VISIBLE, entries.size()));
        int taken = 0;
        for (java.util.Iterator<Entry> it = entries.descendingIterator();
                it.hasNext() && taken < MAX_VISIBLE; taken++) {
            out.add(it.next());
        }
        return out;
    }

    /** Entries held right now, expired or not. Diagnostics and tests. */
    public int size() {
        return entries.size();
    }

    public void clear() {
        entries.clear();
    }
}
