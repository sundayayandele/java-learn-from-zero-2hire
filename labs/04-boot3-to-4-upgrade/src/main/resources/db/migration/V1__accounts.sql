CREATE TABLE account (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    number      VARCHAR(10)   NOT NULL UNIQUE,
    holder_name VARCHAR(200)  NOT NULL,
    balance     NUMERIC(19,2) NOT NULL DEFAULT 0,
    opened_on   DATE          NOT NULL
);
INSERT INTO account (number, holder_name, balance, opened_on) VALUES
    ('0123456789', 'Ada Okafor', 150000.50, DATE '2024-03-01');
