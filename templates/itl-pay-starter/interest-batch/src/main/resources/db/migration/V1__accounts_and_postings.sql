-- End-of-day interest (topic 11.5). In ITL Pay the ledger would own balances; this job keeps a copy to stay self-contained.
CREATE TABLE account_balance (
    account_id    TEXT          PRIMARY KEY,
    balance       NUMERIC(19,2) NOT NULL,
    annual_rate   NUMERIC(6,4)  NOT NULL               -- 0.0400 = 4% a year
);

CREATE TABLE interest_posting (
    account_id    TEXT          NOT NULL,
    business_date DATE          NOT NULL,
    amount        NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    posted_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, business_date)             -- one posting per account per day, however often the job runs
);

-- 1,000 demo accounts: every fifth is a current account that earns no interest.
INSERT INTO account_balance (account_id, balance, annual_rate)
SELECT 'SAV-' || lpad(i::text, 4, '0'),
       1000 + (i * 1371) % 500000,
       CASE WHEN i % 5 = 0 THEN 0 ELSE 0.0400 END
FROM generate_series(1, 1000) AS i;
