package io.github.skystrike.shared.hud;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The per-sighting fade behind the enemy-body visibility gate: how fast a target that just
 * became lit appears, how long a lost sighting holds, and how fast it then fades to zero.
 *
 * <p>Gating a drawn enemy on {@code ObserverSet.isLit} alone would make the body strobe at the
 * cone edge — the exact failure {@link MinimapModel}'s ghost envelope exists to prevent for map
 * markers. This is the body's version of that cure, and the one deliberate difference from the
 * minimap: a marker may linger as a frozen memory of an observation, but a lingering enemy
 * <i>body</i> would be a wallhack. So the body's envelope is much shorter — a fast fade-in, a
 * brief hold, a slower fade-out — and it ends at exactly 0 alpha: the player is not drawn at all,
 * not drawn dimly.
 *
 * <p><b>The wall clock, not the frame delta.</b> Ages are measured against
 * {@code System.currentTimeMillis()}, like the minimap and the kill feed, so one frame spike
 * cannot empty the fade map and a paused client cannot hoard it. Per-call advance is clamped, so
 * a long stall makes at most a bounded amount of fade progress before the age-based expiry
 * forces the sighting out.
 *
 * <p>State is a bounded map of player id → fade state, aged and pruned every frame, cleared on
 * the viewer's respawn (a new life starts with no sightings, exactly like the minimap's memory)
 * and at teardown. Only targets the viewer could call an enemy need an entry; allies and the
 * local player are never gated and never tracked.
 */
public final class SightingFade {

    /**
     * Seconds of continuous lit sighting taken to fade a body from invisible to fully drawn. Fast
     * on purpose: a target stepping into the cone should materialise as a smooth arrival, not a
     * blink. <b>Provisional</b> — the same family as {@link MinimapModel#GHOST_HOLD_SECONDS}, a
     * feel number not a design-doc one.
     */
    public static final float FADE_IN_SECONDS = 0.12f;

    /**
     * Seconds a lost sighting holds at its last alpha before fading. This is the hysteresis: a
     * target oscillating around the lit bar at the cone edge dips for a few frames and returns
     * without the body ever darkening. <b>Provisional</b>, with {@link #FADE_IN_SECONDS}.
     */
    public static final float HOLD_SECONDS = 0.2f;

    /**
     * Seconds the fade-out itself takes once the hold is spent. Slower than the fade-in, so a
     * genuinely lost target eases out instead of snatching away, but half a second — long enough
     * to read as motion, short enough that it cannot be followed as a live tracker. Total
     * post-loss visibility is {@code HOLD + FADE_OUT = 0.7 s}. <b>Provisional</b>.
     */
    public static final float FADE_OUT_SECONDS = 0.5f;

    /** Total life of one lost sighting, after which the body is not drawn at all. */
    public static final float LIFETIME_SECONDS = HOLD_SECONDS + FADE_OUT_SECONDS;

    /**
     * Upper bound on tracked sightings. Defensive rather than expected: only present, alive
     * enemies are tracked, so the player cap bounds it already. Bounded anyway, because an
     * unbounded map keyed by entity id would grow for the life of the process.
     */
    public static final int MAX_TRACKED = 64;

    /** One tracked sighting: its current draw alpha and the wall-clock stamps that steer it. */
    private static final class Memory {
        float alpha;
        long lastLitMillis;
        long lastUpdateMillis;

        Memory(long nowMillis) {
            this.lastLitMillis = nowMillis;
            this.lastUpdateMillis = nowMillis;
        }
    }

    /**
     * The live fade states, keyed by player id. Insertion-ordered so overflow drops the
     * least-recently-seen sighting first, the same reading {@link MinimapModel} gives its memory.
     */
    private final Map<Integer, Memory> memory = new LinkedHashMap<>();

    private boolean viewerWasAlive;

    /**
     * Ages every tracked sighting for one frame and returns the alpha the target should be drawn
     * with: 0 when it has never been lit or has expired, otherwise in {@code [0, 1]}.
     *
     * @param id         the target's player id
     * @param lit        this frame's gate answer: {@code ObserverSet.isLit} for an enemy
     * @param nowMillis  the wall clock the envelope is measured against
     */
    public float alphaFor(int id, boolean lit, long nowMillis) {
        Memory state = memory.get(id);
        if (state == null) {
            if (!lit) {
                return 0f;
            }
            state = new Memory(nowMillis);
            memory.put(id, state);
            evictOverflow();
            return 0f;
        }
        float dt = clamp((nowMillis - state.lastUpdateMillis) / 1000f, 0f, 0.25f);
        if (lit) {
            state.lastLitMillis = nowMillis;
            state.alpha = rise(state.alpha, dt / FADE_IN_SECONDS);
        } else {
            float age = (nowMillis - state.lastLitMillis) / 1000f;
            if (age >= LIFETIME_SECONDS) {
                memory.remove(id);
                return 0f;
            }
            if (age > HOLD_SECONDS) {
                state.alpha = fall(state.alpha, dt / FADE_OUT_SECONDS);
            }
            // Inside the hold the alpha freezes where it is: a dip shorter than HOLD_SECONDS
            // never darkens the body, which is the hysteresis the cone edge needs.
        }
        state.lastUpdateMillis = nowMillis;
        return state.alpha;
    }

    /** Drops one sighting: a target that stopped being an enemy, or is no longer drawn. */
    public void forget(int id) {
        memory.remove(id);
    }

    /**
     * Forgets every sighting whose target is not in {@code liveIds} — the alive, drawn targets of
     * this frame. The snapshot is unculled, so an absent player is a dead or departed one, not a
     * hidden one, and fading a body that no longer exists would misreport the world.
     */
    public void retain(Collection<Integer> liveIds) {
        if (liveIds == null || liveIds.isEmpty()) {
            memory.clear();
            return;
        }
        memory.keySet().retainAll(liveIds);
    }

    /**
     * A new life for the viewer wipes every sighting: the last life's fades are about positions
     * seen from where the player no longer is. Called on the dead→alive edge and at teardown, the
     * same transitions {@link MinimapModel#clear()} covers for the map.
     */
    public void onViewerAlive(boolean alive) {
        if (alive && !viewerWasAlive) {
            memory.clear();
        }
        viewerWasAlive = alive;
    }

    /** Forgets everything. Teardown, and the tests. */
    public void clear() {
        memory.clear();
    }

    /** Sightings tracked right now. Diagnostics and tests. */
    public int trackedCount() {
        return memory.size();
    }

    /** Linear approach to 1 over the given fraction of a fade-in; clamped to {@code [0, 1]}. */
    private static float rise(float alpha, float step) {
        return clamp(alpha + Math.max(0f, step), 0f, 1f);
    }

    /** Linear approach to 0 over the given fraction of a fade-out; clamped to {@code [0, 1]}. */
    private static float fall(float alpha, float step) {
        return clamp(alpha - Math.max(0f, step), 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Keeps {@link #memory} inside {@link #MAX_TRACKED}, forgetting the least recently lit. */
    private void evictOverflow() {
        while (memory.size() > MAX_TRACKED) {
            Integer oldest = null;
            long oldestLit = Long.MAX_VALUE;
            for (Map.Entry<Integer, Memory> entry : memory.entrySet()) {
                if (entry.getValue().lastLitMillis < oldestLit) {
                    oldestLit = entry.getValue().lastLitMillis;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) {
                return;
            }
            memory.remove(oldest);
        }
    }
}
