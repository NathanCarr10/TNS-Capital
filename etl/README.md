# TNS Capital Trade ETL

A scheduled pipeline that copies operational trade data from the TNS Capital PostgreSQL
database into a reporting star schema (`analytics`), so analysts can query trades without
touching the tables the trading API writes to.

```
PostgreSQL (public)                                          PostgreSQL (analytics)
  accounts  ─┐                                                 dim_account
  instruments├─► EXTRACT ─► TRANSFORM ─────────────► LOAD ─►   dim_instrument
  orders    ─┘   raw        clean, validate,        upsert in  dim_date
                 SELECTs    build dims + facts      one txn    fact_trades
                                                               etl_run_log
```

## Acceptance criteria

| Criterion | How it is met |
|---|---|
| Extract → Transform → Load stages | [`extract.py`](tns_etl/extract.py), [`transform.py`](tns_etl/transform.py) and [`load.py`](tns_etl/load.py) are separate modules; [`pipeline.py`](tns_etl/pipeline.py) runs them in order. |
| Trade data extracted from PostgreSQL | `accounts`, `instruments` and `orders` are read in one `REPEATABLE READ` snapshot. You can pass an optional `--since`/`--until` window on `created_on`. |
| Transformations are unit tested | [`tests/test_transform.py`](tests/test_transform.py) covers every transform function: cleaning, each rejection rule, derived measures, dimensions and determinism. |
| Pipeline execution is repeatable | Transforms are pure, with no clock or random input. The load upserts on natural keys, so re-running the same data changes 0 rows. An integration test proves this. |
| Error handling for failed stages | A failure raises `StageError` naming the stage, and the run stops. The run is recorded as `FAILED` in `analytics.etl_run_log` with the stage and error. The CLI exits `1`. The load is a single transaction, so a failure leaves nothing half-written. Bad rows are rejected with a reason and logged; they don't break the run. |
| Retry mechanisms | Extract and load are retried on transient DB errors (connection refused or dropped) with exponential backoff. The defaults are 3 attempts waiting 2s then 4s, capped at 60s. Transform is not retried because it would fail the same way. |
| Scheduled | `python -m tns_etl schedule` runs daily at `ETL_SCHEDULE_TIME`, default 02:00. It is the `etl` service in `docker-compose.yml`. A failed run does not stop the next one. |

## Running it

```bash
cd etl
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt

python -m tns_etl run                                    # once, all orders
python -m tns_etl run --since 2026-08-01 --until 2026-09-01
python -m tns_etl schedule                               # daily at ETL_SCHEDULE_TIME
python -m tns_etl schedule --at 06:30 --run-now          # run now, then daily at 06:30
```

`DB_USER` / `DB_PASSWORD` are read from the repo-root `.env`, the same one used by docker-compose.

With Docker, `docker-compose up -d etl` starts the scheduler next to Postgres. The source
tables must exist, so start the `app` service too: Hibernate creates them.

### Configuration

| Variable | Default | |
|---|---|---|
| `ETL_DB_HOST` / `ETL_DB_PORT` / `ETL_DB_NAME` | `localhost` / `5432` / `tns_capital` | |
| `DB_USER` / `DB_PASSWORD` | from `.env` | shared with the app |
| `ETL_ANALYTICS_SCHEMA` | `analytics` | created automatically |
| `ETL_SCHEDULE_TIME` | `02:00` | container time (UTC in Docker) |
| `ETL_RETRY_MAX_ATTEMPTS` | `3` | |
| `ETL_RETRY_INITIAL_DELAY_SECONDS` | `2` | doubled each retry |
| `ETL_RETRY_BACKOFF_MULTIPLIER` | `2` | |
| `ETL_RETRY_MAX_DELAY_SECONDS` | `60` | |

## Tests

```bash
python -m pytest                      # unit tests, no database needed
```

The integration tests run the real pipeline against Postgres. They cover the full load,
a repeat run that changes 0 rows, a status change flowing through, and a failed run leaving
the analytics tables untouched. They **drop and recreate tables**, so only use a
throwaway database:

```bash
docker run -d --rm --name etl-it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
ETL_IT_DB_NAME=postgres ETL_DB_PORT=55432 DB_USER=postgres DB_PASSWORD=it python -m pytest -m integration
docker stop etl-it
```

## The analytics schema

See [`schema.sql`](tns_etl/schema.sql). `fact_trades` has one row per order, with these
reporting measures:

- `signed_quantity`: +qty for BUY, −qty for SELL, so it sums to a net position
- `notional`: price × quantity
- `is_filled`: true when the order status is FILLED

Example query, filled notional by asset class per month:

```sql
SELECT d.year, d.month, i.asset_class, SUM(f.notional) AS filled_notional
FROM analytics.fact_trades f
JOIN analytics.dim_date d USING (date_key)
JOIN analytics.dim_instrument i USING (instrument_key)
WHERE f.is_filled
GROUP BY 1, 2, 3
ORDER BY 1, 2, 3;
```

Check recent runs with:

```sql
SELECT started_at, status, failed_stage, rows_extracted, rows_rejected, rows_loaded, error_message
FROM analytics.etl_run_log ORDER BY started_at DESC LIMIT 10;
```

## Design notes

- **Full extract by default.** `orders` has no `updated_at` column, and statuses change
  after creation (NEW → FILLED/CANCELLED). An incremental load keyed on `created_on`
  would miss those changes. Re-reading everything and upserting is correct and cheap at
  current volumes. Use `--since` for backfills or once volumes grow.
- **Deleted source orders stay in the fact table.** The trading API cancels orders rather
  than deleting them.
- **Concurrent runs are serialised** with a Postgres advisory lock, for example a manual
  run during a scheduled one.
- **Timestamps are stored as UTC.** The source column is `TIMESTAMP` in `db/tables` but
  `TIMESTAMPTZ` when Hibernate creates it; both are normalised.
