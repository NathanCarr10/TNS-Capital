# Trade Database ER Diagram

The operational schema in PostgreSQL, as created by `db/tables` (loaded by the
`db/` image). GitHub renders the diagram below.

```mermaid
erDiagram
    ACCOUNTS ||--o{ ORDERS : places
    ACCOUNTS ||--o{ POSITIONS : holds
    INSTRUMENTS ||--o{ ORDERS : "traded in"
    INSTRUMENTS ||--o{ POSITIONS : "held as"
    ORDERS ||--o| EXECUTIONS : "filled by"
    ACCOUNTS |o--o| USERS : "owned by"
    USERS ||--o{ REFRESH_TOKENS : "signed in with"

    ACCOUNTS {
        BIGINT id PK "generated identity"
        VARCHAR(32) account_number UK "business identifier, e.g. ACC-1001"
        VARCHAR(255) holder_name
        NUMERIC(18-2) cash_balance "CHECK >= 0"
        VARCHAR(20) status "ACTIVE | SUSPENDED | CLOSED"
        INT version "optimistic lock"
        TIMESTAMP opened_on "set once, when the account is opened"
        TIMESTAMP last_updated
    }

    INSTRUMENTS {
        VARCHAR(20) symbol PK
        VARCHAR(255) name
        VARCHAR(20) asset_class
        CHAR(3) currency
        BOOLEAN tradable
    }

    ORDERS {
        UUID id PK
        BIGINT account_id FK
        VARCHAR(20) symbol FK
        VARCHAR(4) side "BUY | SELL"
        INT quantity "CHECK > 0"
        NUMERIC(18-2) price "CHECK > 0"
        VARCHAR(20) status "NEW | FILLED | REJECTED | CANCELLED"
        VARCHAR(100) idempotency_key UK
        VARCHAR(255) status_reason "reason text (seed data)"
        TIMESTAMP created_on
    }

    POSITIONS {
        BIGINT account_id PK, FK
        VARCHAR(20) symbol PK, FK
        INT quantity "CHECK >= 0"
        NUMERIC(18-2) average_cost "CHECK >= 0"
    }

    EXECUTIONS {
        UUID id PK
        UUID order_id FK, UK "one fill per order"
        INT quantity
        NUMERIC(18-2) price
        VARCHAR(20) venue
        TIMESTAMP executed_on
    }

    ORDER_HISTORY {
        BIGINT id PK
        UUID order_id "no FK: survives the order"
        BIGINT account_id "no FK: survives the account"
        VARCHAR(20) symbol
        VARCHAR(4) side
        INT quantity
        NUMERIC(18-2) price
        VARCHAR(20) status
        VARCHAR(100) idempotency_key UK
        TIMESTAMP order_created_on
        TIMESTAMP deleted_on "when it was archived"
    }

    DLQ_MESSAGES {
        UUID id PK
        UUID original_order_id "null if the message was unreadable"
        TEXT original_message "raw Kafka message, for replay"
        TEXT failure_reason
        VARCHAR(100) failure_type
        VARCHAR(20) status "PENDING | RESOLVED | IGNORED"
        INT retry_count "admin replays"
        TIMESTAMP created_on
        TIMESTAMP last_retry_on
        TIMESTAMP resolved_on
        TEXT admin_notes
        BOOLEAN is_retryable "false = cannot succeed on replay"
    }

    TRADE_EVENT_LOG {
        VARCHAR(64) event_id PK "the event's correlationId"
        UUID order_id "no FK: survives the order"
        BIGINT account_id "no FK"
        VARCHAR(20) symbol
        VARCHAR(4) side
        INT quantity
        NUMERIC(18-2) price
        VARCHAR(20) previous_status
        VARCHAR(20) new_status
        TEXT reason
        TIMESTAMP received_on
    }

    USERS {
        BIGINT id PK
        VARCHAR(255) username UK
        VARCHAR(255) password_hash "Argon2id; plaintext never stored"
        VARCHAR(20) role "USER | SUPPORT | ADMIN"
        BIGINT account_id FK, UK "opened at registration; NULL for support and admins"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    REFRESH_TOKENS {
        BIGINT id PK
        BIGINT user_id FK "ON DELETE CASCADE"
        CHAR(64) token_hash UK "SHA-256 of the token"
        UUID family_id "one login session"
        TIMESTAMP expires_at
        TIMESTAMP used_at "NULL until spent"
        TIMESTAMP created_at
    }
```

## Notes

- **Normalisation.** Each table describes one thing (3NF): instrument details
  live only in `instruments`, account details only in `accounts`, and
  `orders`/`positions` refer to them by key. `positions` uses the natural
  composite key `(account_id, symbol)`.
- **Audit trail.** Every order is kept in `orders`, including `REJECTED` ones;
  the reason a live order failed is recorded in `dlq_messages`. Accounts are closed (`status = CLOSED`), not deleted,
  so their orders stay valid. `order_history` keeps an archived copy of each
  cancelled order without foreign keys.
- **Historical data.** `executions` records the fill for each filled order;
  `orders.created_on` and `executions.executed_on` are indexed for date-range
  queries.
- **Failed messages.** `dlq_messages` is independent of the trading tables: it
  stores Kafka order messages that could not be processed, for an admin to
  replay or dismiss.
- **Lifecycle audit.** `trade_event_log` holds every event from the `trade-events`
  Kafka topic (each status change of each order), written by the API's audit consumer;
  `GET /api/v1/orders/{id}/events` reads it.
- **Users and sessions.** The auth service owns `users` and `refresh_tokens`.
  Registering creates a user and opens their trading account (`users.account_id`).
  Each refresh token is single use and stored only as a hash; tokens from one
  login share a `family_id`, so reusing a spent token revokes the whole session.
- **Reporting.** The views in `db/views` (`v_positions_detail`,
  `v_order_history`, `v_account_summary`) join these tables for dashboards. The
  ETL pipeline loads a star schema into the separate `analytics` schema
  (`dim_account`, `dim_instrument`, `dim_date`, `fact_trades`, plus
  `etl_rejected_orders`); see `etl/schema.sql`.
