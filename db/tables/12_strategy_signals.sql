-- STRATEGY_SIGNALS: every time a strategy's rule fired, and the order it placed.
CREATE TABLE IF NOT EXISTS strategy_signals (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id   BIGINT          NOT NULL REFERENCES strategies(id),
    side          VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    -- Close of the 1-minute bar the rule fired on
    price         NUMERIC(18,4)   NOT NULL,
    bar_time      TIMESTAMP       NOT NULL,
    source        VARCHAR(10)     NOT NULL CHECK (source IN ('LIVE', 'REPLAY')),
    -- The trading API's order, when it accepted one
    order_id      UUID,
    outcome       VARCHAR(10)     NOT NULL CHECK (outcome IN ('PLACED', 'FAILED')),
    detail        VARCHAR(255),
    created_on    TIMESTAMP       NOT NULL DEFAULT NOW(),
    -- A redelivered market-data message cannot fire the same bar twice
    UNIQUE (strategy_id, bar_time, source)
);

CREATE INDEX IF NOT EXISTS ix_strategy_signals_order_id ON strategy_signals (order_id);

COMMENT ON TABLE strategy_signals IS 'Signals of the strategies and the orders they placed through the trading API.';
