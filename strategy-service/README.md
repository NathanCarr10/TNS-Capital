# Strategy service

Strategy Lab's backend: the platform's Sprint 10 extension, automated strategy execution
(spec §13.2; design and decisions in [docs/DECISIONS.md](../docs/DECISIONS.md) 16, flow in
[docs/diagrams/STRATEGY-LAB.md](../docs/diagrams/STRATEGY-LAB.md)).

- **Backtests** a rule on the daily closes the ETL loads into `analytics.fact_daily_prices`.
- **Runs** the strategies traders switch on: consumes `market-data`, builds 1-minute bars per symbol
  (live and replayed kept apart), and on each closed bar places market orders through the trading API
  with its own `STRATEGY` token from the auth service (`POST /auth/token`).
- **REST API** at `/api/v1/strategies` (contract: [contracts/strategy-api.json](../contracts/strategy-api.json),
  Swagger UI at `/swagger-ui.html`): traders manage their own strategies; support staff read every strategy (admins: none, they work in
  the analytics dashboard).

| Rule | Settings (defaults) | Signals |
|---|---|---|
| `MA_CROSSOVER` | `shortWindow` (20), `longWindow` (50) bars | BUY when the short average crosses above the long one, SELL when it crosses back |
| `BOLLINGER` | `window` (20) bars, `width` (2) standard deviations | BUY when the close drops below the lower band, SELL when it rises above the upper band |

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | localhost, `tns_strategy` | Its least-privilege database role |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka, for `market-data` |
| `JWT_SECRET` | none | Verifies users' tokens, as the trading API does |
| `TRADING_API_URL` / `AUTH_URL` | `localhost:3000` / `localhost:4000` | Where it places orders and gets its token |
| `STRATEGY_CLIENT_SECRET` | none | Its client secret at the auth service; without it strategies cannot trade |
| `STRATEGY_MAX_ACTIVE_PER_ACCOUNT` | 3 | Strategies one account may have switched on |

## Tests

`mvn verify`: unit tests (rules, backtester, bars, engine, clients, service), then `StrategyServiceIT`,
which runs the service against Postgres built from `../db` (as `tns_strategy`) and Kafka with
Testcontainers, with a stand-in auth service and trading API, and checks the committed contract.
Update the contract after an API change with
`python3 -m json.tool --indent 2 target/strategy-api.json ../contracts/strategy-api.json`.
