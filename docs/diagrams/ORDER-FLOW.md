# Order Flow Sequence Diagrams

How an order moves through the platform, from the Angular order ticket to settlement. Orders
are asynchronous (docs/DECISIONS.md 2): the API accepts and queues an order, answers
`202 Accepted`, and the Trade Executor and the Broker Simulator fill it a moment later. The
classes named here are in [CLASS-DIAGRAM.md](CLASS-DIAGRAM.md). GitHub renders the diagrams below.

## 1. A BUY that fills

```mermaid
sequenceDiagram
    autonumber
    actor Trader
    participant UI as Angular order ticket
    participant API as OrderController
    participant OS as OrderService
    participant DB as PostgreSQL
    participant K as Kafka
    participant OL as OrderMessageListener<br/>(Trade Executor)
    participant B as Broker Simulator
    participant EL as ExecutionListener

    Trader->>UI: Buy 2 AAPL, limit $100
    UI->>API: POST /api/v1/orders (JWT, idempotencyKey)
    API->>API: AccountAccessGuard: the account is the caller's
    API->>OS: submitOrder(request)
    OS->>DB: account exists and ACTIVE, instrument exists and tradable?
    OS->>DB: insert order, status NEW
    OS->>K: OrderEvent to "orders" (waits for the ack)
    API-->>UI: 202 Accepted, orderId
    loop every 1.5 s until FILLED or REJECTED
        UI->>API: GET /api/v1/orders/{id}
    end

    K->>OL: OrderEvent
    OL->>OS: processOrderEvent(event)
    OS->>OS: OrderValidator: account ACTIVE, instrument tradable
    OS->>OS: checkAffordable: cash >= 2 x $100
    OS->>B: POST /api/v1/orders
    B-->>OS: 202 Accepted
    Note over B: simulated market delay (0.5 to 2 s)
    B->>K: ExecutionEvent to "executions" (fill price at or below the limit)

    K->>EL: ExecutionEvent
    EL->>OS: settleExecution(fill)
    rect rgba(127, 127, 127, 0.12)
        Note over OS,DB: one transaction, the order row locked
        OS->>DB: order still NEW?
        OS->>OS: BuyOrderStrategy: Account.debit(cost)
        OS->>OS: Position.apply(+2, fill price)
        OS->>DB: save account, position, execution, order FILLED, order history
    end
    OS->>K: TradeEvent FILLED to "trade-events"
    UI->>API: GET /api/v1/orders/{id}
    API-->>UI: FILLED
    UI-->>Trader: "Order filled: BUY 2 AAPL"
```

- **Idempotency (FR-06):** the client's `idempotencyKey` is stored with the order; reusing it
  answers `409 ORD-409`, so a retried request cannot place the order twice.
- **Audit (NFR-04):** every status change is published to `trade-events`, and
  `TradeEventAuditListener` stores it in `trade_event_log` (`GET /api/v1/orders/{id}/events`).
- **Fail fast:** an unknown account or instrument, an account that is not `ACTIVE` or an
  instrument that is not tradable is refused before anything is stored (`404 ACC-404`,
  `403 ACC-403`, `404 INS-404`). The Trade Executor checks again, in case either changes
  while the order waits. If Kafka cannot take the order, it is stored `REJECTED` and the API
  answers `503 SYS-503`.

## 2. A SELL, and an order that is rejected

A SELL takes the same path with `SellOrderStrategy`: `Account.credit(proceeds)` and
`Position.apply(-quantity, price)`, and the broker fills at or above the limit. What changes is
where a check can fail:

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka
    participant OL as OrderMessageListener
    participant OS as OrderService
    participant DB as PostgreSQL
    participant EH as Kafka error handler
    participant DLQ as DeadLetterService
    actor Admin

    K->>OL: OrderEvent (SELL 5 MSFT, 3 held)
    OL->>OS: processOrderEvent(event)
    OS->>OS: checkAffordable: held 3 < 5
    OS->>DB: order REJECTED, statusReason "Insufficient holdings"
    OS->>K: TradeEvent REJECTED to "trade-events"
    OS-->>OL: InsufficientHoldingsException
    OL-->>EH: the listener failed
    EH->>DLQ: captureFailedMessage(event, error, retryable?)
    DLQ->>DB: dlq_messages, status PENDING
    Note over DLQ: stays PENDING until an admin acts<br/>(auto-resolve is off, OWASP A09-4)
    Admin->>DLQ: POST /api/v1/dlq/messages/{id}/replay
    DLQ->>OS: replayOrderEvent(event)
```

- The UI's polling sees `REJECTED` and shows the reason: `Insufficient holdings (ORD-409)`, or
  `Insufficient funds (ORD-400)` for a BUY the account cannot afford.
- **Retryable or not:** a missing account or instrument is `NonRetryableOrderException`, so its
  DLQ entry is marked non-retryable; a broker outage (`BrokerUnavailableException`) can be
  replayed once the broker is back.
- **At settlement:** the funds or holdings are checked again inside the settlement transaction.
  If they changed while the broker worked (for example, the trader withdrew cash), the fill is
  recorded as `REJECTED` instead of overdrawing the account.

## 3. Cancelling an open order

```mermaid
sequenceDiagram
    autonumber
    actor Trader
    participant UI as Order history
    participant API as OrderController
    participant OS as OrderService
    participant DB as PostgreSQL
    participant EL as ExecutionListener

    Trader->>UI: Cancel (order is Open, status NEW)
    UI->>API: DELETE /api/v1/orders/{id}
    API->>OS: cancelOrder(id)
    OS->>DB: lock the order row: still NEW?
    alt still NEW
        OS->>DB: order CANCELLED, order history
        OS-->>API: CANCELLED
        API-->>UI: 200, status CANCELLED
    else already FILLED or CANCELLED
        OS-->>API: OrderCancellationConflictException
        API-->>UI: 409 ORD-409
        UI-->>Trader: "already been filled or cancelled"
    end

    Note over EL,DB: the broker's fill can still arrive later
    EL->>OS: settleExecution(fill)
    OS->>DB: lock the order row: not NEW, so the fill is ignored
```

Both `cancelOrder` and `settleExecution` lock the order row, so a cancel and a fill can never
both win: whichever takes the lock first decides, and the other sees the order is no longer
`NEW`. Cash is only moved at settlement, so a cancelled order never spends anything.
