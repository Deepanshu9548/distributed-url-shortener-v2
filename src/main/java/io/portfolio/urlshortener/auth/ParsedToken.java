package io.portfolio.urlshortener.auth;

import java.time.Instant;

/**
 * Post-verification view of a JWT: identity + jti + remaining life, no
 * signing material. Type-narrowed by {@link Type} so callers can enforce
 * "must be access" or "must be refresh".
 */
public record ParsedToken(
        Type type,
        long userId,
        String email,
        String jti,
        String sessionId,   // refresh only, null on access
        Instant expiresAt) {

    public enum Type { ACCESS, REFRESH }
}
