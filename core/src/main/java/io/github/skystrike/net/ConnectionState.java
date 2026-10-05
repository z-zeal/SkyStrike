package io.github.skystrike.net;

/** Where the client is in the connect-and-join sequence. */
public enum ConnectionState {

    /** No socket, nothing in flight. */
    OFFLINE("offline"),

    /** Dialling: the transport handshake is in progress. */
    CONNECTING("connecting"),

    /** Transport is up; the join handshake has been sent and not yet answered. */
    JOINING("joining"),

    /** Joined. The server has given us a player id and is sending snapshots. */
    JOINED("joined"),

    /** The attempt failed or the connection dropped. Holds a reason for display. */
    FAILED("failed");

    private final String label;

    ConnectionState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
