-- Control DB: analytics and idempotency tables (ADR-007, ADR-008).

CREATE TABLE raw_click_events (
    event_id   UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_raw_click_events PRIMARY KEY (event_id)
);

CREATE TABLE link_stats (
    short_code     VARCHAR(32)              NOT NULL,
    click_count    BIGINT                   NOT NULL DEFAULT 0,
    last_click_at  TIMESTAMP WITH TIME ZONE,
    last_referrer  VARCHAR(1024),
    CONSTRAINT pk_link_stats PRIMARY KEY (short_code)
);

CREATE TABLE raw_link_events (
    event_id   UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_raw_link_events PRIMARY KEY (event_id)
);
