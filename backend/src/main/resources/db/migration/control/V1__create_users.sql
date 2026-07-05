-- Control DB (ADR-010): users + user_links.
-- Portable across Postgres and H2 PostgreSQL-mode (used by tests).

CREATE TABLE users (
    id                BIGINT                   NOT NULL PRIMARY KEY,
    email             VARCHAR(320)             NOT NULL,
    email_normalized  VARCHAR(320)             NOT NULL,
    password_hash     VARCHAR(72)              NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_users_email_normalized UNIQUE (email_normalized)
);

-- Written by Track D's link-events consumer (ADR-008); read here for
-- ownership checks and the /api/me/links listing.
CREATE TABLE user_links (
    user_id     BIGINT                   NOT NULL,
    short_code  VARCHAR(32)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_links PRIMARY KEY (short_code)
);

CREATE INDEX ix_user_links_user_id_created_at ON user_links (user_id, created_at DESC);
