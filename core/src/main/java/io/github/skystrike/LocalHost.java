package io.github.skystrike;

import java.io.IOException;

/**
 * Desktop-only seam for starting and stopping an embedded local game server.
 *
 * <p>The core application knows only this lifecycle contract; the desktop module supplies the
 * implementation so core never depends on the server module. Platforms without a local host pass
 * no implementation and do not expose the debug menu action.
 */
public interface LocalHost {

    /** Starts the host and waits until it has bound, or fails after a bounded startup attempt. */
    void start() throws IOException, InterruptedException;

    /** Requests shutdown. Implementations must make this safe to call more than once. */
    void stop();
}
