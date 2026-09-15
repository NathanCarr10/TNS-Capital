-- Orders table
DROP TABLE IF EXISTS orders;

CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES accounts(id),
    symbol           VARCHAR(20) NOT NULL REFERENCES instruments(symbol),
    side             VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity         INT NOT NULL CHECK (quantity > 0),
    price            NUMERIC(18,2) NOT NULL CHECK (price > 0),
    status           VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    idempotency_key  VARCHAR(100) NOT NULL UNIQUE,
    created_on       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Lookups used by the order-history and portfolio endpoints
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
