package io.portfolio.urlshortener.auth;

/**
 * Signed access + refresh tokens returned together from
 * {@link JwtService#issue(User, String)} and from
 * {@link RefreshTokenService#rotate(String)}. {@code sessionId} is the
 * refresh token's {@code sid} claim — retained so callers can register
 * it in Redis without re-parsing the token.
 */
public record TokenPair(String accessToken, String refreshToken, String sessionId, String accessJti) {
}
