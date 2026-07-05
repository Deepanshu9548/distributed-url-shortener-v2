package io.portfolio.urlshortener.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * CONTRACT — frozen post-M0. Link lifecycle event schema, version 1.
 *
 * <p>Feeds the control-DB user_links index asynchronously (replaces the
 * dual-write from the create path — ADR-008) and fans out cache invalidation.
 * Same idempotency mechanism as {@link ClickEvent}: consumer dedupes on
 * {@code eventId}.
 */
public record LinkEvent(
        UUID eventId,
        Type type,
        String shortCode,
        Long userId,
        Instant timestamp,
        String requestId) {

    public enum Type { CREATED, UPDATED, DELETED }

    public static LinkEvent of(Type type, String shortCode, Long userId, String requestId) {
        return new LinkEvent(UUID.randomUUID(), type, shortCode, userId, Instant.now(), requestId);
    }
}
