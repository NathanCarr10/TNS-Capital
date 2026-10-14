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
| 9 | Trading API contract generated live, not a hand-written OpenAPI file | §9.2 task 2 | In place |
| 10 | Only admins open accounts and manage instruments | §22 | Team decision |
| 11 | Two Spring CVEs accepted until the Spring Boot 4 upgrade | §22 (pipeline security) | Review each sprint |
| 12 | Admins sign in again on the analytics dashboard; no token handoff from Angular | §7, §11 | Team decision |
| 13 | The analytics dashboard is served under `/analytics/` on the web app's origin | §12, §14 | In place |

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

Settlement re-checks funds and holdings at the fill price, because another order may have
used them while the broker worked this one; funds are not reserved when the order is
accepted. A fill for an order that was cancelled meanwhile is ignored. A real broker would
also need to be told about the cancellation; the simulator has no cancel endpoint.

## 5. Live prices from Alpaca

FR-07 asks for positions priced against live market data from a provided pricing API. We use
Alpaca's free market data API (see `docs/MARKET_DATA_PROVIDER_EVALUATION.md`): a WebSocket
stream keeps the latest trade price of each instrument in memory, with the REST API as a
fallback. Positions are valued at that price, and an order without a price is priced at it
when accepted. Without Alpaca keys (local development, CI) there are no live prices: orders
must include a price and `marketValue` is `null`.

Prices are not yet republished on the `market-data` Kafka topic listed in §10.3; that would
let other services (e.g. price alerts) consume them.

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
UI at `/swagger-ui.html`), so it cannot drift from the implementation. The sprint 9 Angular
clients can be generated from that URL.

## 10. Roles

Users have the role `USER` or `ADMIN`. A user can use only the account opened when they
registered; admins can use every account. Admin-only: opening accounts, creating or changing
instruments, and the DLQ endpoints. Any further role rules are to be agreed by the team.

## 11. Accepted vulnerabilities

`.trivyignore` accepts CVE-2026-47884 (XSLT views) and CVE-2026-47890 (Server-Sent Events) in
`spring-webmvc` 6.2.19. The API uses neither feature, and the only fixes are in Spring 7
(Spring Boot 4), a major upgrade to plan separately. Each entry has an owner and a review
date.

## 12. Admin analytics sign-in

The admin analytics dashboard is the Streamlit app in `dashboard/` (spec §7 asks for a Python
dashboard). The Angular login sends admins there, and its **Trading dashboard** button brings
them back to the Angular app. Admins have no trading account of their own, so there the
dashboard becomes a read-only account viewer: the admin picks any account (the API already lets
admins read every account) and sees its balance, holdings and orders, without the order ticket.
Whether admins may place orders for traders is left for the team to decide.

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
(`?returnUrl=`), admins included. That is how the Trading dashboard button, which opens a new
tab without the Angular session, lands on the account viewer and not back on analytics.

## 13. Analytics dashboard under `/analytics/`

The Angular app's nginx (and `ng serve`'s `proxy.conf.json`) forwards `/analytics/` to the
Streamlit container, which runs with the base path `analytics` (`dashboard/.streamlit/config.toml`),
WebSocket included. The Admin card, the admin login and the Analytics button all go to that
relative path. Linking to `http://localhost:8501` only worked when the browser ran on the Docker
host with that port open: through a forwarded or remote address it gave "page not found", and a
browser in the end-to-end tests could not reach it either. The same pattern carries over to the
sprint 11 deployment, where CloudFront can route `/analytics/*` to the dashboard's origin. The
Streamlit **Trading dashboard** button is the relative `/dashboard` for the same reason. Port 8501
stays published for direct access (`http://localhost:8501/analytics/`).
