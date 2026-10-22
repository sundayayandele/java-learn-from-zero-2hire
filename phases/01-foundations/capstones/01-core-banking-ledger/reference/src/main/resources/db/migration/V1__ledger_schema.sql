-- ITL Bank double-entry ledger.
-- The database enforces the invariants itself, so a bug in any service can't break them:
--   * customer balances never go negative (CHECK)
--   * journal references are unique (idempotency)
--   * a journal can be reversed at most once (UNIQUE reverses_journal_id)
--   * postings are immutable (trigger)
--   * every journal's postings balance per currency at commit (deferred constraint trigger)

CREATE SEQUENCE account_number_seq START WITH 1000000001;

CREATE TABLE account (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number       VARCHAR(10)   NOT NULL UNIQUE CHECK (number ~ '^[0-9]{10}$'),
    holder_name  VARCHAR(200)  NOT NULL,
    kind         VARCHAR(20)   NOT NULL CHECK (kind IN ('CUSTOMER', 'CASH', 'FEE_INCOME', 'SUSPENSE')),
    normal_side  CHAR(1)       NOT NULL CHECK (normal_side IN ('D', 'C')),
    currency     VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status       VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    daily_limit  NUMERIC(19,2) CHECK (daily_limit > 0),
    balance      NUMERIC(19,2) NOT NULL DEFAULT 0,
    version      BIGINT        NOT NULL DEFAULT 0,
    opened_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT customer_balance_not_negative CHECK (kind <> 'CUSTOMER' OR balance >= 0)
);

CREATE TABLE journal (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference            VARCHAR(64)  NOT NULL UNIQUE,
    type                 VARCHAR(10)  NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER', 'REVERSAL')),
    request_hash         VARCHAR(64)  NOT NULL,
    narration            VARCHAR(140),
    reverses_journal_id  BIGINT UNIQUE REFERENCES journal (id),
    created_at           TIMESTAMPTZ  NOT NULL
);

CREATE TABLE posting (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_id  BIGINT        NOT NULL REFERENCES journal (id),
    account_id  BIGINT        NOT NULL REFERENCES account (id),
    direction   CHAR(1)       NOT NULL CHECK (direction IN ('D', 'C')),
    amount      NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)    NOT NULL,
    posted_at   TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_posting_journal ON posting (journal_id);
-- Serves statements and the daily-limit query: one account, a time range, in posting order.
CREATE INDEX idx_posting_account_posted ON posting (account_id, posted_at, id);

CREATE FUNCTION forbid_posting_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'postings are immutable: correct mistakes with a reversing journal';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER posting_immutable
    BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION forbid_posting_change();

CREATE FUNCTION check_journal_balanced() RETURNS trigger AS $$
DECLARE
    imbalance NUMERIC;
BEGIN
    SELECT coalesce(sum(CASE WHEN direction = 'D' THEN amount ELSE -amount END), 0)
      INTO imbalance
      FROM posting
     WHERE journal_id = NEW.journal_id AND currency = NEW.currency;
    IF imbalance <> 0 THEN
        RAISE EXCEPTION 'journal % is not balanced in % (difference %)', NEW.journal_id, NEW.currency, imbalance;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER journal_balanced
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_balanced();

-- Internal general-ledger accounts (fixed numbers, NGN).
INSERT INTO account (number, holder_name, kind, normal_side, currency) VALUES
    ('9000000001', 'ITL Bank cash',        'CASH',       'D', 'NGN'),
    ('9000000002', 'ITL Bank fee income',  'FEE_INCOME', 'C', 'NGN'),
    ('9000000003', 'ITL Bank suspense',    'SUSPENSE',   'D', 'NGN');
