-- V1: per-shard schema for the core shortener (Track A).
-- Applied programmatically per shard by Track B's Flyway wiring
-- (spring.flyway.enabled=false; see ShardDataSourceConfig).

CREATE TABLE links (
    id              BIGINT PRIMARY KEY,                   -- Snowflake, minted in-process (ADR-001)
    short_code      VARCHAR(32)   NOT NULL,               -- Base62 code or custom alias; shard routing key
    long_url        VARCHAR(8192) NOT NULL,               -- max length frozen in ADR-011
    user_id         BIGINT,                               -- nullable until auth (Track C) populates it
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,                          -- NULL = never expires
    is_custom_alias BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX ux_links_short_code ON links (short_code);

-- Idempotency-Key -> short_code for POST /api/links (ADR-011, 24h window).
-- Cleanup of rows older than 24h is out of scope for M1; documented follow-up:
-- periodic DELETE FROM idempotency_keys WHERE created_at < now() - interval '24 hours'.
CREATE TABLE idempotency_keys (
    key         VARCHAR(128) PRIMARY KEY,
    short_code  VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
