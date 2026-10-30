-- The transfer saga's state, and the outbox its events leave through (topic 11.3).
CREATE TABLE transfer (
    reference     TEXT          PRIMARY KEY,                  -- the business key: one row per transfer, ever
    from_account  TEXT          NOT NULL,
    to_account    TEXT          NOT NULL,
    amount        NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    state         TEXT          NOT NULL CHECK (state IN ('REQUESTED', 'RESERVED', 'CLEARED', 'REJECTED', 'FAILED')),
    reason        TEXT,
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id            BIGSERIAL     PRIMARY KEY,                  -- publish order
    topic         TEXT          NOT NULL,
    msg_key       TEXT          NOT NULL,
    payload       TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX outbox_unpublished ON outbox (id) WHERE published_at IS NULL;
