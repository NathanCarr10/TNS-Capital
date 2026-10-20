-- Reporting tables filled by the ETL pipeline.
-- "IF NOT EXISTS" means this is safe to run every time the pipeline loads.

CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.dim_account (
    account_number  VARCHAR(32)  PRIMARY KEY,
    holder_name     VARCHAR(255) NOT NULL,
    account_status  VARCHAR(20)  NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.dim_instrument (
    symbol           VARCHAR(20)  PRIMARY KEY,
    instrument_name  VARCHAR(255) NOT NULL,
    asset_class      VARCHAR(20)  NOT NULL,
    currency         VARCHAR(3)   NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.dim_date (
    date_key    INT PRIMARY KEY,   -- e.g. 20260801
    full_date   DATE        NOT NULL,
    year        INT         NOT NULL,
    quarter     INT         NOT NULL,
    month       INT         NOT NULL,
    month_name  VARCHAR(9)  NOT NULL,
    day         INT         NOT NULL,
    day_name    VARCHAR(9)  NOT NULL,
    is_weekend  BOOLEAN     NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.fact_trades (
    order_id         UUID PRIMARY KEY,
    account_number   VARCHAR(32)   NOT NULL REFERENCES analytics.dim_account(account_number),
    symbol           VARCHAR(20)   NOT NULL REFERENCES analytics.dim_instrument(symbol),
    date_key         INT           NOT NULL REFERENCES analytics.dim_date(date_key),
    side             VARCHAR(4)    NOT NULL,
    quantity         INT           NOT NULL,
    signed_quantity  INT           NOT NULL,   -- +quantity for BUY, -quantity for SELL
    price            NUMERIC(18,2) NOT NULL,
    notional         NUMERIC(20,2) NOT NULL,   -- price * quantity
    status           VARCHAR(20)   NOT NULL,
    is_filled        BOOLEAN       NOT NULL,
    created_on       TIMESTAMP     NOT NULL    -- UTC
);

-- Orders that failed validation in the latest run, with the rule they broke.
-- Replaced on every run. Values are text because they did not pass as valid
-- numbers or dates.
CREATE TABLE IF NOT EXISTS analytics.etl_rejected_orders (
    order_id        TEXT,
    account_number  TEXT,
    symbol          TEXT,
    side            TEXT,
    quantity        TEXT,
    price           TEXT,
    status          TEXT,
    created_on      TEXT,
    reject_reason   VARCHAR(100) NOT NULL,
    rejected_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Daily price bars per instrument from the Alpaca market data API, for the
-- dashboard. Prices are as traded (not adjusted), so they match the order
-- prices in fact_trades; a stock split shows as a jump. symbol matches
-- dim_instrument.symbol, but there is no foreign key so prices can load even
-- if the trades part of a run fails.
CREATE TABLE IF NOT EXISTS analytics.fact_daily_prices (
    symbol       VARCHAR(20)   NOT NULL,
    trade_date   DATE          NOT NULL,   -- US trading day (New York time)
    open         NUMERIC(18,4) NOT NULL,
    high         NUMERIC(18,4) NOT NULL,
    low          NUMERIC(18,4) NOT NULL,
    close        NUMERIC(18,4) NOT NULL,
    volume       BIGINT        NOT NULL,
    trade_count  BIGINT,
    vwap         NUMERIC(18,4),            -- volume-weighted average price
    PRIMARY KEY (symbol, trade_date)
);

-- Let the dashboard (tns_reader) and the strategy service (tns_strategy) read
-- these tables. roles.sql grants this by default only on tables tns_etl
-- creates; a run as the database owner (python pipeline.py with .env's
-- DB_USER) creates them as the owner, and the readers get "permission denied".
-- Only objects the running role may grant on are touched, so this cannot fail
-- the load, and roles that don't exist (a bare test database) are skipped.
DO $$
DECLARE
    reader TEXT;
    tbl    TEXT;
BEGIN
    FOREACH reader IN ARRAY ARRAY['tns_reader', 'tns_strategy'] LOOP
        CONTINUE WHEN NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = reader);
        IF (SELECT pg_has_role(nspowner, 'USAGE') FROM pg_namespace WHERE nspname = 'analytics') THEN
            EXECUTE format('GRANT USAGE ON SCHEMA analytics TO %I', reader);
        END IF;
        FOR tbl IN SELECT tablename FROM pg_tables
                   WHERE schemaname = 'analytics' AND pg_has_role(tableowner, 'USAGE') LOOP
            EXECUTE format('GRANT SELECT ON analytics.%I TO %I', tbl, reader);
        END LOOP;
    END LOOP;
END $$;
