# Decision Log

Design decisions for TNS Capital, and where the build deliberately differs from the
*Enterprise Trading Platform* specification. Each entry gives the reason, so the choice can
be explained at the showcase and revisited later.

| # | Decision | Spec reference | Status |
|---|---|---|---|
| 1 | Spring Data JPA alongside MyBatis | §9.2 task 4, §9.5 | Agreed with tutor |
| 2 | Orders are placed asynchronously (`202 Accepted`) | §19.1 | Team decision |
| 3 | Failed orders are stored `REJECTED` and captured in a dead-letter queue | §10.2, NFR-05 | Team decision |
| 4 | Our own Broker Simulator service | FR-12, §5 | In place |
| 5 | Live prices from Alpaca's market data API | FR-07, §5 | In place |
| 6 | Analytics in a PostgreSQL `analytics` schema, not Snowflake | §4.2, §18.2 | To confirm with tutor |
| 7 | Jenkins instead of GitHub Actions | §4.1 | In place |
| 8 | No TLS, SASL or Kafka ACLs in the local stack | §5 | Deployment concern |
| 9 | Trading API contract generated from the code; the committed copy is checked by `ApiContractIT` | §9.2 task 2 | In place |
| 10 | Separate SUPPORT and ADMIN roles: support works on traders' accounts in the trading app, admins run analytics and operations | §22 | Team decision |
| 11 | Two Spring CVEs accepted until the Spring Boot 4 upgrade | §22 (pipeline security) | Review each sprint |
| 12 | Admins sign in again on the analytics dashboard; no token handoff from Angular | §7, §11 | Team decision |
| 13 | The analytics dashboard is served under `/analytics/` on the web app's origin | §12, §14 | In place |
| 14 | Angular 19, not 21+ | §4.1, §4.2 | Team decision |
| 15 | The auth service runs on Node.js 22 LTS, not 20 | §4.2 | In place |

## 1. Spring Data JPA alongside MyBatis

The spec names MyBatis as the persistence approach. Entities and most queries use Spring Data
JPA, which the tutor approved; the original MyBatis mappers are kept and `OrderValidator`
still reads accounts, instruments and orders through them. Both work against the same
Postgres schema from `db/`, and Hibernate only validates that schema (`ddl-auto: validate`),
so the SQL in `db/tables` stays the single source of truth.

## 2. Asynchronous order placement

§19.1 shows `POST /api/v1/orders` answering `200` with the order already `FILLED`. Ours
answers `202 Accepted` with an `orderId` and the order stored as `NEW`; the Trade Executor
works it through Kafka and the Broker Simulator, and the client polls
`GET /api/v1/orders/{id}` (or `/events`) for `FILLED` or `REJECTED`.

Why: the spec's own architecture (§5) has trades executed asynchronously by a Trade Executor
via a Broker Simulator, which a synchronous `FILLED` response cannot express. It also means a
slow broker never holds an HTTP request open, and an order can be cancelled while it is
being worked (FR-01). Unknown accounts or instruments, invalid input and reused
idempotency keys are still rejected synchronously (404, 422, 409).

## 3. Rejected orders and the dead-letter queue

An order that fails after it was accepted (insufficient funds or holdings at the limit or
fill price, inactive account, broker unreachable) is stored as `REJECTED` with a reason,
published as a trade event, and captured in `dlq_messages` with an `is_retryable` flag. There
are no automatic Kafka retries. Admins review and replay messages through `/api/v1/dlq`.

Why: chosen by the team (PR #72) so every failure is visible and auditable, and so a
business failure (e.g. insufficient funds) is never retried blindly.

## 4. Broker Simulator

The spec says a Broker Simulator API is *provided*. Ours is `broker-simulator/`, modelled on
the course's reference execution engine: orders arrive over HTTP, and after 0.5 to 2 seconds
each is filled in full at or better than its limit price and published to the `executions`
topic. If a real broker simulator is supplied, only `BrokerSimulatorClient` (the HTTP call)
and the `ExecutionEvent` contract need to change.

**Limit orders are immediate-or-cancel.** The simulator used to fill every order at its limit,
so a BUY of a $227 stock with a $1 limit filled at $1. Now, when a live price is available:

- the trading API refuses a limit that would not fill at it when the order is placed (a BUY
  limit below the market or a SELL limit above it: `422 ORD-422`, nothing stored);
- the Trade Executor sends the live price with the order, and the simulator fills at it, or
  at the limit if the market has moved past the limit since (never worse than the limit).

Without a live price (no Alpaca keys, as in local development and CI) orders fill relative to
their limit as before. Resting limit orders that wait for the market to reach them would need
the price stream to drive fills; they are left for a later extension.

**Cash and shares are held when an order is accepted.** When the Trade Executor accepts an
order, it moves the order's cost at its limit (BUY) or its shares (SELL) into
`accounts.reserved_cash` or `positions.reserved_quantity`; `orders.reserved` records that the
order holds them. Another order or a withdrawal can only use what is left (`cash_balance -
reserved_cash`), so two orders can no longer both pass the check and one then fail at
settlement. The hold is released when the order is filled (settlement then moves the cash and
shares at the fill price, which is never worse than the limit), cancelled or rejected; a DLQ
replay of a rejected order takes it again. Reserving, releasing and settling lock the account
row, so concurrent orders for one account take turns, and `accounts.version` still guards
deposits and withdrawals. A fill for an order that was cancelled meanwhile is ignored. A real
broker would also need to be told about the cancellation; the simulator has no cancel
endpoint.

## 5. Live prices from Alpaca

FR-07 asks for positions priced against live market data from a provided pricing API. We use
Alpaca's free market data API (see `docs/MARKET_DATA_PROVIDER_EVALUATION.md`): a WebSocket
stream keeps the latest trade price of each instrument in memory, with the REST API as a
fallback. Positions are valued at that price, and an order without a price is priced at it
when accepted. Without Alpaca keys (local development, CI) there are no live prices: orders
must include a price and `marketValue` is `null`.

The API republishes the prices it takes on the `market-data` Kafka topic listed in §10.3 (at
most one per symbol per second), which the strategy service consumes (decision 16).

## 6. PostgreSQL `analytics` schema instead of Snowflake

§4.2 lists Snowflake as the data warehouse. The ETL loads the same star schema (§18.2:
`dim_account`, `dim_instrument`, `dim_date`, `fact_trades`) into a separate `analytics`
schema in our PostgreSQL. The Docker Compose stack has no Snowflake instance, so this keeps
the whole pipeline runnable and testable locally, and a separate schema still keeps reporting
queries away from the trading tables. Moving to Snowflake
means changing `etl/load.py` and the connection settings; the transforms and their tests are
warehouse-independent. **To confirm with the tutor.**

## 7. Jenkins instead of GitHub Actions

§4.1 mentions GitHub Actions. The course environment provides Jenkins (with SonarQube), so the
pipeline is a `Jenkinsfile`: secret scanning (Gitleaks), SAST (Semgrep), build, unit and
integration tests, the ETL, dashboard, broker and auth suites, SonarQube with its quality
gate, and dependency and image scanning (Trivy). The AWS deployment in sprint 11 may still
use a GitHub Action for the S3 upload and CloudFront invalidation.

## 8. Transport security in the local stack

§5 calls for TLS 1.2+ in transit and Kafka TLS, SASL and ACLs. The Docker Compose stack is a
local development environment and uses plain HTTP and Kafka PLAINTEXT. Application-level
controls are in place: signed JWTs with issuer and type checks, role checks, account
ownership checks, input validation, parameterised SQL, rate limiting on the auth endpoints,
and the broker is reachable only inside the Compose network. TLS belongs to the deployed
environment (sprint 11: CloudFront terminates TLS for the UI).

## 9. Trading API contract

The auth service has a hand-written contract (`contracts/auth-api.yaml`). The trading API's
OpenAPI document is generated from the code by springdoc and served at `/v3/api-docs` (Swagger
UI at `/swagger-ui.html`). Each endpoint documents its success status and its errors with
`@Operation` / `@ApiResponse`; an error's description starts with its code from the spec's
catalogue (§21), and `OpenApiConfig` gives every coded error the `ErrorResponse` body and adds
the 401, 422 and 500 that all endpoints share.

A copy is committed as `contracts/trading-api.json`, and the Angular clients are generated from
it. `ApiContractIT` fails when the copy no longer matches `/v3/api-docs`, writing the live spec
to `target/trading-api.json`; after reviewing the difference, update the copy with
`python3 -m json.tool --indent 2 target/trading-api.json contracts/trading-api.json`.

## 10. Roles

Users have the role `USER`, `SUPPORT` or `ADMIN`. The two staff roles have no trading account
and are kept apart, so neither one does the other's job:

| Role | Where | Can do |
|---|---|---|
| `USER` | Trading app | Only the account opened when they registered |
| `SUPPORT` | Trading app (account viewer) | View every account and order read-only; suspend or reactivate accounts; open accounts; add and change instruments |
| `ADMIN` | Analytics dashboard (`/analytics/`) | The analytics dashboard, the DLQ endpoints, the actuator's metrics, info and health details (observability) |

An admin is not support: the trading API gives `ADMIN` no access to traders' accounts, and the
Angular app sends an admin who signs in there to the analytics dashboard (`notAdminGuard`).
Support is turned away by the analytics dashboard. The auth service creates both accounts at
startup, `admin` from `ADMIN_PASSWORD` and `support` from `SUPPORT_PASSWORD`, refuses the same
password for both, and never changes an existing user's role. A user holds one role
(`users.role`).

`STRATEGY` is a service role, never a user's: only the strategy service's client-credentials
token has it (decision 16). It may place and read orders and read prices, and only for an
account whose owner has an ACTIVE strategy; every other endpoint refuses it. In Strategy Lab,
support sees every trader's strategies read-only, like their accounts; admins see strategy orders
in the analytics dashboard only.

## 11. Accepted vulnerabilities

`.trivyignore` accepts CVE-2026-47884 (XSLT views) and CVE-2026-47890 (Server-Sent Events) in
`spring-webmvc` 6.2.19. The API uses neither feature, and the only fixes are in Spring 7
(Spring Boot 4), a major upgrade to plan separately. Each entry has an owner and a review
date.

## 12. Admin analytics sign-in

The admin analytics dashboard is the Streamlit app in `dashboard/` (spec §7 asks for a Python
dashboard). The Angular login sends admins there. The read-only account viewer in the Angular app
belongs to support, not admins (see 10): support staff pick any account and see its balance,
holdings and orders, without the order ticket, and can suspend or reactivate it.

Streamlit's session lives in its Python server and cannot read the Angular app's token, which
is kept in the browser tab's `sessionStorage`, so the admin signs in on each (even though both
are now served on one origin, see 13). Passing the access token across in the URL was rejected: URLs end up in browser
history, server logs and `Referer` headers, and the token would stay usable for 15 minutes. The
safe alternative, a single-use, short-lived code that Streamlit exchanges with the auth service,
needs new auth service endpoints and a table; it is a possible extension. Until then the second
sign-in is the deliberate price of never putting a token in a URL. The analytics dashboard
verifies the token it gets itself (HS256 signature, issuer, `typ`, expiry) and admits only the
`ADMIN` role.

When a signed-out user opens a protected page, the login sends them back to it afterwards
(`?returnUrl=`).

## 13. Analytics dashboard under `/analytics/`

The Angular app's nginx (and `ng serve`'s `proxy.conf.json`) forwards `/analytics/` to the
Streamlit container, which runs with the base path `analytics` (`dashboard/.streamlit/config.toml`),
WebSocket included. The home page's admin link and the admin login both go to that
relative path. Linking to `http://localhost:8501` only worked when the browser ran on the Docker
host with that port open: through a forwarded or remote address it gave "page not found", and a
browser in the end-to-end tests could not reach it either. The same pattern carries over to the
sprint 11 deployment, where CloudFront can route `/analytics/*` to the dashboard's origin. Port 8501
stays published for direct access (`http://localhost:8501/analytics/`).

## 14. Angular 19

§4 lists Angular 21+. The frontend was generated with Angular 19.2 and the team stays on it for
now: everything the spec asks of the UI (standalone components, signals, the new control flow,
functional guards and interceptors, typed API clients) is available in 19, and a two-major
upgrade during the build would touch every component, the test setup and the generated API
clients for no change a user would see. The upgrade (`ng update @angular/core@21
@angular/cli@21`, one major at a time) is revisited if the tutor asks for it.

## 15. Node.js 22 for the auth service

§4.2 lists Node.js 20 LTS. Node 20 reached end of life on 2026-04-30 and no longer gets security
fixes (docs/OWASP_REVIEW.md A06-3), so the auth service builds, tests (Jenkins) and runs on
`node:22-alpine`, the current LTS. NestJS 11, Argon2 and the test suite run unchanged on it. The
runtime image also drops the npm CLI, which only the build needs; it carried every HIGH finding
in Trivy's scan of the image (A06-4).

## 16. Sprint 10 extension: Strategy Lab (automated strategy execution)

The team chose **automated strategy execution** (§13.2): rules-based strategies that watch the
real-time price stream and act through the Trade REST API. The proposal is in the team's Strategy
Lab write-up; what was built, and why:

- **Its own microservice.** `strategy-service/` (Spring Boot, port 8087) owns the `strategies` and
  `strategy_signals` tables and logs in to Postgres as `tns_strategy`, which can read the price
  history, who owns which account and the outcome of orders, but cannot change orders, accounts or
  positions. nginx routes `/api/v1/strategies` to it.
- **Rules are strategy classes.** `TradingRule` with `MovingAverageCrossoverRule` and
  `BollingerBandRule`; the backtester and the live engine run any rule the same way. A rule signals
  only on the bar where its condition starts (a cross), so a strategy trades once per move.
- **Backtest on daily prices, trade live on 1-minute bars.** Five years of daily closes come from the
  ETL's `analytics.fact_daily_prices`. A daily crossover fires a few times a year, so live trading uses
  1-minute bars built from the `market-data` stream; the rule's settings mean bars in both cases.
- **The service places orders through the trading API, as a trader would** (`POST /api/v1/orders`),
  so every check still applies. It cannot use a trader's token (they expire after 15 minutes and the
  trader may be logged out), and writing to the `orders` topic would skip every check (OWASP A01-5).
  Instead the auth service issues it its own 15-minute token by client credentials
  (`POST /auth/token`, `STRATEGY_CLIENT_SECRET`), with the service role `STRATEGY` (decision 10). The
  API accepts that role only for placing and reading orders on an account with an ACTIVE strategy.
- **Market orders only.** The strategy sends no price, so the API prices the order at the live market.
  Without market data keys (CI, local) the API answers PRC-503 and the service resends the order at the
  signal's price. Resting limit orders stay out of scope.
- **Long only, one position per strategy.** A strategy buys its quantity when FLAT and sells it when
  LONG, so it never sells the trader's own shares. It waits for each order to settle before the next bar
  (up to 15 s): acting earlier sold shares whose purchase had not filled yet, which the API rejected.
  Its position moves only on a fill.
- **Replay for the demo.** Markets are closed for most of a UK day, so `POST /api/v1/market-data/replays`
  publishes a past day's regular-session 1-minute bars on `market-data` at up to 50 a second, marked
  `REPLAY` and kept apart from live bars. Any signed-in user may start one; only one runs at a time, as
  every strategy on the symbol sees it. Orders are still priced at the live market price.
- **Limits.** Up to 3 strategies per account can be on (`STRATEGY_MAX_ACTIVE_PER_ACCOUNT`; the MVP had
  1). Support staff see every strategy and backtest but change none, as they only view traders'
  accounts (decision 10); admins, who work in the analytics dashboard, see no strategies in the app.
  Deleting a strategy archives it, so its signals stay on record.
- **Reporting.** Traders get a toast whenever a strategy trades, and a comparison of their strategies'
  realized P&L with their own (average cost). The admin analytics dashboard shows strategy orders next
  to everyone else's.

