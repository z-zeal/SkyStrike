package io.github.skystrike.net;

/**
 * One transport notification, captured on the network thread and replayed on the render thread.
 *
 * <p>Lifecycle changes travel through the same queue as packets so they stay in order: a
 * disconnect that overtook the last packet before it would make the client's state machine
 * disagree with what actually happened.
 */
public record ClientEvent(Type type, Object payload) {

    public enum Type {
        /** The transport handshake finished. */
        CONNECTED,
        /** The connection ended, cleanly or otherwise. */
        DISCONNECTED,
        /** A packet arrived; {@link #payload()} holds it. */
        RECEIVED,
        /** Connecting failed; {@link #payload()} holds a human-readable reason. */
        FAILED
    }

    public static ClientEvent connected() {
        return new ClientEvent(Type.CONNECTED, null);
    }

    public static ClientEvent disconnected() {
        return new ClientEvent(Type.DISCONNECTED, null);
    }

    public static ClientEvent received(Object payload) {
        return new ClientEvent(Type.RECEIVED, payload);
    }

    public static ClientEvent failed(String reason) {
        return new ClientEvent(Type.FAILED, reason);
    }
}
