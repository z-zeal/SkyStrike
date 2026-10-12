package io.github.skystrike.server.net.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.map.ArenaMapFormat;
import io.github.skystrike.shared.net.s2c.PacketJoinReject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The join-time map gate (protocol 16).
 *
 * <p>The decision is a pure function of the client's stated hash and the server's authored map
 * precisely so it can be tested as one: the handler around it needs a socket, a Kryo connection and
 * a player registry, while the gate itself is the whole risk. A wrong answer here is silent for a
 * whole match — the destruction baseline names blocks by authored index, so a client admitted on a
 * different map would remove the wrong blocks for as long as it plays.
 */
class JoinRequestHandlerTest {

    @Test
    @DisplayName("the authored map's own hash is admitted, and no other tuple of hex is")
    void onlyTheAuthoredMapIsAdmitted() {
        ArenaMap arena = ArenaMap.standard();

        assertNull(
            JoinRequestHandler.mapRefusal(arena.mapHashHex(), arena),
            "a client that states the server's map joins");

        PacketJoinReject refused = JoinRequestHandler.mapRefusal("0".repeat(64), arena);
        assertNotNull(refused);
        assertEquals(PacketJoinReject.REASON_MAP_MISMATCH, refused.reason);
        assertEquals(
            arena.mapHashHex(),
            refused.serverMapHashHex,
            "the refusal names the map the server plays, or the player has nothing to compare");
    }

    @Test
    @DisplayName("a join that states no map is refused rather than treated as a wildcard")
    void anUnstatedMapIsRefused() {
        ArenaMap arena = ArenaMap.standard();

        assertEquals(
            PacketJoinReject.REASON_MAP_MISMATCH,
            JoinRequestHandler.mapRefusal(null, arena).reason,
            "a null stated hash is an unstated map, not a match");
        assertEquals(
            PacketJoinReject.REASON_MAP_MISMATCH,
            JoinRequestHandler.mapRefusal("", arena).reason,
            "the wire's empty default is an unstated map too");
    }

    @Test
    @DisplayName("a map that differs only in the order its lines were authored still matches")
    void lineOrderDoesNotChangeTheIdentity() {
        // The gate compares hashes, and MapHash sorts before hashing, so a re-authored file with
        // the same contents keeps its identity — the same tolerance the SDF header guard keeps.
        String first = String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "centred wall stone 0 0 3000 100",
            "mirrored wall stone 120 200 120 18",
            "mirrored door metal 1034 100 36 170 id=1 access=ALL",
            "");
        String shuffled = String.join("\n",
            "skystrike-map 1",
            "mirror 1500",
            "mirrored door metal 1034 100 36 170 id=1 access=ALL",
            "mirrored wall stone 120 200 120 18",
            "centred wall stone 0 0 3000 100",
            "");
        ArenaMap one = ArenaMapFormat.parse(first);
        ArenaMap other = ArenaMapFormat.parse(shuffled);

        assertEquals(one.mapHashHex(), other.mapHashHex());
        assertNull(
            JoinRequestHandler.mapRefusal(other.mapHashHex(), one),
            "the shuffled copy states the same map, so it joins the original");
    }
}
