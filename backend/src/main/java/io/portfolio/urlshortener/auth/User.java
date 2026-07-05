package io.portfolio.urlshortener.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Control-DB user (ADR-010). Snowflake id (reuses the shortener generator).
 * Email is stored twice: {@code email} preserves the caller's casing,
 * {@code emailNormalized} (lowercased) is the unique-lookup index.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private Long id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "email_normalized", nullable = false, length = 320, unique = true)
    private String emailNormalized;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    public User(Long id, String email, String emailNormalized, String passwordHash, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
