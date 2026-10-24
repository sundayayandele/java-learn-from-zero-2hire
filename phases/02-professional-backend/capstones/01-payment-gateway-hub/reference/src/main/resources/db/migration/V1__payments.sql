-- Payments, their immutable history, idempotency keys, webhook deduplication and refunds.
CREATE TABLE payment (
    id                 BIGSERIAL PRIMARY KEY,
    reference          VARCHAR(40)  NOT NULL UNIQUE,
    merchant_id        VARCHAR(64)  NOT NULL,
    amount_minor       BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency           CHAR(3)      NOT NULL,
    email              VARCHAR(254) NOT NULL,
    provider           VARCHAR(40),
    status             VARCHAR(20)  NOT NULL CHECK (status IN
                         ('INITIATED','PENDING','SUCCEEDED','FAILED','ABANDONED','PARTIALLY_REFUNDED','REFUNDED')),
    authorization_url  VARCHAR(500),
    refunded_minor     BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT refunds_within_amount CHECK (refunded_minor >= 0 AND refunded_minor <= amount_minor)
);
CREATE INDEX payment_merchant_page ON payment (merchant_id, created_at DESC, id DESC);
CREATE INDEX payment_status_age ON payment (status, updated_at);

CREATE TABLE payment_event (
    id                BIGSERIAL PRIMARY KEY,
    payment_id        BIGINT      NOT NULL REFERENCES payment (id),
    from_status       VARCHAR(20),
    to_status         VARCHAR(20) NOT NULL,
    cause             VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(120),
    note              VARCHAR(300),
    created_at        TIMESTAMPTZ NOT NULL
);
CREATE INDEX payment_event_payment ON payment_event (payment_id, id);

-- Postings-style immutability: history rows can't be changed or removed.
CREATE FUNCTION forbid_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'payment_event rows are append-only';
END $$;
CREATE TRIGGER payment_event_append_only BEFORE UPDATE OR DELETE ON payment_event
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TABLE idempotency_key (
    merchant_id   VARCHAR(64)  NOT NULL,
    idem_key      VARCHAR(100) NOT NULL,
    request_hash  CHAR(64)     NOT NULL,
    response_json TEXT,
    expires_at    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (merchant_id, idem_key)
);

CREATE TABLE webhook_event (
    provider    VARCHAR(40)  NOT NULL,
    event_id    VARCHAR(120) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (provider, event_id)
);

CREATE TABLE refund (
    id                 BIGSERIAL PRIMARY KEY,
    payment_id         BIGINT      NOT NULL REFERENCES payment (id),
    amount_minor       BIGINT      NOT NULL CHECK (amount_minor > 0),
    provider_refund_id VARCHAR(80),
    status             VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);
