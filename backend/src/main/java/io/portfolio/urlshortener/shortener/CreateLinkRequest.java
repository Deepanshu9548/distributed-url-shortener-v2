package io.portfolio.urlshortener.shortener;

import java.time.Instant;

/**
 * Create-link request body. {@code expiresAt} (absolute) wins over
 * {@code ttlSeconds} (relative) when both are present. {@code customAlias} is
 * optional; validation rules in {@link UrlValidator}.
 */
public record CreateLinkRequest(
        String longUrl,
        String customAlias,
        Instant expiresAt,
        Long ttlSeconds) {
}
