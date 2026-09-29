-- Reporting star schema populated by the TNS Capital ETL pipeline.
-- Every statement is idempotent so the pipeline can run it on each load.
-- {schema} is replaced with ETL_ANALYTICS_SCHEMA (default: analytics).

CREATE SCHEMA IF NOT EXISTS {schema};

CREATE TABLE IF NOT EXISTS {schema}.dim_account (
    account_key     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32)  NOT NULL UNIQUE,
    holder_name     VARCHAR(255) NOT NULL,
    account_status  VARCHAR(20)  NOT NULL,
    loaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS {schema}.dim_instrument (
    instrument_key   INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    symbol           VARCHAR(20)  NOT NULL UNIQUE,
    instrument_name  VARCHAR(255) NOT NULL,
    asset_class      VARCHAR(20)  NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    tradable         BOOLEAN      NOT NULL,
    loaded_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS {schema}.dim_date (
    date_key     INT PRIMARY KEY,           -- YYYYMMDD
    full_date    DATE        NOT NULL UNIQUE,
    year         SMALLINT    NOT NULL,
    quarter      SMALLINT    NOT NULL,
    month        SMALLINT    NOT NULL,
    month_name   VARCHAR(9)  NOT NULL,
    day          SMALLINT    NOT NULL,
    day_of_week  SMALLINT    NOT NULL,      -- ISO: Monday = 1
    day_name     VARCHAR(9)  NOT NULL,
    is_weekend   BOOLEAN     NOT NULL
);

CREATE TABLE IF NOT EXISTS {schema}.fact_trades (
    order_id         UUID PRIMARY KEY,
    account_key      INT NOT NULL REFERENCES {schema}.dim_account(account_key),
    instrument_key   INT NOT NULL REFERENCES {schema}.dim_instrument(instrument_key),
    date_key         INT NOT NULL REFERENCES {schema}.dim_date(date_key),
    side             VARCHAR(4)    NOT NULL,
    quantity         INT           NOT NULL,
    signed_quantity  INT           NOT NULL,
    price            NUMERIC(18,2) NOT NULL,
    notional         NUMERIC(20,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    is_filled        BOOLEAN       NOT NULL,
    created_on       TIMESTAMP     NOT NULL,  -- UTC
    idempotency_key  VARCHAR(100)  NOT NULL,
    loaded_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_trades_date ON {schema}.fact_trades(date_key);
CREATE INDEX IF NOT EXISTS idx_fact_trades_account ON {schema}.fact_trades(account_key);
CREATE INDEX IF NOT EXISTS idx_fact_trades_instrument ON {schema}.fact_trades(instrument_key);

-- One row per pipeline run, written outside the load transaction so failed
-- runs are recorded too.
CREATE TABLE IF NOT EXISTS {schema}.etl_run_log (
    run_id          UUID PRIMARY KEY,
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    status          VARCHAR(10) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    failed_stage    VARCHAR(20),
    error_message   TEXT,
    window_start    TIMESTAMP,
    window_end      TIMESTAMP,
    rows_extracted  INT,
    rows_rejected   INT,
    rows_loaded     INT
);
