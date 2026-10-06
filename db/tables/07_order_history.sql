-- ORDER_HISTORY: archived copy of each cancelled order, kept for the audit trail
CREATE TABLE IF NOT EXISTS order_history (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id          UUID                NOT NULL,
    account_id        BIGINT              NOT NULL,
    symbol            VARCHAR(20)         NOT NULL,
    side              VARCHAR(4)          NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity          INT                 NOT NULL CHECK (quantity > 0),
    price             NUMERIC(18,2)       NOT NULL CHECK (price > 0),
    status            VARCHAR(20)         NOT NULL CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),
    idempotency_key   VARCHAR(100)        NOT NULL UNIQUE,
    order_created_on  TIMESTAMP           NOT NULL,
    deleted_on        TIMESTAMP           NOT NULL
);

-- No foreign keys: history rows must survive the account or order being removed
CREATE INDEX IF NOT EXISTS ix_order_history_order_id ON order_history (order_id);
CREATE INDEX IF NOT EXISTS ix_order_history_account_id ON order_history (account_id);

COMMENT ON TABLE order_history IS 'Archived copies of cancelled orders, kept for auditing.';
