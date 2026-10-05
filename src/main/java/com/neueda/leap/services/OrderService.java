package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.time.Clock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.OrderHistory;
import com.neueda.leap.model.Position;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.OrderEvent;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates order placement: validates, executes, and persists.
 */
@Service
@Transactional
@Slf4j
public class OrderService {
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;
    private final OrderHistoryRepository orderHistoryRepository;
    private final PositionRepository positionRepository;
    private final OrderValidator validator;
    private final Map<OrderSide, OrderExecutionStrategy> strategies;
    private final Clock clock;
    private final OrderEventPublisher orderEventPublisher;
    private final TradeEventPublisher tradeEventPublisher;
    private final TransactionTemplate transactionTemplate;

    public OrderService(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PositionRepository positionRepository,
            OrderValidator validator,
            Map<OrderSide, OrderExecutionStrategy> strategies,
            Clock clock,
            OrderEventPublisher orderEventPublisher,
            TradeEventPublisher tradeEventPublisher,
            PlatformTransactionManager transactionManager) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.validator = Objects.requireNonNull(validator);
        this.strategies = Objects.requireNonNull(strategies);
        this.clock = Objects.requireNonNull(clock);
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher);
        this.tradeEventPublisher = Objects.requireNonNull(tradeEventPublisher);
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @SuppressWarnings("null")
    public Order placeOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);

        validator.validate(request);
        String symbol = InputNormalizer.normalize(request.symbol());
        Order order = new Order(request.accountId(), symbol, request.side(), request.quantity(),
                request.price(), request.idempotencyKey(), clock);

        // Track previous status for lifecycle events
        OrderStatus previousStatus = null;
        String rejectionReason = null;

        try {
            // Retrieve account with null-safety
            Account account = accountRepository.findById(request.accountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.accountId()));

            // Retrieve strategy with null-safety
            OrderExecutionStrategy strategy = strategies.get(request.side());
            if (strategy == null) {
                throw new IllegalStateException("No strategy registered for order side: " + request.side());
            }

            strategy.execute(account, request, symbol);
            previousStatus = order.getStatus();
            order.setStatus(OrderStatus.FILLED);
        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            previousStatus = order.getStatus();
            order.setStatus(OrderStatus.REJECTED);
            rejectionReason = ex.getMessage();
            throw ex;
        } catch (Exception ex) {
            previousStatus = order.getStatus();
            order.setStatus(OrderStatus.REJECTED);
            rejectionReason = ex.getMessage();
            throw new IllegalStateException("Unexpected error during order execution", ex);
        } finally {
            orderRepository.save(order);

            // Publish lifecycle event to Kafka for all status changes
            tradeEventPublisher.publish(order, previousStatus, rejectionReason);
        }

        // Publish order event to Kafka (when order is placed)
        orderEventPublisher.publish(order);

        return order;
    }

    /**
     * Processes an order event from Kafka asynchronously.
     * 
     * Called by OrderMessageListener when an order event is received from the Kafka
     * topic. Validates and executes the order, creating a new Order entity if it
     * doesn't already exist (based on order ID).
     * 
     * TRANSACTION BOUNDARIES: Execution runs in its own transaction. If it fails,
     * that transaction is rolled back (undoing any balance/position changes) and
     * the order is then persisted as REJECTED in a second, independent
     * transaction, so the rejection survives the exception that is rethrown to
     * the Kafka error handler.
     * 
     * TRADE EVENTS: Exactly one lifecycle event is published per order, and only
     * after the corresponding status has been committed (FILLED or REJECTED).
     * 
     * DEFENSIVE VALIDATION: Even though REST layer validates Account/Instrument
     * existence, this method also validates defensively to catch race conditions
     * (e.g., account deleted between REST validation and async processing).
     * Missing resources are wrapped in NonRetryableOrderException so the DLQ
     * records them as not replayable.
     * 
     * NO RETRY LOGIC: All exceptions are terminal - the order is persisted with
     * REJECTED status and the exception is rethrown so the message is routed to
     * the DLQ.
     * 
     * @param event the order event to process
     * @return the processed order
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order processOrderEvent(OrderEvent event) {
        return processOrderEvent(event, false);
    }

    /**
     * Re-processes an order event that was routed to the DLQ.
     * 
     * Unlike {@link #processOrderEvent(OrderEvent)}, an existing REJECTED order is
     * not treated as already processed: it is executed again and replaced by the
     * outcome of this attempt. If the replay fails again, the original REJECTED
     * order is kept and no further trade event is published.
     * 
     * @param event the order event to replay
     * @return the processed order
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Order replayOrderEvent(OrderEvent event) {
        return processOrderEvent(event, true);
    }

    private Order processOrderEvent(OrderEvent event, boolean replay) {
        Objects.requireNonNull(event, "Order event cannot be null");

        // Idempotency check: a redelivered message must not execute the order twice
        Optional<Order> existingOrder = orderRepository.findById(event.orderId());
        boolean replayingRejected = replay && existingOrder.isPresent()
                && existingOrder.get().getStatus() == OrderStatus.REJECTED;
        if (existingOrder.isPresent() && !replayingRejected) {
            log.info("Order already exists, skipping re-processing: orderId={}, status={}",
                    event.orderId(), existingOrder.get().getStatus());
            return existingOrder.get();
        }

        Order order;
        try {
            order = transactionTemplate.execute(status -> {
                if (replayingRejected) {
                    // Remove the previous REJECTED attempt; restored if this attempt rolls back
                    orderRepository.delete(existingOrder.get());
                    orderRepository.flush();
                }
                return executeOrderEvent(event);
            });
        } catch (AccountNotFoundException | InstrumentNotFoundException ex) {
            log.warn("Order rejected due to missing resource (non-retryable): orderId={}, error={}, type={}",
                    event.orderId(), ex.getMessage(), ex.getClass().getSimpleName());
            recordRejectedOrder(event, ex.getMessage());
            throw new NonRetryableOrderException(ex.getMessage(), ex);
        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            log.warn("Order rejected due to insufficient resources: orderId={}, error={}", event.orderId(),
                    ex.getMessage());
            recordRejectedOrder(event, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected error processing order event: orderId={}, error={}", event.orderId(),
                    ex.getMessage(), ex);
            recordRejectedOrder(event, ex.getMessage());
            throw ex;
        }

        log.info("Order FILLED: orderId={}, accountId={}", event.orderId(), event.accountId());
        tradeEventPublisher.publish(order, OrderStatus.NEW, null);
        return order;
    }

    /**
     * Validates and executes the order. Must run inside a transaction so that
     * account and position changes are rolled back if execution fails.
     */
    private Order executeOrderEvent(OrderEvent event) {
        String symbol = InputNormalizer.normalize(event.symbol());

        PlaceOrderRequest request = new PlaceOrderRequest(
                event.accountId(),
                event.symbol(),
                event.side(),
                event.quantity(),
                event.price(),
                event.orderId().toString());

        validator.validate(request);

        // DEFENSIVE VALIDATION: account or instrument may have been deleted between
        // REST validation and async processing
        Account account = accountRepository.findById(event.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + event.accountId()));
        instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));

        OrderExecutionStrategy strategy = strategies.get(event.side());
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for order side: " + event.side());
        }

        Order order = newOrderFromEvent(event);
        strategy.execute(account, request, symbol);
        order.setStatus(OrderStatus.FILLED);
        orderRepository.save(order);
        return order;
    }

    /**
     * Persists the order as REJECTED in its own transaction and publishes the
     * REJECTED trade event once it is committed.
     * 
     * Does nothing if the order is already stored (e.g. a failed DLQ replay of an
     * order that was already rejected), so each order produces at most one
     * REJECTED event. Failures here are logged and swallowed so the original
     * exception still reaches the Kafka error handler.
     */
    private void recordRejectedOrder(OrderEvent event, String reason) {
        try {
            Order rejectedOrder = transactionTemplate.execute(status -> {
                if (orderRepository.existsById(event.orderId())) {
                    return null;
                }
                Order order = newOrderFromEvent(event);
                order.setStatus(OrderStatus.REJECTED);
                orderRepository.save(order);
                return order;
            });

            if (rejectedOrder == null) {
                log.info("Order already recorded, not saving REJECTED again: orderId={}", event.orderId());
                return;
            }

            log.info("Saved rejected order: orderId={}, reason={}", event.orderId(), reason);
            tradeEventPublisher.publish(rejectedOrder, OrderStatus.NEW, reason);
        } catch (RuntimeException ex) {
            log.error("Failed to record REJECTED order: orderId={}, error={}", event.orderId(), ex.getMessage(), ex);
        }
    }

    private Order newOrderFromEvent(OrderEvent event) {
        Order order = new Order(
                event.accountId(),
                event.symbol(),
                event.side(),
                event.quantity(),
                event.price(),
                event.orderId().toString(),
                clock);
        order.setId(event.orderId());
        return order;
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(key);
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        return positionRepository.findByAccountIdAndSymbol(accountId, InputNormalizer.normalize(symbol));
    }

    /**
     * Retrieves all orders for a given account.
     *
     * @param accountId the account ID
     * @return list of orders for the account (empty list if no orders found)
     * @throws IllegalArgumentException if accountId is null
     */
    public List<Order> getOrdersByAccountId(Long accountId) {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        return orderRepository.findByAccountId(accountId);
    }

    /**
     * Cancels an order if its status is NEW.
     * 
     * Cancellation is only allowed for orders in NEW state.
     * Attempting to cancel FILLED, REJECTED, or CANCELLED orders throws a conflict
     * exception.
     * When an order is cancelled, it is archived to OrderHistory for audit trail
     * and a lifecycle event is published to Kafka.
     *
     * @param orderId the order ID to cancel
     * @return the cancelled order
     * @throws OrderNotFoundException             if order is not found
     * @throws OrderCancellationConflictException if order status is not NEW
     */
    public Order cancelOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "Order ID cannot be null");

        // Retrieve order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        // Validate cancellation is allowed (only NEW orders can be cancelled)
        if (order.getStatus() != OrderStatus.NEW) {
            throw new OrderCancellationConflictException(
                    String.format("Cannot cancel order in %s status. Only NEW orders can be cancelled.",
                            order.getStatus()));
        }

        // Track status transition for event
        OrderStatus previousStatus = order.getStatus();

        // Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Archive cancelled order to history for audit trail
        OrderHistory history = new OrderHistory(order, clock);
        orderHistoryRepository.save(history);

        // Publish lifecycle event to Kafka
        tradeEventPublisher.publish(order, previousStatus, "User requested cancellation");

        return order;
    }
}
