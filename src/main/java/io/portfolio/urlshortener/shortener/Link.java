package io.portfolio.urlshortener.shortener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A shortened link, sharded by {@code short_code} (routing key — ADR-002).
 * The id is a pre-minted Snowflake (never DB-generated, ADR-001).
 */
@Entity
@Table(name = "links")
public class Link {

    @Id
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 32)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 8192)
    private String longUrl;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_custom_alias", nullable = false)
    private boolean customAlias;

    protected Link() {
        // for JPA
    }

    public Link(Long id, String shortCode, String longUrl, Long userId,
                Instant createdAt, Instant expiresAt, boolean customAlias) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }
}
