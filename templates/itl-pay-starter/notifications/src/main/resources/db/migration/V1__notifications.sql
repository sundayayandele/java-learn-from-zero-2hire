-- Idempotent consumer (topic 11.3): an event id is recorded in the same transaction as its effect.
CREATE TABLE processed_event (
    event_id      UUID          PRIMARY KEY,
    processed_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    id            BIGSERIAL     PRIMARY KEY,
    reference     TEXT          NOT NULL,
    account_id    TEXT          NOT NULL,
    message       TEXT          NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
