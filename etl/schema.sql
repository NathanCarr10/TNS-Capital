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
