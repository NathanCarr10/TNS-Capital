# TNS Capital Trade ETL

A scheduled pipeline that copies trade data from the TNS Capital PostgreSQL database into
reporting tables in an `analytics` schema. Reports can then be run without touching the
tables the trading app uses.

```
accounts, instruments, orders ──► EXTRACT ──► TRANSFORM ──► LOAD ──► analytics.dim_account
(the app's tables)                                                   analytics.dim_instrument
                                                                     analytics.dim_date
                                                                     analytics.fact_trades
```

## Files

| File | What it does |
|---|---|
| [extract.py](extract.py) | **Extract**: reads accounts, instruments and orders from PostgreSQL into DataFrames |
| [transform.py](transform.py) | **Transform**: cleans the orders, removes invalid ones and builds the four reporting tables |
| [load.py](load.py) | **Load**: saves the tables into the `analytics` schema in one transaction |
| [pipeline.py](pipeline.py) | Runs the three stages in order, with retries, error handling and a daily schedule |
| [config.py](config.py) | Settings, such as the database connection, schedule time and retry count |
| [schema.sql](schema.sql) | Creates the `analytics` tables if they don't exist |
| [tests/](tests/) | Unit tests, plus integration tests that need a database |

## How it meets the acceptance criteria

- **Extract → Transform → Load:** each stage is its own file and function. `run_pipeline()`
  calls them in order.
- **Extracted from PostgreSQL:** `extract.py` reads the app's tables.
- **Transformations are unit tested:** see [tests/test_transform.py](tests/test_transform.py).
- **Repeatable:** `load.py` uses an *upsert*: it inserts new rows and updates rows that
  already exist. Running the pipeline twice gives the same tables, with no duplicates.
- **Error handling:** each stage is wrapped in `try/except`. A failure logs which stage
  failed and stops the run, and the program exits with code 1. The load is one
  transaction, so a failed load saves nothing.
- **Retry:** if the database can't be reached, `with_retry()` tries again up to 3 times,
  waiting 2s and then 4s between tries.
- **Scheduled:** `python pipeline.py --schedule` runs it every day at 02:00. It runs as the
  `etl` service in `docker-compose.yml`.

## Running it

```bash
cd etl
pip install -r requirements-dev.txt   # runtime deps + pytest

python pipeline.py               # run once
python pipeline.py --schedule    # run every day at ETL_SCHEDULE_TIME
python -m pytest                 # run the unit tests
```

The database password is read from the repo's `.env` file. With Docker, run
`docker-compose up -d etl`. The source tables are created by the Spring app, so the `app`
service needs to be running too.

### Settings

| Environment variable | Default |
|---|---|
| `ETL_DB_HOST`, `ETL_DB_PORT`, `ETL_DB_NAME` | `localhost`, `5432`, `tns_capital` |
| `DB_USER`, `DB_PASSWORD` | from `.env` |
| `ETL_SCHEDULE_TIME` | `02:00` |
| `ETL_RETRY_ATTEMPTS` | `3` |
| `ETL_RETRY_DELAY_SECONDS` | `2` (doubles each retry) |
| `ALPACA_API_KEY_ID`, `ALPACA_API_SECRET_KEY` | blank: the price job is skipped |
| `PRICE_HISTORY_FEED` | `sip` (all US exchanges; free for data older than 15 minutes) |
| `PRICE_BACKFILL_YEARS` | `5` (how far back the first run loads) |

## Daily price history

Each run also loads daily price bars (open, high, low, close, volume, VWAP) for every
instrument from the [Alpaca market data API](https://docs.alpaca.markets/docs/historical-stock-data-1)
into `analytics.fact_daily_prices`, one row per symbol per trading day. Alpaca is the same
source the API uses for live prices, so the history and live prices agree.

| File | What it does |
|---|---|
| [market_data.py](market_data.py) | **Extract**: downloads daily bars from Alpaca, following page tokens and retrying when rate limited |
| [extract.py](extract.py) | `extract_last_price_dates()` reads which days are already loaded for each instrument |
| [transform.py](transform.py) | `build_fact_daily_prices()` turns the bars into rows and drops invalid ones |
| [load.py](load.py) | `load_prices()` upserts them on `(symbol, trade_date)` |

- **First run:** loads `PRICE_BACKFILL_YEARS` of history for every instrument.
- **Later runs:** start at each instrument's last loaded day. That day is downloaded again
  in case it was loaded before the market closed, and the upsert replaces it.
- **New instruments** are backfilled on the next run.
- **Prices are as traded,** not adjusted for splits or dividends, so they match order
  prices. A stock split shows as a jump in the chart.
- **Without Alpaca keys** the price job is skipped with a warning, and the trade job runs as before.
- **Failures:** if the price job fails, the run exits with code 1, but trades are still loaded.

### Integration tests

These run the whole pipeline against a real database. They delete and recreate tables, so
only use a throwaway database:

```bash
docker run -d --rm --name etl-it -e POSTGRES_PASSWORD=it -p 55432:5432 postgres:16
ETL_IT=1 ETL_DB_NAME=postgres ETL_DB_PORT=55432 DB_USER=postgres DB_PASSWORD=it python -m pytest tests/integration
docker stop etl-it
```

## Example report queries

Closing prices for a chart:

```sql
SELECT trade_date, close
FROM analytics.fact_daily_prices
WHERE symbol = 'AAPL' AND trade_date >= CURRENT_DATE - INTERVAL '1 year'
ORDER BY trade_date;
```

Total value of filled trades per asset class per month:

```sql
SELECT d.year, d.month, i.asset_class, SUM(f.notional) AS filled_value
FROM analytics.fact_trades f
JOIN analytics.dim_date d ON d.date_key = f.date_key
JOIN analytics.dim_instrument i ON i.symbol = f.symbol
WHERE f.is_filled
GROUP BY d.year, d.month, i.asset_class
ORDER BY d.year, d.month;
```
