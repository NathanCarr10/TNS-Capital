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
or support user who signs in is turned away, and no data is loaded until an admin is signed in.
Support staff work in the Angular trading app instead (decision 10 in
[docs/DECISIONS.md](../docs/DECISIONS.md)).

The sidebar switches between the trading insights below and the [Monitoring](#monitoring) view.

Admins who sign in through the Angular trading app are sent here. The admin signs in here
separately, as Streamlit cannot read the Angular app's token; see decision 12.

The admin is `admin`, with the `ADMIN_PASSWORD` from `.env`.

### Staying signed in

A page reload keeps the admin signed in, for up to 8 hours from sign-in
(`DASHBOARD_SESSION_HOURS`). [sessions.py](sessions.py) keeps the access and refresh tokens
on the server, under a random session id. The browser only gets that id, in the
`tns_dashboard_session` cookie: path `/analytics`, `SameSite=Strict`, and `Secure` over
HTTPS. Streamlit cannot send `Set-Cookie` headers, so the page sets the cookie from a
script. It is therefore not `HttpOnly`, which is why it carries no token.

The token is still checked on every interaction. When the access token expires (15 minutes),
the dashboard swaps it for a new one through `POST /auth/refresh`, once per session even with
several tabs open, as presenting a refresh token twice looks like theft (OWASP A09-1). The admin
goes back to the sign-in form only when the 8 hours are up, the refresh is refused or the
dashboard restarts, which forgets every session. **Sign out** ends the session here and at the
auth service (`POST /auth/logout`) and clears the cookie.

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

Below the per-user table, choose an account holder (listed by holder name and account number)
to see their fill rate, average trade size, buy/sell mix and every order they placed. The list
is keyed by account, since holder names can repeat. Each trade is also labelled with the
username that owns the account (`users.account_id`); accounts no user owns, such as the seeded
ones, show as `(no login)`.

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

## Monitoring

The **Monitoring** view (sidebar) shows the monitoring stack: alerts, metrics and every
service's logs ([observability/README.md](../observability/README.md)). It needs the stack
running (`docker compose --profile observability up -d`); otherwise it says how to start it.
While alerts are firing, the sidebar says so on every view.

| Section | Shows | Source |
|---|---|---|
| Alerts | Firing and pending alerts, most severe first: what happened, what to do, OWASP finding | vmalert |
| Services | Whether the API, auth service and broker can be scraped; the Alpaca price stream's state | `up`, `marketdata_stream_state` |
| Order pipeline | Orders accepted; 95th percentile time to fill; dead letters awaiting review; largest consumer lag. Charts: orders accepted by side, finished by outcome, median and 95th percentile fill time, consumer lag by group, dead letters | `trading_*`, `kafka_consumer_group_lag` |
| Security events | Failed logins, reused refresh tokens, API 401/403 and 5xx over the period. Charts: logins by outcome, refused or failed API requests by status | `auth_*`, `http_server_requests_seconds_count` |
| Resources | Heap in use by service | `jvm_memory_used_bytes`, `nodejs_heap_used_bytes` |
| Logs | Search by text, service and level, newest first; saved searches for failed logins, refresh token reuse, access denied and errors | VictoriaLogs |

The period (15 minutes to 7 days) applies to every number and chart. With **Live** on (the
default) the panels refresh every 10 s, the scrape interval, without reloading the page; turn
it off to read a chart without it moving. Rates have no fixed window: each point covers its
own step (10 s over 15 minutes), so a burst of orders shows straight away rather than being
smoothed over five minutes. The log search text is sent as a quoted phrase, so it cannot change the
query. `monitoring.py` holds the queries and turns the answers into DataFrames;
`monitoring_page.py` draws the view.

## Files

| File | What it does |
|---|---|
| [auth.py](auth.py) | Signs in through the auth service and checks the token is a valid admin one |
| [sessions.py](sessions.py) | Keeps signed-in admins' tokens on the server, so a reload stays signed in |
| [data.py](data.py) | Loads the live and batch data from PostgreSQL into DataFrames |
| [insights.py](insights.py) | Pure pandas functions, one per insight; no database or UI code |
| [app.py](app.py) | The Streamlit page: headline numbers, insight sentences and Plotly charts |
| [monitoring.py](monitoring.py) | Queries the monitoring stack: PromQL, alerts, LogsQL |
| [monitoring_page.py](monitoring_page.py) | The Monitoring view |
| [tests/](tests/) | pytest unit tests for every insight function |

## Running it

With Docker, it runs as the `dashboard` service: `docker compose up -d dashboard`, then
open it through the trading app at http://localhost:8086/analytics/, or directly at
http://localhost:8501/analytics/ (change the port with `DASHBOARD_PORT`). The `/analytics` base path
is set in [.streamlit/config.toml](.streamlit/config.toml) so the app's nginx can forward that path here. Run the ETL once
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
| `MONITORING_METRICS_URL`, `MONITORING_ALERTS_URL`, `MONITORING_LOGS_URL` | unset, so Monitoring is off (compose: the `observability` services) |

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
  sign-in, traders turned away, expired and forged sessions, sign-out, the per-user view, and
  the Monitoring view with the stack running, down and not configured
- `tests/test_sessions.py`: the session store: expiry, token rotation, one refresh for many tabs
- `tests/test_monitoring.py`: the monitoring stack's answers parsed into DataFrames, and the
  LogsQL the log search sends, with a fake HTTP client
- `tests/integration/test_data_postgres.py`: `data.py` against a real database. Skipped
  unless `DASHBOARD_IT=1`; it drops and recreates tables, so only point it at a throwaway
  database (see the file for a one-line setup)

`ci/python-tests.sh dashboard` (from the repo root) runs everything, database tests
included, against a throwaway Postgres in Docker. Jenkins runs it in the **Dashboard Tests**
stage and SonarQube picks up the coverage.
