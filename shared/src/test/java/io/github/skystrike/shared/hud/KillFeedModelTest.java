package io.github.skystrike.shared.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.net.s2c.PacketKillEvent;
import io.github.skystrike.shared.weapons.WeaponId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The feed as a widget needs it: newest first, capped, aged out, and aware of who you are. */
class KillFeedModelTest {

    private static final long T0 = 1_700_000_000_000L;

    private KillFeedModel feed;

    @BeforeEach
    void setUp() {
        feed = new KillFeedModel();
        feed.setLocalPlayerId(7);
    }

    private static PacketKillEvent kill(int killerId, String killer, int victimId, String victim) {
        return new PacketKillEvent(killerId, killer, victimId, victim,
            WeaponId.DEFAULT.ordinal(), false, false, false);
    }

    @Test
    @DisplayName("the line is the kill event's own wording, never a second rendering of it")
    void lineComesFromTheEvent() {
        PacketKillEvent event = kill(1, "Ada", 2, "Bo");
        feed.add(event, T0);
        List<KillFeedModel.Entry> visible = feed.visible(T0);
        assertEquals(1, visible.size());
        assertEquals(event.feedLine(), visible.get(0).line());
        assertEquals("Ada → Bo  " + WeaponId.DEFAULT.displayName(), visible.get(0).line());
    }

    @Test
    @DisplayName("newest first, and never more than five lines")
    void newestFirstAndCapped() {
        for (int i = 1; i <= 8; i++) {
            feed.add(kill(i, "Killer" + i, 100 + i, "Victim" + i), T0 + i);
        }
        List<KillFeedModel.Entry> visible = feed.visible(T0 + 10);
        assertEquals(KillFeedModel.MAX_VISIBLE, visible.size());
        assertTrue(visible.get(0).line().startsWith("Killer8"));
        assertTrue(visible.get(4).line().startsWith("Killer4"));
    }

    @Test
    @DisplayName("lines hold, then fade, then are gone")
    void ageing() {
        feed.add(kill(1, "Ada", 2, "Bo"), T0);
        assertEquals(1f, feed.visible(T0).get(0).alpha(T0), 1e-4f);

        long holdEnd = T0 + (long) (KillFeedModel.HOLD_SECONDS * 1000f);
        assertEquals(1f, feed.visible(holdEnd).get(0).alpha(holdEnd), 1e-4f);

        long halfFaded = holdEnd + (long) (KillFeedModel.FADE_SECONDS * 500f);
        assertEquals(0.5f, feed.visible(halfFaded).get(0).alpha(halfFaded), 1e-2f);

        long dead = T0 + (long) (KillFeedModel.LIFETIME_SECONDS * 1000f) + 1L;
        assertEquals(List.of(), feed.visible(dead));
        assertEquals(0, feed.size(), "reading the feed is what retires it");
    }

    @Test
    @DisplayName("your own kills and deaths are flagged, nobody else's are")
    void localInvolvement() {
        feed.add(kill(7, "You", 2, "Bo"), T0);
        feed.add(kill(3, "Cy", 7, "You"), T0);
        feed.add(kill(3, "Cy", 4, "Dee"), T0);

        List<KillFeedModel.Entry> visible = feed.visible(T0);
        assertEquals(3, visible.size());

        KillFeedModel.Entry theirs = visible.get(0);
        assertFalse(theirs.involvesLocal());

        KillFeedModel.Entry yourDeath = visible.get(1);
        assertTrue(yourDeath.localVictim());
        assertFalse(yourDeath.localKiller());
        assertTrue(yourDeath.involvesLocal());

        KillFeedModel.Entry yourKill = visible.get(2);
        assertTrue(yourKill.localKiller());
        assertFalse(yourKill.localVictim());
    }

    @Test
    @DisplayName("a suicide is the victim's line, not a kill credited to them")
    void suicideIsNotAKill() {
        feed.add(new PacketKillEvent(7, "You", 7, "You",
            WeaponId.DEFAULT.ordinal(), false, true, false), T0);
        KillFeedModel.Entry entry = feed.visible(T0).get(0);
        assertTrue(entry.selfInflicted());
        assertFalse(entry.localKiller(), "nobody earns a kill for dying");
        assertTrue(entry.localVictim());
    }

    @Test
    @DisplayName("null and blank events are ignored, and clear empties the feed")
    void defensive() {
        feed.add(null, T0);
        assertEquals(0, feed.size());

        feed.add(kill(1, "Ada", 2, "Bo"), T0);
        assertEquals(1, feed.size());
        feed.clear();
        assertEquals(0, feed.size());
        assertEquals(List.of(), feed.visible(T0));
    }

    @Test
    @DisplayName("with no local id nothing is flagged as yours")
    void noLocalIdYet() {
        KillFeedModel anonymous = new KillFeedModel();
        assertEquals(-1, anonymous.localPlayerId());
        anonymous.add(kill(-1, "Ada", -1, "Bo"), T0);
        KillFeedModel.Entry entry = anonymous.visible(T0).get(0);
        assertFalse(entry.localKiller());
        assertFalse(entry.localVictim());
    }
}
