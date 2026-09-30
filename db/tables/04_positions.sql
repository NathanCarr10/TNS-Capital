-- Positions table
CREATE TABLE IF NOT EXISTS positions (
    account_id    BIGINT            NOT NULL REFERENCES accounts(id),
    symbol        VARCHAR(20)       NOT NULL REFERENCES instruments(symbol),
    quantity      INT               NOT NULL CHECK (quantity >= 0) DEFAULT 0,
    average_cost  NUMERIC(18,2)     NOT NULL CHECK (average_cost >= 0) DEFAULT 0,
    PRIMARY KEY (account_id, symbol)
);

COMMENT ON TABLE positions IS 'Current positions held in trading accounts.';
