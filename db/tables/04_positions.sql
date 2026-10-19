-- Positions table
CREATE TABLE IF NOT EXISTS positions (
    account_id    BIGINT            NOT NULL REFERENCES accounts(id),
    symbol        VARCHAR(20)       NOT NULL REFERENCES instruments(symbol),
    quantity      INT               NOT NULL CHECK (quantity >= 0) DEFAULT 0,
    -- Shares held for accepted SELL orders until they are filled, cancelled or rejected
    reserved_quantity INT           NOT NULL CHECK (reserved_quantity >= 0) DEFAULT 0,
    average_cost  NUMERIC(18,2)     NOT NULL CHECK (average_cost >= 0) DEFAULT 0,
    PRIMARY KEY (account_id, symbol),
    CONSTRAINT ck_positions_reserved_quantity CHECK (reserved_quantity <= quantity)
);

COMMENT ON TABLE positions IS 'Current positions held in trading accounts.';
