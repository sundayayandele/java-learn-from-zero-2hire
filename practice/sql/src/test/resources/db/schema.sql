-- Practice schema for the SQL interview drills (synthetic data, CC0).
CREATE TABLE branch (
    id    INT PRIMARY KEY,
    name  TEXT NOT NULL,
    city  TEXT NOT NULL
);

CREATE TABLE customer (
    id          INT PRIMARY KEY,
    full_name   TEXT NOT NULL,
    branch_id   INT NOT NULL REFERENCES branch (id),
    joined_on   DATE NOT NULL,
    referred_by INT REFERENCES customer (id)
);

CREATE TABLE account (
    id           INT PRIMARY KEY,
    number       CHAR(10) NOT NULL UNIQUE,
    customer_id  INT NOT NULL REFERENCES customer (id),
    type         TEXT NOT NULL CHECK (type IN ('CURRENT', 'SAVINGS', 'DOMICILIARY')),
    currency     CHAR(3) NOT NULL,
    opened_on    DATE NOT NULL,
    status       TEXT NOT NULL CHECK (status IN ('ACTIVE', 'DORMANT', 'CLOSED')),
    balance      NUMERIC(19, 2) NOT NULL
);

CREATE TABLE txn (
    id          INT PRIMARY KEY,
    account_id  INT NOT NULL REFERENCES account (id),
    booked_at   TIMESTAMP NOT NULL,
    amount      NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    direction   TEXT NOT NULL CHECK (direction IN ('CREDIT', 'DEBIT')),
    channel     TEXT NOT NULL CHECK (channel IN ('POS', 'NIP', 'USSD', 'ATM', 'WEB')),
    reference   TEXT NOT NULL,
    merchant    TEXT,
    category    TEXT
);
CREATE INDEX txn_account_time ON txn (account_id, booked_at);

CREATE TABLE employee (
    id          INT PRIMARY KEY,
    name        TEXT NOT NULL,
    manager_id  INT REFERENCES employee (id),
    branch_id   INT REFERENCES branch (id),
    salary      NUMERIC(12, 2) NOT NULL
);
