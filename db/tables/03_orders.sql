-- Orders table

CREATE TABLE IF NOT EXISTS orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       BIGINT              NOT NULL REFERENCES accounts(id),
    symbol           VARCHAR(20)         NOT NULL REFERENCES instruments(symbol),
    side             VARCHAR(4)          NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity         INT                 NOT NULL CHECK (quantity > 0),
    price            NUMERIC(18,2)       NOT NULL CHECK (price > 0),
    status           VARCHAR(20)         NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')) DEFAULT 'NEW',
    idempotency_key  VARCHAR(100)        NOT NULL UNIQUE,
    status_reason    VARCHAR(255),
    created_on       TIMESTAMP           NOT NULL DEFAULT NOW()
);

-- Lookups used by the order-history and portfolio endpoints
CREATE INDEX IF NOT EXISTS ix_orders_account_id ON orders (account_id);
CREATE INDEX IF NOT EXISTS ix_orders_symbol ON orders (symbol);
CREATE INDEX IF NOT EXISTS ix_orders_status ON orders (status);
CREATE INDEX IF NOT EXISTS ix_orders_created_on ON orders (created_on);

COMMENT ON TABLE orders IS 'Placed and executed orders, trading activity';
