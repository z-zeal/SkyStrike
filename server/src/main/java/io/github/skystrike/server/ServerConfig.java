package io.github.skystrike.server;

import io.github.skystrike.shared.command.Permission;
import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.config.WorldConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the host process needs to know before it starts, parsed once from the command line.
 *
 * <p>Gameplay tuning does not live here — that belongs in {@code shared/config} where the client
 * can see it too. This is deployment configuration only.
 *
 * @param devMode dev host mode: every joined player resolves to admin (playable build plan
 *     M1 §2.4). Off by default; never the silent state of a real host.
 * @param grants  static permission grants by player name ({@code --grant name=LEVEL}), the
 *     stand-in for authenticated identity until the auth service exists.
 */
public record ServerConfig(
    int tcpPort,
    int udpPort,
    int maxPlayers,
    int tickRateHz,
    double profileIntervalSeconds,
    boolean devMode,
    Map<String, Permission> grants) {

    public static final double DEFAULT_PROFILE_INTERVAL_SECONDS = 5.0;

    public ServerConfig {
        if (tcpPort <= 0 || tcpPort > 65535) {
            throw new IllegalArgumentException("tcpPort out of range: " + tcpPort);
        }
        if (udpPort <= 0 || udpPort > 65535) {
            throw new IllegalArgumentException("udpPort out of range: " + udpPort);
        }
        if (maxPlayers <= 0) {
            throw new IllegalArgumentException("maxPlayers must be positive: " + maxPlayers);
        }
        if (tickRateHz <= 0) {
            throw new IllegalArgumentException("tickRateHz must be positive: " + tickRateHz);
        }
        if (profileIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                "profileIntervalSeconds must not be negative: " + profileIntervalSeconds);
        }
        grants = grants == null ? Map.of() : Map.copyOf(grants);
    }

    /** Convenience for tests and embedders: the five original fields, no dev flags. */
    public ServerConfig(
        int tcpPort, int udpPort, int maxPlayers, int tickRateHz, double profileIntervalSeconds) {
        this(tcpPort, udpPort, maxPlayers, tickRateHz, profileIntervalSeconds, false, Map.of());
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
            NetConfig.DEFAULT_TCP_PORT,
            NetConfig.DEFAULT_UDP_PORT,
            NetConfig.MAX_PLAYERS,
            WorldConfig.TICK_RATE_HZ,
            DEFAULT_PROFILE_INTERVAL_SECONDS,
            true,
            Map.of());
    }

    /**
     * Parses {@code --key value} pairs plus the valueless {@code --dev}, falling back to
     * {@link #defaults()} for anything absent.
     *
     * <p>Recognised: {@code --tcp-port}, {@code --udp-port}, {@code --max-players},
     * {@code --tick-rate}, {@code --profile-interval}, {@code --dev},
     * {@code --grant name=LEVEL} (repeatable).
     */
    public static ServerConfig fromArgs(String[] args) {
        ServerConfig config = defaults();
        int tcpPort = config.tcpPort();
        int udpPort = config.udpPort();
        int maxPlayers = config.maxPlayers();
        int tickRateHz = config.tickRateHz();
        double profileInterval = config.profileIntervalSeconds();
        boolean devMode = config.devMode();
        Map<String, Permission> grants = new LinkedHashMap<>(config.grants());

        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + key);
            }
            if ("--dev".equals(key)) {
                devMode = true;
                continue;
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("missing value for " + key);
            }
            String value = args[++i];
            switch (key) {
                case "--tcp-port" -> tcpPort = Integer.parseInt(value);
                case "--udp-port" -> udpPort = Integer.parseInt(value);
                case "--max-players" -> maxPlayers = Integer.parseInt(value);
                case "--tick-rate" -> tickRateHz = Integer.parseInt(value);
                case "--profile-interval" -> profileInterval = Double.parseDouble(value);
                case "--grant" -> parseGrant(value, grants);
                default -> throw new IllegalArgumentException("unknown option: " + key);
            }
        }
        return new ServerConfig(
            tcpPort, udpPort, maxPlayers, tickRateHz, profileInterval, devMode, grants);
    }

    /** One {@code name=LEVEL} grant; a misspelled level is a startup error, never a silent admin. */
    private static void parseGrant(String value, Map<String, Permission> grants) {
        int split = value.indexOf('=');
        if (split <= 0 || split == value.length() - 1) {
            throw new IllegalArgumentException(
                "--grant expects name=LEVEL, got '" + value + "'");
        }
        String name = value.substring(0, split).trim();
        Permission level = Permission.parse(value.substring(split + 1));
        if (level == null) {
            throw new IllegalArgumentException(
                "--grant level must be one of everyone/player/moderator/admin, got '" + value + "'");
        }
        grants.put(name, level);
    }

    public static String usage() {
        return "options: --tcp-port <n> --udp-port <n> --max-players <n> --tick-rate <hz> "
            + "--profile-interval <seconds> --dev --grant <name>=<level>";
    }
}
