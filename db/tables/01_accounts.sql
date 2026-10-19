-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number  VARCHAR(32)         NOT NULL UNIQUE,
    holder_name     VARCHAR(255)        NOT NULL,
    cash_balance    NUMERIC(18,2)       NOT NULL CHECK (cash_balance >= 0) DEFAULT 0,
    -- Cash held for accepted BUY orders until they are filled, cancelled or rejected
    reserved_cash   NUMERIC(18,2)       NOT NULL CHECK (reserved_cash >= 0) DEFAULT 0,
    status          VARCHAR(20)         NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')) DEFAULT 'ACTIVE',
    version         INT                 NOT NULL DEFAULT 0,
    opened_on       TIMESTAMP           NOT NULL DEFAULT NOW(),
    last_updated    TIMESTAMP           NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_accounts_reserved_cash CHECK (reserved_cash <= cash_balance)
);

COMMENT ON TABLE accounts IS 'Trading accounts and their cash balances.';
