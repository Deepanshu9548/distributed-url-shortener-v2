package io.portfolio.urlshortener.shortener;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** Response body for link creation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        Instant expiresAt) {
}
