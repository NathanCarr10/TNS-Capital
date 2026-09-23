-- Price History table for Yahoo Finance data
DROP TABLE IF EXISTS price_history CASCADE;

CREATE TABLE price_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    date DATE NOT NULL,
    open_price NUMERIC(18,6) NOT NULL,
    high_price NUMERIC(18,6) NOT NULL,
    low_price NUMERIC(18,6) NOT NULL,
    close_price NUMERIC(18,6) NOT NULL,
    adj_close_price NUMERIC(18,6),
    volume BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE CASCADE,
    UNIQUE(symbol, date)
);

CREATE INDEX idx_price_history_symbol ON price_history(symbol);
CREATE INDEX idx_price_history_date ON price_history(date);
CREATE INDEX idx_price_history_symbol_date ON price_history(symbol, date DESC);

-- Optional: Market data/metrics table
DROP TABLE IF EXISTS market_metrics CASCADE;

CREATE TABLE market_metrics (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    metric_date DATE NOT NULL,
    market_cap NUMERIC(20,2),
    pe_ratio NUMERIC(10,4),
    dividend_yield NUMERIC(10,4),
    fifty_two_week_high NUMERIC(18,6),
    fifty_two_week_low NUMERIC(18,6),
    avg_volume BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    FOREIGN KEY (symbol) REFERENCES instruments(symbol) ON DELETE CASCADE,
    UNIQUE(symbol, metric_date)
);

CREATE INDEX idx_market_metrics_symbol ON market_metrics(symbol);
