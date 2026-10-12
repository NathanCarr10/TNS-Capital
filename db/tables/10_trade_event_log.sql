-- TRADE_EVENT_LOG: every order lifecycle event from the trade-events topic, as
-- received by the API's audit consumer (NFR-04). One row per event.
CREATE TABLE IF NOT EXISTS trade_event_log (
    event_id         VARCHAR(64)     PRIMARY KEY,   -- the event's correlationId, so a redelivered event is stored once
    order_id         UUID            NOT NULL,
    account_id       BIGINT          NOT NULL,
    symbol           VARCHAR(20)     NOT NULL,
    side             VARCHAR(4)      NOT NULL,
    quantity         INT             NOT NULL,
    price            NUMERIC(18,2)   NOT NULL,
    previous_status  VARCHAR(20),
    new_status       VARCHAR(20)     NOT NULL,
    reason           TEXT,
    received_on      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- No foreign keys: the audit trail must survive the order or account being removed
CREATE INDEX IF NOT EXISTS ix_trade_event_log_order_id ON trade_event_log (order_id);
CREATE INDEX IF NOT EXISTS ix_trade_event_log_account_id ON trade_event_log (account_id);

COMMENT ON TABLE trade_event_log IS 'Order lifecycle events (NEW -> FILLED / REJECTED / CANCELLED) consumed from the trade-events topic.';
