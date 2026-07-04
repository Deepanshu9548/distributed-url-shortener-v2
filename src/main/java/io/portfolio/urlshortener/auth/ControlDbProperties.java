package io.portfolio.urlshortener.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds the {@code app.control-db} section — the users / user_links database
 * (ADR-010). Explicit, separate from the shard datasources so the control
 * plane never lands on the read/write hot path.
 */
@ConfigurationProperties(prefix = "app.control-db")
public record ControlDbProperties(
        String jdbcUrl,
        String username,
        String password,
        @DefaultValue("10") int poolSize) {
}
