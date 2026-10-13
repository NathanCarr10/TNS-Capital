# TNS Capital Dashboard

A Streamlit dashboard that turns the trading data into business insights for a
non-technical audience. It reads two sources and lets you switch between them:

| Source | Tables | Updated by |
|---|---|---|
| **Live** | `orders`, `accounts`, `instruments`, `positions` | The Kafka order consumer, as each order is processed |
| **Batch** | `analytics.fact_trades` and its dimensions | The ETL pipeline (`etl/`), once a day |

The **Orders not yet in batch** figure shows the gap between the two: live orders the next
ETL run will load.

## Admins only

The dashboard shows every user's trades, so only admins can open it. The page asks for a
username and password and signs in through the auth service (`POST /auth/login`). It then
checks the access token itself, the same checks the auth service's guard makes: HS256
signature with the shared `JWT_SECRET`, issuer `urn:tns-capital:auth-service`, `typ` "access"
and expiry. Only a token whose `roles` include `ADMIN` gets past the sign-in form. A trader
who signs in is turned away, and no data is loaded until an admin is signed in.

Admins who sign in through the Angular trading app are sent here, and the sidebar's
**Trading dashboard** button takes them back to it, where they can view any account
read-only. The two apps run on different origins, so the admin signs in here separately;
see decision 12 in [docs/DECISIONS.md](../docs/DECISIONS.md).

The token is checked again on every interaction, so when it expires (15 minutes) the page
goes back to the sign-in form. The seeded admin is `admin` / `adminPassword`.

## Insights

| Insight | Function in `insights.py` |
|---|---|
| Trade volume: count and value of filled trades by day | `trade_volume_by_date` |
| Most active accounts | `most_active_accounts` |
| Fill rate: share of finished orders that filled | `fill_rate`, `status_breakdown` |
| Exposure by instrument (cost basis of positions) | `exposure_by_instrument` |
| Average trade size | `average_trade_size` |
| Buy and sell value by asset class | `value_by_asset_class` |
| Trading by user: orders, fill rate, bought and sold value, top instrument, last order | `trades_by_user` |
| Why orders are rejected, grouped into categories | `rejection_reasons`, `rejected_orders` |
| Time from order to fill: median, 95th percentile, slowest fills | `fill_time_summary`, `fill_times` |
| Stuck orders: still NEW after 5 minutes | `stuck_orders` |
| Dead-letter queue by failure type: total, awaiting review, retryable | `dlq_summary` |

Below the per-user table, choose a user to see their fill rate, average trade size, buy/sell
mix and every order they placed. Each trade is labelled with the username that owns the
account (`users.account_id`); accounts no user owns, such as the seeded ones, show as
`(no login)`.

The top of the page also states the main findings in plain sentences.

## Operations

Two sections always use the live tables, whichever source is selected, because the batch
schema has no rejection reasons or execution times:

- **Why orders are rejected.** `orders.status_reason` is free text with symbols and
  amounts in it ("Cannot sell 20 of MSFT, only 0 available"), so `rejection_category`
  groups reasons by keyword: insufficient funds, insufficient holdings, account not
  active, instrument not tradable, no live price, system failure, or Other. A chart shows
  the count per category and a table lists each rejected order with its full reason.
- **Order pipeline health.** Time to fill is measured from `orders.created_on` to the
  order's first row in `executions`. Orders still `NEW` after 5 minutes are flagged as
  stuck, since the executor normally settles an order within seconds; that usually means
  the Kafka consumer or the broker simulator is down. The dead-letter queue
  (`dlq_messages`) is summarised by failure type. It is read-only here: retrying and
  dismissing messages stays with the DLQ admin API.

## Files

| File | What it does |
|---|---|
| [auth.py](auth.py) | Signs in through the auth service and checks the token is a valid admin one |
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
| `DASHBOARD_AUTH_URL` | `http://localhost:4000` (compose: `http://auth-service:4000`) |
| `JWT_SECRET` | from `.env`; must be the auth service's secret |
| `DASHBOARD_TRADING_URL` | `http://localhost:4200/dashboard`: the Angular trading app |

The auth service has to be running to sign in: `docker compose up -d auth-service`.

The page caches the data for 30 seconds; reload after that to see new orders.

## Tests

```bash
cd dashboard
pip install -r requirements.txt -r requirements-dev.txt
python -m pytest
```

- `tests/test_insights.py`: every insight function, on small hand-made DataFrames
- `tests/test_auth.py`: token checks with real signed JWTs (expired, wrong signature,
  issuer, type or algorithm, no roles) and the sign-in call with a fake HTTP client
- `tests/test_app.py`: the Streamlit page rendered headlessly (`AppTest`) with fake data:
  sign-in, traders turned away, expired and forged sessions, sign-out, and the per-user view
- `tests/integration/test_data_postgres.py`: `data.py` against a real database. Skipped
  unless `DASHBOARD_IT=1`; it drops and recreates tables, so only point it at a throwaway
  database (see the file for a one-line setup)

`ci/python-tests.sh dashboard` (from the repo root) runs everything, database tests
included, against a throwaway Postgres in Docker. Jenkins runs it in the **Dashboard Tests**
stage and SonarQube picks up the coverage.
