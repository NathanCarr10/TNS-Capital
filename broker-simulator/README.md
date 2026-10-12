# Broker Simulator

A small Spring Boot service that stands in for a real broker or exchange. The
trading API's Trade Executor sends it each validated order over HTTP; the
simulator "works" the order and reports the fill on the `executions` Kafka
topic, where the API settles it. Modelled on the course's reference
execution engine (`LeapExample/execution-engine`).

```
Trading API (Trade Executor) --POST /api/v1/orders--> Broker Simulator
                                                         | after 0.5-2 s
Trading API (settlement)  <--- executions topic --------+  ExecutionEvent
```

## API

`POST /api/v1/orders`

```json
{ "orderId": "4f63fed9-6d52-424b-844b-504313595bb9", "accountId": 11, "symbol": "ACME",
  "side": "BUY", "quantity": 10, "limitPrice": 30.00 }
```

Answers `202 {"orderId": "...", "status": "ACCEPTED"}` straight away, or `400 VAL-422` for an
invalid order. The fill arrives later on Kafka, not in this response.

`GET /actuator/health` is the health check.

## The simulated market

- **Delay:** random, between `BROKER_MIN_DELAY` and `BROKER_MAX_DELAY` (default 500 ms to 2 s).
- **Price:** every order is a limit order filled in full, at or better than its limit by up to
  `BROKER_MAX_PRICE_IMPROVEMENT_BPS` basis points (default 50, i.e. 0.5%). A BUY never fills
  above its limit and a SELL never below it.

## The `executions` topic

Keyed by account ID (so one account's fills arrive in order), 3 partitions:

```json
{ "executionId": "…", "orderId": "…", "accountId": 11, "symbol": "ACME", "side": "BUY",
  "quantity": 10, "price": 29.91, "venue": "SIM", "executedOn": "2026-10-12T10:00:01.234Z" }
```

## Running and testing

It runs as the `broker-simulator` service in `docker-compose.yml`. It is only reachable inside
the Compose network: it has no authentication, so it is not published on a host port.

```bash
cd broker-simulator
mvn test                 # SimulatedMarket, Broker and the REST API
mvn spring-boot:run      # needs Kafka on SPRING_KAFKA_BOOTSTRAP_SERVERS (default localhost:9092)
```

| Setting | Env var | Default |
|---|---|---|
| `server.port` | `SERVER_PORT` | `8085` |
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `broker.min-delay` / `broker.max-delay` | `BROKER_MIN_DELAY` / `BROKER_MAX_DELAY` | `500ms` / `2000ms` |
| `broker.max-price-improvement-bps` | `BROKER_MAX_PRICE_IMPROVEMENT_BPS` | `50` |

Set both delays to `10s` to have time to watch (or cancel) orders sitting at `NEW`.
