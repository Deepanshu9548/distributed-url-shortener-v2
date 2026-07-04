package io.portfolio.urlshortener.shortener;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sharded repository — calls MUST run inside {@code ShardRouter} operations
 * keyed by the idempotency key string.
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {
}
