-- Positions table: holdings per account and instrument (composite key: account_id, symbol)
CREATE TABLE positions (
    account_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    average_cost NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (account_id, symbol),
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

-- Indexes for common lookups
CREATE INDEX idx_positions_symbol ON positions(symbol);
