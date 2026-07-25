package io.portfolio.urlshortener.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Control-DB ownership index (ADR-008). Written by Track D's link-events
 * consumer; read here for ownership checks and the paged listing endpoint.
 * {@code shortCode} is the primary key — one owner per code.
 */
@Entity
@Table(name = "user_links")
public class UserLink {

    @Id
    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserLink() {
        // for JPA
    }

    public UserLink(String shortCode, Long userId, Instant createdAt) {
        this.shortCode = shortCode;
        this.userId = userId;
        this.createdAt = createdAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
