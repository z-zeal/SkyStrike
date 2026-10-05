package io.github.skystrike.server;

import java.io.IOException;

/** Process entry point: parse arguments, start the host, shut it down cleanly. */
public final class ServerLauncher {

    private ServerLauncher() {
    }

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = ServerConfig.fromArgs(args);
        } catch (IllegalArgumentException invalid) {
            System.err.println("[server] " + invalid.getMessage());
            System.err.println("[server] " + ServerConfig.usage());
            System.exit(2);
            return;
        }

        GameServer server = new GameServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "skystrike-shutdown"));

        try {
            server.run();
        } catch (IOException failure) {
            System.err.println("[server] could not start: " + failure.getMessage());
            System.exit(1);
        }
    }
}
