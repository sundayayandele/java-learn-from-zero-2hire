-- ECB euro reference rates: one row per currency per publication date, expressed as units per 1 EUR.
CREATE TABLE import_run (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source         VARCHAR(500) NOT NULL,
    started_at     TIMESTAMPTZ  NOT NULL,
    finished_at    TIMESTAMPTZ,
    rows_read      INTEGER,
    rows_inserted  INTEGER,
    status         VARCHAR(10)  NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    error          VARCHAR(500)
);

CREATE TABLE fx_rate (
    currency       VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    rate_date      DATE           NOT NULL,
    rate_per_eur   NUMERIC(19,10) NOT NULL CHECK (rate_per_eur > 0),
    import_run_id  BIGINT         NOT NULL REFERENCES import_run (id),
    -- The primary key doubles as the index for "rate for this currency on or before this date".
    PRIMARY KEY (currency, rate_date)
);

-- "Latest publication date on or before a date" (any currency).
CREATE INDEX idx_fx_rate_date ON fx_rate (rate_date);
