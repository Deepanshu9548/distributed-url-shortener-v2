package io.portfolio.urlshortener.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * The current caller's identity as extracted from a validated access token.
 * Populated by {@link JwtAuthenticationFilter} and made available to
 * controllers via {@link AuthenticatedUserResolver}. Not a Spring Security
 * principal — just a thin carrier for the fields controllers actually use.
 */
public record AuthenticatedUser(long userId, String email, String jti, Instant expiresAt) {

    /** Remaining life of this access token; zero if already expired. */
    public Duration remaining() {
        Duration d = Duration.between(Instant.now(), expiresAt);
        return d.isNegative() ? Duration.ZERO : d;
    }
}
