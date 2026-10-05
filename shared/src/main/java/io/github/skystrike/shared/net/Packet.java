package io.github.skystrike.shared.net;

/**
 * Marker for everything that crosses the wire.
 *
 * <p>Implementations are plain mutable classes with a public no-argument constructor: the
 * serialiser instantiates them reflectively, and the server reuses instances for high-frequency
 * traffic. They carry data only — no behaviour, no references to either side's runtime types.
 *
 * <p>Every implementation must be listed in {@link NetworkRegistration}.
 */
public interface Packet {
}
