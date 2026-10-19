-- STRATEGY_SIGNALS: every time a strategy's rule fired, and the order it placed.
CREATE TABLE IF NOT EXISTS strategy_signals (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    strategy_id   BIGINT          NOT NULL REFERENCES strategies(id),
    side          VARCHAR(4)      NOT NULL CHECK (side IN ('BUY', 'SELL')),
    -- Close of the 1-minute bar the rule fired on
    price         NUMERIC(18,4)   NOT NULL,
    bar_time      TIMESTAMP       NOT NULL,
    source        VARCHAR(10)     NOT NULL CHECK (source IN ('LIVE', 'REPLAY')),
    -- The market replay the bar came from; NULL for live prices
    replay_id     VARCHAR(36),
    -- The trading API's order, when it accepted one
    order_id      UUID,
    outcome       VARCHAR(10)     NOT NULL CHECK (outcome IN ('PLACED', 'FAILED')),
    detail        VARCHAR(255),
    created_on    TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- A redelivered market-data message cannot fire the same bar twice; two replays
-- of the same day are different bars
CREATE UNIQUE INDEX IF NOT EXISTS ux_strategy_signals_bar
    ON strategy_signals (strategy_id, source, COALESCE(replay_id, ''), bar_time);

CREATE INDEX IF NOT EXISTS ix_strategy_signals_order_id ON strategy_signals (order_id);
CREATE INDEX IF NOT EXISTS ix_strategy_signals_strategy_id ON strategy_signals (strategy_id, created_on DESC);

COMMENT ON TABLE strategy_signals IS 'Signals of the strategies and the orders they placed through the trading API.';
