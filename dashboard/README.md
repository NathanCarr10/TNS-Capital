# TNS Capital Dashboard

A Streamlit dashboard that turns the trading data into business insights for a
non-technical audience. It reads two sources and lets you switch between them:

| Source | Tables | Updated by |
|---|---|---|
| **Live** | `orders`, `accounts`, `instruments`, `positions` | The Kafka order consumer, as each order is processed |
| **Batch** | `analytics.fact_trades` and its dimensions | The ETL pipeline (`etl/`), once a day |

The **Orders not yet in batch** figure shows the gap between the two: live orders the next
ETL run will load.

## Insights

| Insight | Function in `insights.py` |
|---|---|
| Trade volume: count and value of filled trades by day | `trade_volume_by_date` |
| Most active accounts | `most_active_accounts` |
| Fill rate: share of finished orders that filled | `fill_rate`, `status_breakdown` |
| Exposure by instrument (cost basis of positions) | `exposure_by_instrument` |
| Average trade size | `average_trade_size` |
| Buy and sell value by asset class | `value_by_asset_class` |

The top of the page also states the main findings in plain sentences.

## Files

| File | What it does |
|---|---|
| [data.py](data.py) | Loads the live and batch data from PostgreSQL into DataFrames |
| [insights.py](insights.py) | Pure pandas functions, one per insight; no database or UI code |
| [app.py](app.py) | The Streamlit page: headline numbers, insight sentences and Plotly charts |
| [tests/](tests/) | pytest unit tests for every insight function |

## Running it

With Docker, it runs as the `dashboard` service: `docker compose up -d dashboard`, then
open http://localhost:8501 (change the port with `DASHBOARD_PORT`). Run the ETL once
(`python etl/pipeline.py`) to fill the batch source.

Locally:

```bash
cd dashboard
pip install -r requirements.txt
streamlit run app.py         # reads DB_USER and DB_PASSWORD from the repo's .env
```

| Environment variable | Default |
|---|---|
| `DASHBOARD_DB_HOST`, `DASHBOARD_DB_PORT`, `DASHBOARD_DB_NAME` | `localhost`, `5432`, `tns_capital` |
| `DB_USER`, `DB_PASSWORD` | from `.env` |

The page caches the data for 30 seconds; reload after that to see new orders.

## Tests

```bash
cd dashboard
pip install -r requirements-dev.txt
python -m pytest
```

The tests use small hand-made DataFrames, so they need no database. Jenkins runs them in
the **Dashboard Tests** stage.
