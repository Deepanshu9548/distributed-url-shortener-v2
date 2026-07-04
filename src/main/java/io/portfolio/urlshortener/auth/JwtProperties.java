package io.portfolio.urlshortener.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * JWT settings (ADR-009). {@code secret} must be at least 32 bytes for HS256.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT15M") Duration accessTtl,
        @DefaultValue("P7D") Duration refreshTtl,
        @DefaultValue("url-shortener") String issuer) {

    public JwtProperties {
        // Deliberately lax: an unset/empty secret is a signal that auth is
        // OFF for this run (SecurityConfig is @ConditionalOnExpression on
        // the secret being non-empty). If we throw here, that whole
        // "auth disabled" mode couldn't even bind the property record.
        // When auth IS on, the JwtService constructor enforces the length.
        if (secret != null && !secret.isBlank() && secret.getBytes().length < 32) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be >= 32 bytes for HS256 (got " + secret.getBytes().length + ")");
        }
    }
}
