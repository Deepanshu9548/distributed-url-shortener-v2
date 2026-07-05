package io.portfolio.urlshortener.shortener;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Public link metadata for GET /api/links/{shortCode}. No ownership check yet —
 * Track C (auth) adds authorization.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkMetadataResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        boolean customAlias) {
}
