package io.github.skystrike.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.net.s2c.PacketCapabilities;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CapabilityBroadcasterTest {

    private record Sent(int playerId, Permission level, boolean consoleAccess) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private PermissionResolver resolver;
    private CapabilityBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        sent.clear();
        resolver = new PermissionResolver();
        broadcaster = new CapabilityBroadcaster(
            resolver, (playerId, packet) -> sent.add(new Sent(playerId, packet.level, packet.consoleAccess)));
    }

    @Test
    @DisplayName("every join is told its capability, even when the answer is no")
    void joinAlwaysPushes() {
        PacketCapabilities packet = broadcaster.pushOnJoin(1, "Nova");

        assertEquals(Permission.PLAYER, packet.level);
        assertFalse(packet.consoleAccess);
        assertEquals(List.of(new Sent(1, Permission.PLAYER, false)), sent);
    }

    @Test
    @DisplayName("an unchanged capability is not pushed again")
    void redundantPushesAreSuppressed() {
        broadcaster.pushOnJoin(1, "Nova");
        sent.clear();

        assertFalse(broadcaster.pushIfChanged(1, "Nova"));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("a mid-match promotion is pushed immediately, with no reconnect")
    void promotionPushesImmediately() {
        broadcaster.pushOnJoin(1, "Nova");
        sent.clear();

        assertTrue(broadcaster.promote(1, "Nova", Permission.MODERATOR));
        assertEquals(List.of(new Sent(1, Permission.MODERATOR, true)), sent);
        assertTrue(broadcaster.lastSentTo(1).consoleAccess);
    }

    @Test
    @DisplayName("a demotion is pushed too, so the console disappears mid-match")
    void demotionPushesToo() {
        broadcaster.promote(1, "Nova", Permission.ADMIN);
        sent.clear();

        assertTrue(broadcaster.promote(1, "Nova", Permission.PLAYER));
        assertEquals(List.of(new Sent(1, Permission.PLAYER, false)), sent);
    }

    @Test
    @DisplayName("promoting to the same level pushes nothing")
    void repeatedPromotionIsSilent() {
        broadcaster.promote(1, "Nova", Permission.MODERATOR);
        sent.clear();

        assertFalse(broadcaster.promote(1, "Nova", Permission.MODERATOR));
        assertTrue(sent.isEmpty());
    }

    @Test
    @DisplayName("the configured threshold decides, not the broadcaster")
    void thresholdDrivesTheFlag() {
        resolver.setConsoleThreshold(Permission.PLAYER);
        PacketCapabilities packet = broadcaster.pushOnJoin(1, "Nova");

        assertEquals(Permission.PLAYER, packet.level);
        assertTrue(packet.consoleAccess);
    }

    @Test
    @DisplayName("forgetting a player drops both the dedupe cache and the runtime promotion")
    void forgettingClearsSessionState() {
        broadcaster.promote(1, "Nova", Permission.ADMIN);
        broadcaster.forget(1);

        assertNull(broadcaster.lastSentTo(1));
        assertEquals(Permission.PLAYER, resolver.resolve(1, "Nova"));

        sent.clear();
        broadcaster.pushOnJoin(1, "Nova");
        assertEquals(List.of(new Sent(1, Permission.PLAYER, false)), sent);
    }
}
