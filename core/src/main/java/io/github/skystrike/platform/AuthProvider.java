package io.github.skystrike.platform;

import java.util.Optional;

/** Refresh-token persistence hook only; does not implement authentication. */
public interface AuthProvider {
    Optional<String> loadRefreshToken();
    void saveRefreshToken(String token);
    void clearRefreshToken();
}
