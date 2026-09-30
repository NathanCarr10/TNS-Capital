-- Order history table: archived copies of deleted orders, kept for auditing.
-- No foreign keys, so a record outlives the order and account it came from.
CREATE TABLE order_history (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(50) NOT NULL,
    side VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    order_created_on TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_on TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for common lookups
CREATE INDEX idx_order_history_order_id ON order_history(order_id);
CREATE INDEX idx_order_history_account_id ON order_history(account_id);
