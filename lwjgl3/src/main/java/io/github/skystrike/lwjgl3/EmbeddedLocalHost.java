package io.github.skystrike.lwjgl3;

import io.github.skystrike.LocalHost;
import io.github.skystrike.server.GameServer;
import io.github.skystrike.server.ServerConfig;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Desktop adapter that runs the local test server beside the LWJGL client. */
public final class EmbeddedLocalHost implements LocalHost {

    private static final long STARTUP_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long SHUTDOWN_WAIT_MILLIS = 1_000L;

    private GameServer server;
    private Thread serverThread;
    private volatile IOException startupFailure;

    /**
     * Starts the default local server on a daemon thread and waits for both network channels to bind.
     * Readiness comes from the server's bound state rather than a throwaway TCP probe, which would
     * enqueue a synthetic connection on the real accept path.
     */
    @Override
    public synchronized void start() throws IOException, InterruptedException {
        if (server != null && server.isStarted()) {
            return;
        }
        if (serverThread != null && serverThread.isAlive()) {
            awaitReady(server, serverThread);
            return;
        }

        GameServer nextServer = new GameServer(ServerConfig.fromArgs(new String[0]));
        server = nextServer;
        startupFailure = null;
        Thread nextThread = new Thread(() -> runServer(nextServer), "skystrike-embedded-server");
        nextThread.setDaemon(true);
        serverThread = nextThread;
        nextThread.start();
        awaitReady(nextServer, nextThread);
    }

    private void runServer(GameServer target) {
        try {
            target.run();
        } catch (IOException failure) {
            startupFailure = failure;
        }
    }

    private void awaitReady(GameServer target, Thread worker) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT_NANOS;
        try {
            while (!target.isStarted()) {
                if (!worker.isAlive()) {
                    throw new IOException("Embedded server exited before binding its ports.", startupFailure);
                }
                if (System.nanoTime() >= deadline) {
                    throw new IOException("Timed out waiting for the embedded server to bind its ports.",
                        startupFailure);
                }
                Thread.sleep(10L);
            }
        } catch (InterruptedException interrupted) {
            stop();
            throw interrupted;
        } catch (IOException failure) {
            if (!target.isStarted()) {
                stop();
            }
            throw failure;
        }
    }

    /** Stops only the server started by this adapter; repeated calls are harmless. */
    @Override
    public synchronized void stop() {
        GameServer currentServer = server;
        Thread currentThread = serverThread;
        server = null;
        serverThread = null;
        startupFailure = null;

        if (currentServer != null) {
            currentServer.stop();
        }
        if (currentThread != null && currentThread != Thread.currentThread()) {
            currentThread.interrupt();
            try {
                currentThread.join(SHUTDOWN_WAIT_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
