package io.github.skystrike.server.combat;

import io.github.skystrike.shared.gadget.GadgetRegistry;
import io.github.skystrike.shared.model.Player;
import io.github.skystrike.shared.model.Team;
import io.github.skystrike.shared.utility.UtilityRegistry;
import io.github.skystrike.shared.weapons.WeaponRegistry;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Records deaths and hands them to the broadcaster.
 *
 * <p>Two queues on purpose: {@link #drain()} gives the networking layer each event exactly once,
 * while {@link #recent()} keeps a short bounded history for the server console and for clients
 * that join mid-match.
 */
public final class KillFeedService {

    /** Default number of past kills kept for late joiners and the console. */
    public static final int DEFAULT_HISTORY = 16;

    /**
     * One death.
     *
     * @param killerId      the player credited, or the victim's own id when self-inflicted
     * @param weaponId      shared weapon-family wire id (gun, melee, utility or gadget)
     * @param friendlyFire  killer and victim shared a team
     */
    public record KillEvent(
        int killerId,
        String killerName,
        int victimId,
        String victimName,
        int weaponId,
        boolean headshot,
        boolean selfInflicted,
        boolean friendlyFire
    ) {
        public WeaponId weapon() {
            return WeaponId.fromOrdinal(weaponId);
        }

        /** Display name for guns, utilities, melee weapons and gadget wire ids. */
        public String weaponDisplayName() {
            String gadget = GadgetRegistry.displayNameForWireId(weaponId);
            if (!gadget.isEmpty()) {
                return gadget;
            }
            String utility = UtilityRegistry.displayNameForWireId(weaponId);
            return utility.isEmpty() ? WeaponRegistry.displayNameForWireId(weaponId) : utility;
        }
    }

    private final Deque<KillEvent> pending = new ArrayDeque<>();
    private final Deque<KillEvent> history = new ArrayDeque<>();
    private final int historyLimit;

    public KillFeedService() {
        this(DEFAULT_HISTORY);
    }

    public KillFeedService(int historyLimit) {
        this.historyLimit = Math.max(1, historyLimit);
    }

    /** Records a death. {@code killer} may be the victim (self-inflicted) or {@code null}. */
    public KillEvent recordKill(Player killer, Player victim, int weaponId, boolean headshot) {
        if (victim == null) {
            return null;
        }
        boolean selfInflicted = killer == null || killer.id == victim.id;
        boolean friendlyFire = !selfInflicted
            && Team.areAllies(killer.teamIndex, victim.teamIndex);

        KillEvent event = new KillEvent(
            selfInflicted ? victim.id : killer.id,
            selfInflicted ? victim.name : killer.name,
            victim.id,
            victim.name,
            weaponId,
            headshot,
            selfInflicted,
            friendlyFire);

        pending.addLast(event);
        history.addLast(event);
        while (history.size() > historyLimit) {
            history.pollFirst();
        }
        return event;
    }

    /** Returns every event since the last call and clears the queue. */
    public List<KillEvent> drain() {
        if (pending.isEmpty()) {
            return Collections.emptyList();
        }
        List<KillEvent> events = new ArrayList<>(pending);
        pending.clear();
        return events;
    }

    /** The bounded history, oldest first. */
    public List<KillEvent> recent() {
        return List.copyOf(history);
    }

    public int pendingCount() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
        history.clear();
    }
}
