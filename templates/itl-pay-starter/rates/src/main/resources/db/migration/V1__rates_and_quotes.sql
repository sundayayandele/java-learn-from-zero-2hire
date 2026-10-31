-- FX rates (latest per pair) and the quotes given to customers (topic 14.2).
CREATE TABLE rate (
    pair        TEXT          PRIMARY KEY,               -- e.g. USD-NGN
    bid         NUMERIC(19,4) NOT NULL,
    ask         NUMERIC(19,4) NOT NULL CHECK (ask >= bid),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE quote (
    id          UUID          PRIMARY KEY,
    pair        TEXT          NOT NULL REFERENCES rate (pair),
    amount      NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    rate        NUMERIC(19,4) NOT NULL,
    total       NUMERIC(19,2) NOT NULL,
    expires_at  TIMESTAMPTZ   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

INSERT INTO rate (pair, bid, ask) VALUES
    ('USD-NGN', 1525.0000, 1535.0000),
    ('GBP-NGN', 2010.0000, 2025.0000),
    ('EUR-NGN', 1650.0000, 1662.0000);
