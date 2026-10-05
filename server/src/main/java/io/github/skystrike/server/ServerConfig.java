package io.github.skystrike.server;

import io.github.skystrike.shared.config.NetConfig;
import io.github.skystrike.shared.config.WorldConfig;

/**
 * Everything the host process needs to know before it starts, parsed once from the command line.
 *
 * <p>Gameplay tuning does not live here — that belongs in {@code shared/config} where the client
 * can see it too. This is deployment configuration only.
 */
public record ServerConfig(
    int tcpPort,
    int udpPort,
    int maxPlayers,
    int tickRateHz,
    double profileIntervalSeconds) {

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
    }

    public static ServerConfig defaults() {
        return new ServerConfig(
            NetConfig.DEFAULT_TCP_PORT,
            NetConfig.DEFAULT_UDP_PORT,
            NetConfig.MAX_PLAYERS,
            WorldConfig.TICK_RATE_HZ,
            DEFAULT_PROFILE_INTERVAL_SECONDS);
    }

    /**
     * Parses {@code --key value} pairs, falling back to {@link #defaults()} for anything absent.
     *
     * <p>Recognised: {@code --tcp-port}, {@code --udp-port}, {@code --max-players},
     * {@code --tick-rate}, {@code --profile-interval}.
     */
    public static ServerConfig fromArgs(String[] args) {
        ServerConfig config = defaults();
        int tcpPort = config.tcpPort();
        int udpPort = config.udpPort();
        int maxPlayers = config.maxPlayers();
        int tickRateHz = config.tickRateHz();
        double profileInterval = config.profileIntervalSeconds();

        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + key);
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
                default -> throw new IllegalArgumentException("unknown option: " + key);
            }
        }
        return new ServerConfig(tcpPort, udpPort, maxPlayers, tickRateHz, profileInterval);
    }

    public static String usage() {
        return "options: --tcp-port <n> --udp-port <n> --max-players <n> --tick-rate <hz> "
            + "--profile-interval <seconds>";
    }
}
