-- EXECUTIONS: fills reported by the execution engine, one per filled order
CREATE TABLE IF NOT EXISTS executions (
    id            UUID          PRIMARY KEY,
    order_id      UUID          NOT NULL UNIQUE REFERENCES orders(id),
    quantity      INT           NOT NULL CHECK (quantity > 0),
    price         NUMERIC(18,2) NOT NULL CHECK (price > 0),
    venue         VARCHAR(20)   NOT NULL,
    executed_on   TIMESTAMP     NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_executions_executed_on ON executions (executed_on);

COMMENT ON TABLE executions IS 'Fills reported by the execution engine; orders are filled in full, so one row per FILLED order.';
