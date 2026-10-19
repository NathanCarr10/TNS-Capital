-- STRATEGIES: automated trading rules that traders switch on in Strategy Lab.
-- The strategy service evaluates each ACTIVE strategy on 1-minute bars from the
-- market-data topic and places its orders through the trading API, which only
-- accepts them for an account with an ACTIVE strategy (docs/DECISIONS.md #16).
CREATE TABLE IF NOT EXISTS strategies (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id   BIGINT          NOT NULL REFERENCES accounts(id),
    name         VARCHAR(80)     NOT NULL,
    symbol       VARCHAR(20)     NOT NULL REFERENCES instruments(symbol),
    rule_type    VARCHAR(30)     NOT NULL CHECK (rule_type IN ('MA_CROSSOVER', 'BOLLINGER')),
    rule_params  JSONB           NOT NULL,
    quantity     INT             NOT NULL CHECK (quantity > 0),
    status       VARCHAR(10)     NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')) DEFAULT 'PAUSED',
    -- What the strategy holds from its own orders: it buys only when FLAT and sells only when LONG
    position     VARCHAR(5)      NOT NULL CHECK (position IN ('FLAT', 'LONG')) DEFAULT 'FLAT',
    created_on   TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_on   TIMESTAMP       NOT NULL DEFAULT NOW(),
    version      INT             NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_strategies_account_id ON strategies (account_id);
CREATE INDEX IF NOT EXISTS ix_strategies_active_symbol ON strategies (symbol) WHERE status = 'ACTIVE';

COMMENT ON TABLE strategies IS 'Trading rules owned by an account; only ACTIVE ones trade.';
COMMENT ON COLUMN strategies.rule_params IS 'Rule settings, e.g. {"shortWindow":20,"longWindow":50} for MA_CROSSOVER.';
COMMENT ON COLUMN strategies.status IS 'ACTIVE trades, PAUSED does not, ARCHIVED is deleted by its owner but kept for the audit trail.';
