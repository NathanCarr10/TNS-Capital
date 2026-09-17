-- Accounts table
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id    VARCHAR(32)  NOT NULL UNIQUE,
    holder_name   VARCHAR(255) NOT NULL,
    cash_balance  NUMERIC(18,2) NOT NULL CHECK (cash_balance >= 0),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    version       INT NOT NULL DEFAULT 0,
    last_updated  TIMESTAMP NOT NULL DEFAULT NOW()
);
