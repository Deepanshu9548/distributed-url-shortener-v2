package io.portfolio.urlshortener.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency-Key → short_code mapping for the create endpoint (ADR-011).
 * Rows are honored for 24h ({@code ShortenService.IDEMPOTENCY_WINDOW});
 * physical cleanup of expired rows is out of scope for M1 — a periodic
 * {@code DELETE WHERE created_at < now() - interval '24 hours'} job is the
 * documented follow-up.
 *
 * <p>Sharded by the idempotency key itself (stable hashing means a client
 * retry lands on the same shard); the referenced link may live on a different
 * shard — the short code stored here is the routing key to find it.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "key", length = 128)
    private String key;

    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
        // for JPA
    }

    public IdempotencyKey(String key, String shortCode, Instant createdAt) {
        this.key = key;
        this.shortCode = shortCode;
        this.createdAt = createdAt;
    }

    public String getKey() {
        return key;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
