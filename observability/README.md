# Monitoring stack

Metrics, alerts and logs for every service, shown to admins in the analytics dashboard's
**Monitoring** view (`/analytics/`, sidebar → Monitoring). It is off by default:

```bash
docker compose --profile observability up -d
```

It is built to fit next to the rest of the stack on a small machine: about 90 MB measured,
at most 400 MB by its memory limits. Grafana, Prometheus, Loki and a tracing backend would
need 0.8–1.5 GB (decision 16 in [docs/DECISIONS.md](../docs/DECISIONS.md)).

| Service | Image | Memory (limit) | Does |
|---|---|---|---|
| `victoriametrics` | `victoriametrics/victoria-metrics:v1.153.0` | ~40 MB (160 MB) | Scrapes the three services every 15 s, keeps 15 days, answers PromQL |
| `vmalert` | `victoriametrics/vmalert:v1.153.0` | ~20 MB (64 MB) | Evaluates [vmalert/alerts.yml](vmalert/alerts.yml) every 30 s |
| `victorialogs` | `victoriametrics/victoria-logs:v1.53.0` | ~20 MB (128 MB) | Stores every container's logs for 7 days (at most 2 GiB), answers LogsQL |
| `fluent-bit` | `fluent/fluent-bit:5.1.3` | ~10 MB (48 MB) | Ships the containers' log lines to VictoriaLogs |

None of them publishes a port, and nginx proxies none of them: the dashboard reaches them on the
Compose network, after an admin has signed in. They have no login of their own.

## What is measured

| Metric | From | Shows |
|---|---|---|
| `trading_orders_accepted_total{side}` | API, `TradingMetrics` | Order rate |
| `trading_orders_finished_total{status}` | API, `TradingMetrics` | Filled, rejected and cancelled orders |
| `trading_order_fill_time_seconds_bucket` | API, `TradingMetrics` | Time from accepting an order to filling it (histogram: 0.25 s to 5 min) |
| `trading_dlq_pending` | API, `OperationsMetrics` | Dead letters awaiting review (A09-4) |
| `kafka_consumer_group_lag{group, topic}` | API, `KafkaLagMonitor` | Messages each consumer group has not processed, read from the broker |
| `marketdata_stream_state{state}` | API, `OperationsMetrics` | Alpaca stream: OFF, CONNECTING, STREAMING or FAILED |
| `http_server_requests_seconds_count{status, uri}` | API, Spring Boot | 401/403 (refused) and 5xx (SYS-500) responses |
| `jvm_memory_used_bytes{area="heap"}` | API, broker | Heap in use |
| `auth_logins_total{outcome}` | Auth service | Successful and failed logins (A09-2) |
| `auth_refresh_token_reuse_total` | Auth service | Reused refresh tokens; each one revoked a session (A09-1) |
| `auth_http_responses_total{status}` | Auth service | 401s and rate-limited 429s |
| `up{job}` | VictoriaMetrics | Whether each service could be scraped |

The API serves its metrics on its own port (`METRICS_PORT`, 9464), not through the actuator, which
stays admin-only on :3000. The auth service does the same. The broker simulator has no host port and
no authentication, so it serves `/actuator/prometheus` on :8085. Series nothing reads (Tomcat, class
loading, executor pools...) are dropped at scrape time ([victoriametrics/scrape.yml](victoriametrics/scrape.yml)).

## Alerts

| Alert | Fires when | Severity | OWASP |
|---|---|---|---|
| `RefreshTokenReuse` | Any refresh token reused in 15 min | critical | A09-1 |
| `LoginFailureSpike` | 10+ failed logins in 5 min | warning | A09-2 |
| `AuthRateLimited` | Any 429 from the auth service in 5 min | warning | A09-2 |
| `ApiAccessDeniedSpike` | 20+ API 401/403 responses in 5 min | warning | A09-5 |
| `ApiServerErrors` | Any API 5xx in 5 min | warning | A09-5 |
| `ServiceDown` | A service cannot be scraped for 1 min | critical | |
| `DeadLettersPending` | A dead letter waits over 1 min | warning | A09-4 |
| `KafkaConsumerLag` | A group is over 50 messages behind for 2 min | warning | |
| `SlowFills` | The slowest 5% take over 10 s to fill, for 5 min | warning | |
| `MarketDataStreamDown` | The Alpaca stream is not streaming for 5 min | warning | |

There is no Alertmanager: vmalert runs with `-notifier.blackhole`, and firing alerts are shown in the
dashboard (a banner in the sidebar on every view, and a table on the Monitoring view). To be notified
elsewhere, add an Alertmanager and point vmalert's `-notifier.url` at it.

## Logs

Fluent Bit reads Docker's `json-file` logs from `/var/lib/docker/containers` read-only, with no Docker
socket (which would be root on the host), and drops lines from other Compose projects.
[fluent-bit/docker_metadata.lua](fluent-bit/docker_metadata.lua) gives every line a `service` (the
Compose service), a `level` (error, warn, info, debug) and a message. It does this whatever the
source's format: ECS JSON from the Java services, JSON from the auth service (`LOG_FORMAT=json`), or
plain text from Kafka and Postgres.

On Docker Desktop, or with a different Docker root directory, set `DOCKER_CONTAINERS_DIR` in `.env`
to the host's containers directory.

The Monitoring view searches them by text, service and level, with saved searches for failed logins,
refresh token reuse, access denied and errors.

## Not included

- **Tracing** (OpenTelemetry with Tempo or Jaeger): 300–500 MB for the backend, plus a tracer in each
  JVM. The fill time histogram and the consumer lag show where an order is waiting. The executions
  table times each order's fill, so tracing can be added later if those are not enough.
- **Grafana**: the Streamlit dashboard is the UI, behind the admin sign-in it already has.
