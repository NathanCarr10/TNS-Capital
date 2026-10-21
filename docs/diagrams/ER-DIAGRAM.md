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
    ACCOUNTS ||--o{ STRATEGIES : "runs"
    INSTRUMENTS ||--o{ STRATEGIES : "traded by"
    STRATEGIES ||--o{ STRATEGY_SIGNALS : "fired"
    STRATEGY_SIGNALS |o--o| ORDERS : "placed"
    USERS ||--o{ SUPPORT_CONVERSATIONS : "opens"
    SUPPORT_CONVERSATIONS ||--o{ SUPPORT_MESSAGES : "contains"
    USERS ||--o{ SUPPORT_MESSAGES : "sends"
    SUPPORT_MESSAGES ||--o{ SUPPORT_NOTIFICATIONS : "notifies"

    ACCOUNTS {
        BIGINT id PK "generated identity"
        VARCHAR(32) account_number UK "business identifier, e.g. ACC-1001"
        VARCHAR(255) holder_name
        VARCHAR(320) email "the owner's email; seeded accounts get a placeholder"
        NUMERIC(18-2) cash_balance "CHECK >= 0"
        NUMERIC(18-2) reserved_cash "held for accepted BUY orders, <= cash_balance"
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
        BOOLEAN reserved "holds cash or shares on its account"
        TIMESTAMP created_on
    }

    POSITIONS {
        BIGINT account_id PK, FK
        VARCHAR(20) symbol PK, FK
        INT quantity "CHECK >= 0"
        INT reserved_quantity "held for accepted SELL orders, <= quantity"
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
        VARCHAR(320) email UK "sign-in identity and the token's sub; lower case"
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

    STRATEGIES {
        BIGINT id PK
        BIGINT account_id FK
        VARCHAR name
        VARCHAR symbol FK
        VARCHAR rule_type "MA_CROSSOVER, BOLLINGER"
        JSONB rule_params
        INT quantity
        VARCHAR status "ACTIVE, PAUSED, ARCHIVED"
        VARCHAR position "FLAT, LONG"
        TIMESTAMP created_on
        TIMESTAMP updated_on
        INT version
    }

    STRATEGY_SIGNALS {
        BIGINT id PK
        BIGINT strategy_id FK
        VARCHAR side "BUY, SELL"
        NUMERIC price
        TIMESTAMP bar_time
        VARCHAR source "LIVE, REPLAY"
        VARCHAR replay_id
        UUID order_id "no FK: the order lives in the trading API's tables"
        VARCHAR outcome "PLACED, FAILED"
        VARCHAR detail
        TIMESTAMP created_on
    }

    SUPPORT_CONVERSATIONS {
        UUID id PK
        VARCHAR(320) customer_email FK "users.email"
        VARCHAR(120) subject
        VARCHAR(20) status "OPEN | CLOSED"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    SUPPORT_MESSAGES {
        BIGINT id PK
        UUID conversation_id FK "ON DELETE CASCADE"
        VARCHAR(320) sender_email FK "users.email"
        VARCHAR(20) sender_role "CUSTOMER | SUPPORT"
        VARCHAR(4000) body
        TIMESTAMP created_at
    }

    SUPPORT_NOTIFICATIONS {
        BIGINT id PK
        VARCHAR(20) recipient_role "CUSTOMER | SUPPORT"
        VARCHAR(320) recipient_email FK "NULL for the support team"
        UUID conversation_id FK
        BIGINT message_id FK
        TIMESTAMP created_at
        TIMESTAMP read_at "NULL until read"
    }

    NEWSLETTER_ISSUES {
        BIGINT id PK
        DATE week_start UK "a Monday: one issue per week"
        TIMESTAMP generated_at
        JSONB gainers "top gainers, best first"
        JSONB losers "top losers, worst first"
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
  `etl_rejected_orders` and the daily prices in `fact_daily_prices`); see `etl/schema.sql`.
- **Strategy Lab.** The strategy service owns `strategies` and `strategy_signals`; a signal
  records the order it placed without a foreign key, as orders belong to the trading API.
- **Support chat.** `support_conversations`, `support_messages` and `support_notifications`
  hold traders' conversations with support, keyed by the users' emails. Deleting a
  conversation deletes its messages and notifications.
- **Newsletter.** `newsletter_issues` keeps one immutable issue per week, its movers stored as
  JSONB snapshots (docs/DECISIONS.md #17).
