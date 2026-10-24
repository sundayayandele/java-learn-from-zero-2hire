CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,      -- Keycloak preferred_username (usernames are not editable in the realm)
    full_name   VARCHAR(200) NOT NULL
);

CREATE TABLE account (
    id                VARCHAR(40)    PRIMARY KEY,
    customer_id       BIGINT         NOT NULL REFERENCES customer (id),
    account_number    CHAR(10)       NOT NULL UNIQUE,
    account_type      VARCHAR(20)    NOT NULL CHECK (account_type IN ('CURRENT','SAVINGS','DOMICILIARY')),
    currency          CHAR(3)        NOT NULL,
    nickname          VARCHAR(100)   NOT NULL,
    booked_balance    NUMERIC(19, 2) NOT NULL,
    available_balance NUMERIC(19, 2) NOT NULL
);
CREATE INDEX account_customer ON account (customer_id);

CREATE TABLE txn (
    id                   BIGSERIAL      PRIMARY KEY,
    account_id           VARCHAR(40)    NOT NULL REFERENCES account (id),
    booked_at            TIMESTAMPTZ    NOT NULL,
    amount               NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    credit_debit         VARCHAR(6)     NOT NULL CHECK (credit_debit IN ('CREDIT','DEBIT')),
    counterparty_name    VARCHAR(200),
    counterparty_account VARCHAR(20),
    narration            VARCHAR(200),
    balance_after        NUMERIC(19, 2) NOT NULL
);
CREATE INDEX txn_account_booked ON txn (account_id, booked_at DESC, id DESC);

CREATE TABLE consent (
    id                VARCHAR(40)  PRIMARY KEY,
    tpp_id            VARCHAR(100) NOT NULL,
    customer_id       BIGINT       REFERENCES customer (id),
    permissions       VARCHAR(200) NOT NULL,
    status            VARCHAR(30)  NOT NULL CHECK (status IN ('AwaitingAuthorisation','Authorised','Rejected','Revoked')),
    expires_at        TIMESTAMPTZ  NOT NULL,
    tx_from           TIMESTAMPTZ,
    tx_to             TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    status_updated_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX consent_customer ON consent (customer_id);

CREATE TABLE consent_account (
    consent_id VARCHAR(40) NOT NULL REFERENCES consent (id),
    account_id VARCHAR(40) NOT NULL REFERENCES account (id),
    PRIMARY KEY (consent_id, account_id)
);

CREATE TABLE access_log (
    id          BIGSERIAL    PRIMARY KEY,
    at          TIMESTAMPTZ  NOT NULL,
    tpp_id      VARCHAR(100) NOT NULL,
    consent_id  VARCHAR(40),
    customer_id BIGINT,
    endpoint    VARCHAR(60)  NOT NULL,
    account_id  VARCHAR(40),
    outcome     VARCHAR(10)  NOT NULL CHECK (outcome IN ('ALLOWED','DENIED')),
    reason      VARCHAR(60)
);
CREATE INDEX access_log_consent ON access_log (consent_id, id DESC);

CREATE FUNCTION access_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'access_log rows are append-only';
END $$;
CREATE TRIGGER access_log_no_change BEFORE UPDATE OR DELETE ON access_log
    FOR EACH ROW EXECUTE FUNCTION access_log_append_only();

CREATE TABLE access_counter (
    consent_id VARCHAR(40) NOT NULL,
    account_id VARCHAR(40) NOT NULL,
    endpoint   VARCHAR(60) NOT NULL,
    day        DATE        NOT NULL,
    calls      INT         NOT NULL,
    PRIMARY KEY (consent_id, account_id, endpoint, day)
);
