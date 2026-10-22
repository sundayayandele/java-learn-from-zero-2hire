-- Query-tuning lab seed. Usage:
--   docker run -d --name tuning -e POSTGRES_PASSWORD=devpass -p 5433:5432 postgres:17-alpine
--   psql -h localhost -p 5433 -U postgres -v rows=20000000 -f seed.sql
-- 'rows' defaults to 2,000,000 if not set. 20 million rows needs ~6 GB of disk and several minutes.
\if :{?rows}
\else
  \set rows 2000000
\endif

DROP TABLE IF EXISTS txn, account, customer, merchant CASCADE;

CREATE TABLE customer (
    id         BIGINT PRIMARY KEY,
    full_name  TEXT NOT NULL,
    email      TEXT NOT NULL,
    state      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE account (
    id          BIGINT PRIMARY KEY,
    number      VARCHAR(10) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES customer (id),
    status      TEXT NOT NULL,
    opened_on   DATE NOT NULL
);

CREATE TABLE merchant (
    id   BIGINT PRIMARY KEY,
    name TEXT NOT NULL,
    mcc  CHAR(4) NOT NULL
);

-- Deliberately under-indexed: only primary keys. Finding the right indexes is the lab.
CREATE TABLE txn (
    id          BIGINT PRIMARY KEY,
    account_id  BIGINT NOT NULL REFERENCES account (id),
    merchant_id BIGINT REFERENCES merchant (id),
    reference   TEXT NOT NULL,
    channel     TEXT NOT NULL,
    direction   CHAR(1) NOT NULL,
    amount      NUMERIC(19,2) NOT NULL,
    narration   TEXT NOT NULL,
    posted_at   TIMESTAMPTZ NOT NULL
);

INSERT INTO customer
SELECT g, 'Customer ' || g, 'customer' || g || '@example.ng',
       (ARRAY['Lagos','Abuja','Rivers','Kano','Oyo','Enugu','Kaduna','Ogun'])[1 + g % 8],
       now() - (random() * interval '2000 days')
FROM generate_series(1, :rows / 100) g;

INSERT INTO account
SELECT g, lpad(g::text, 10, '0'), 1 + (g % (:rows / 100)),
       CASE WHEN g % 50 = 0 THEN 'CLOSED' WHEN g % 7 = 0 THEN 'DORMANT' ELSE 'ACTIVE' END,
       current_date - (random() * 2000)::int
FROM generate_series(1, :rows / 40) g;

INSERT INTO merchant
SELECT g, 'Merchant ' || g, (ARRAY['5411','5812','5541','4814','5999','6011','4121','5732'])[1 + g % 8]
FROM generate_series(1, 5000) g;

INSERT INTO txn
SELECT g,
       1 + (random() * (:rows / 40 - 1))::bigint,
       CASE WHEN random() < 0.7 THEN 1 + (random() * 4999)::bigint END,
       'REF' || g,
       (ARRAY['MOBILE','USSD','POS','WEB','SYSTEM'])[1 + (random() * 4)::int],
       CASE WHEN random() < 0.6 THEN 'D' ELSE 'C' END,
       round((random() * 250000)::numeric, 2),
       (ARRAY['POS PURCHASE','TRANSFER TO','AIRTIME','BILL PAYMENT','SALARY','ATM WITHDRAWAL'])[1 + (random() * 5)::int]
           || ' ' || g,
       now() - (random() * interval '730 days')
FROM generate_series(1, :rows) g;

-- Make every 10th account dormant: no recent customer activity.
UPDATE txn SET posted_at = posted_at - interval '400 days'
WHERE account_id % 10 = 0 AND posted_at > now() - interval '200 days';

ANALYZE;
SELECT 'seeded' AS status, (SELECT count(*) FROM txn) AS transactions;
