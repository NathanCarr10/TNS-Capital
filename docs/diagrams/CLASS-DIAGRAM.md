# Trading Domain Class Diagram

The core classes of the trading API (`src/main/java/com/neueda/leap`): the domain entities and
their behaviour, the order lifecycle enums, the services that run it, and the buy/sell
strategies. Getters, setters and Spring wiring are left out. GitHub renders the diagrams below.

The database tables behind the entities are in [ER-DIAGRAM.md](ER-DIAGRAM.md); how an order moves
through these classes is in [ORDER-FLOW.md](ORDER-FLOW.md).

## Domain model

```mermaid
classDiagram
    direction LR

    class Account {
        -Long id
        -String accountNumber
        -String holderName
        -BigDecimal cashBalance
        -AccountStatus status
        -Integer version
        -Instant openedOn
        -Instant lastUpdated
        +debit(BigDecimal amount)
        +credit(BigDecimal amount)
        +isActive() boolean
    }

    class Instrument {
        -String symbol
        -String name
        -String assetClass
        -String currency
        -boolean tradable
        +isTradable() boolean
    }

    class Order {
        -UUID id
        -Long accountId
        -String symbol
        -OrderSide side
        -Integer quantity
        -BigDecimal price
        -OrderStatus status
        -String idempotencyKey
        -Instant createdOn
        -String statusReason
    }

    class Position {
        -Long accountId
        -String symbol
        -Integer quantity
        -BigDecimal averageCost
        +apply(Integer quantity, BigDecimal price)
        +marketValue(BigDecimal currentPrice) BigDecimal
    }

    class Execution {
        -UUID id
        -UUID orderId
        -Integer quantity
        -BigDecimal price
        -String venue
        -Instant executedOn
    }

    class OrderHistory {
        archived copy of a finished order
    }

    class AccountStatus {
        <<enumeration>>
        ACTIVE
        SUSPENDED
        CLOSED
    }

    class OrderSide {
        <<enumeration>>
        BUY
        SELL
    }

    class OrderStatus {
        <<enumeration>>
        NEW
        FILLED
        REJECTED
        CANCELLED
    }

    Account "1" --> "0..*" Order : places
    Account "1" --> "0..*" Position : holds
    Instrument "1" --> "0..*" Order : traded in
    Instrument "1" --> "0..*" Position : held as
    Order "1" --> "0..1" Execution : filled by
    Order "1" --> "0..1" OrderHistory : archived as
    Account --> AccountStatus
    Order --> OrderSide
    Order --> OrderStatus
```

- `Account.debit` throws `InsufficientFundsException` rather than letting the balance go below
  zero; `version` is the optimistic lock (NFR-02).
- `Position.apply` adds a fill to the holding and recomputes `averageCost`; a negative quantity
  is a sale.
- An `Order` is `NEW` from the moment the API accepts it until the broker's fill settles it
  (`FILLED`), a check fails (`REJECTED`) or the trader cancels it (`CANCELLED`).

## Services and strategies

```mermaid
classDiagram
    direction TB

    class OrderController {
        +placeOrder(PlaceOrderRequest) 202 PlaceOrderResponse
        +cancelOrder(UUID) OrderResponse
        +getOrder(UUID) OrderResponse
    }

    class OrderService {
        +submitOrder(PlaceOrderRequest) Order
        +processOrderEvent(OrderEvent) Order
        +replayOrderEvent(OrderEvent) Order
        +settleExecution(ExecutionEvent) Order
        +cancelOrder(UUID) Order
    }

    class OrderValidator {
        account exists and is ACTIVE
        instrument exists and is tradable
        quantity and price above 0
    }

    class OrderExecutionStrategy {
        <<interface>>
        +execute(Account, PlaceOrderRequest, String symbol)
    }

    class BuyOrderStrategy {
        debits cash, adds to the position
    }

    class SellOrderStrategy {
        credits cash, reduces the position
    }

    class OrderEventPublisher {
        +publish(Order) to orders
    }

    class TradeEventPublisher {
        +publish(Order, previousStatus, reason) to trade-events
    }

    class BrokerSimulatorClient {
        +submit(Order)
    }

    class PriceProvider {
        <<interface>>
        +latestPrice(String symbol)
    }

    class LivePriceService {
        Alpaca stream and REST
    }

    class AccountService {
        +getAccountById(id) Account
        +deposit(id, amount) Account
        +withdraw(id, amount) Account
        +updateStatus(id, status) Account
        +closeAccount(id) Account
    }

    class DeadLetterService {
        +captureFailedMessage(...) DeadLetterMessage
        +replayMessage(id, OrderService) boolean
        +dismissMessage(id, adminNotes)
    }

    OrderController --> OrderService
    OrderService --> OrderValidator
    OrderService --> OrderExecutionStrategy : by OrderSide
    OrderExecutionStrategy <|.. BuyOrderStrategy
    OrderExecutionStrategy <|.. SellOrderStrategy
    OrderService --> OrderEventPublisher
    OrderService --> TradeEventPublisher
    OrderService --> BrokerSimulatorClient
    OrderService --> PriceProvider : orders sent without a price
    PriceProvider <|.. LivePriceService
    DeadLetterService --> OrderService : replay
```

- **Strategy pattern (SOLID: open/closed).** `OrderService` picks the strategy for the order's
  side from a `Map<OrderSide, OrderExecutionStrategy>`; adding a side means adding a strategy,
  not editing the service.
- **Dependency inversion.** Pricing is behind `PriceProvider`, so the order flow and its tests do
  not depend on Alpaca.
- **Single responsibility.** Validation (`OrderValidator`), execution rules (the strategies),
  messaging (the publishers) and the broker call (`BrokerSimulatorClient`) are separate classes.

## Exceptions

Every custom exception extends the abstract `TradingException`, which carries the spec error
code (§21). `GlobalExceptionHandler` returns that code with an HTTP status and a fixed, safe
message. The four categories under it say what kind of failure it is, and
`OrderProcessingException` wraps failures inside the Kafka consumers.

```mermaid
classDiagram
    direction TB
    class RuntimeException
    class TradingException {
        <<abstract>>
        -String errorCode
        +getErrorCode() String
    }
    class ResourceNotFoundException { <<abstract>> }
    class BusinessRuleException { <<abstract>> }
    class ConflictException { <<abstract>> }
    class ServiceUnavailableException { <<abstract>> }
    class OrderProcessingException {
        <<abstract>>
        keeps the error code of its cause
    }
    RuntimeException <|-- TradingException
    TradingException <|-- ResourceNotFoundException
    TradingException <|-- BusinessRuleException
    TradingException <|-- ConflictException
    TradingException <|-- ServiceUnavailableException
    TradingException <|-- OrderProcessingException
    ResourceNotFoundException <|-- AccountNotFoundException
    ResourceNotFoundException <|-- InstrumentNotFoundException
    ResourceNotFoundException <|-- OrderNotFoundException
    ResourceNotFoundException <|-- PositionNotFoundException
    BusinessRuleException <|-- AccountNotActiveException
    BusinessRuleException <|-- InsufficientFundsException
    BusinessRuleException <|-- InsufficientHoldingsException
    ConflictException <|-- AccountAlreadyExistsException
    ConflictException <|-- AccountDeletionConflictException
    ConflictException <|-- DuplicateInstrumentException
    ConflictException <|-- DuplicateOrderException
    ConflictException <|-- OrderCancellationConflictException
    ServiceUnavailableException <|-- PriceUnavailableException
    ServiceUnavailableException <|-- MarketDataUnavailableException
    ServiceUnavailableException <|-- OrderPublishException
    ServiceUnavailableException <|-- BrokerUnavailableException
    OrderProcessingException <|-- NonRetryableOrderException
    OrderProcessingException <|-- SettlementException
```

| Error code | HTTP | Exceptions |
|---|---|---|
| `ACC-404` | 404 | `AccountNotFoundException` |
| `ACC-403` | 403 | `AccountNotActiveException` |
| `ACC-409` | 409 | `AccountAlreadyExistsException`, `AccountDeletionConflictException` |
| `INS-404` | 404 | `InstrumentNotFoundException` (unknown or not tradable) |
| `INS-409` | 409 | `DuplicateInstrumentException` |
| `ORD-400` | 400 | `InsufficientFundsException` |
| `ORD-409` | 409 | `InsufficientHoldingsException`, `DuplicateOrderException`, `OrderCancellationConflictException` |
| `ORD-404` | 404 | `OrderNotFoundException` |
| `POS-404` | 404 | `PositionNotFoundException` |
| `PRC-503`, `MKT-503`, `SYS-503` | 503 | `PriceUnavailableException`, `MarketDataUnavailableException`, `OrderPublishException` |
| `SYS-409` | 409 | Spring's `OptimisticLockingFailureException`: two requests changed the same account at once, and its `version` column (NFR-02) refused the later one |
| `BRK-503` (internal) | | `BrokerUnavailableException`: the order is stored as REJECTED and can be replayed from the DLQ |
| cause's code (internal) | | `NonRetryableOrderException`, `SettlementException`: raised in the Kafka consumers, where they decide whether an order goes to the DLQ |
