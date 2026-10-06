package io.github.skystrike.shared.command;

/**
 * A parsed player selector: the raw token plus the numeric id when the token was one.
 *
 * <p>Resolution against an authoritative roster happens at execution time on the side that owns
 * the roster. This record deliberately carries no identity beyond what the caller typed, so a
 * client cannot launder a guess into an authoritative id.
 *
 * @param token the token as typed, for messages
 * @param id the player id, or {@code -1} when the token was a name
 */
public record PlayerRef(String token, int id) {

    public boolean hasId() {
        return id >= 0;
    }

    @Override
    public String toString() {
        return token;
    }
}
