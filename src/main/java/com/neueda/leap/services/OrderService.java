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
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.OrderEvent;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
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
            TradeEventPublisher tradeEventPublisher) {
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
     * topic.
     * Validates and executes the order, creating a new Order entity if it doesn't
     * already exist (based on order ID).
     * 
     * DEFENSIVE VALIDATION: Even though REST layer validates Account/Instrument
     * existence, this method also validates defensively to catch race conditions
     * (e.g., account deleted between REST validation and async processing).
     * 
     * EXCEPTION DIFFERENTIATION:
     * - Not-found exceptions (Account/Instrument missing): Wrapped in
     * NonRetryableOrderException. Error handler will skip retries and route to DLQ.
     * - Business logic exceptions (Insufficient funds/holdings, Account not
     * active):
     * Re-thrown directly. Error handler will retry 3 times before DLQ.
     * 
     * @param event the order event to process
     * @return the processed order
     * @throws NonRetryableOrderException if account/instrument not found
     *                                    (non-retryable)
     * @throws InsufficientFundsException if order fails business logic validation
     *                                    (retryable)
     * @throws Exception                  for other unexpected errors
     */
    @Transactional
    public Order processOrderEvent(OrderEvent event) {
        Objects.requireNonNull(event, "Order event cannot be null");

        String symbol = InputNormalizer.normalize(event.symbol());

        try {
            // Check for existing order by ID (idempotency check)
            Optional<Order> existingOrder = orderRepository.findById(event.orderId());
            if (existingOrder.isPresent()) {
                log.info("Order already exists, skipping re-processing: orderId={}", event.orderId());
                return existingOrder.get();
            }

            // Create PlaceOrderRequest from event
            PlaceOrderRequest request = new PlaceOrderRequest(
                    event.accountId(),
                    event.symbol(),
                    event.side(),
                    event.quantity(),
                    event.price(),
                    event.orderId().toString());

            // Validate the order request
            validator.validate(request);

            // DEFENSIVE VALIDATION: Retrieve account (may throw AccountNotFoundException)
            // This catches race conditions where account was deleted between REST
            // validation
            // and async processing
            Account account = accountRepository.findById(event.accountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + event.accountId()));

            // DEFENSIVE VALIDATION: Validate instrument exists
            // This catches race conditions where instrument was deleted
            instrumentRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));

            // Create order entity
            Order order = new Order(
                    event.accountId(),
                    symbol,
                    event.side(),
                    event.quantity(),
                    event.price(),
                    event.orderId().toString(),
                    clock);
            order.setId(event.orderId());

            // Retrieve and execute strategy
            OrderExecutionStrategy strategy = strategies.get(event.side());
            if (strategy == null) {
                throw new IllegalStateException("No strategy registered for order side: " + event.side());
            }

            strategy.execute(account, request, symbol);
            OrderStatus previousStatus = order.getStatus();
            order.setStatus(OrderStatus.FILLED);

            // Save the successfully processed order
            orderRepository.save(order);

            // Publish trade event if order was filled
            if (order.getStatus() == OrderStatus.FILLED) {
                tradeEventPublisher.publish(order, previousStatus, null);
            }

            return order;

        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            log.warn("Order rejected due to insufficient resources: orderId={}, error={}", event.orderId(),
                    ex.getMessage());

            // Save rejected order in separate transaction before exception causes rollback
            saveRejectedOrder(event, symbol, ex.getMessage(), false); // false = retryable (business logic error)

            // Re-throw to allow Kafka error handler to retry
            throw ex;

        } catch (AccountNotFoundException | InstrumentNotFoundException ex) {
            log.warn("Order rejected due to missing resource (non-retryable): orderId={}, error={}, type={}",
                    event.orderId(), ex.getMessage(), ex.getClass().getSimpleName());

            // Save rejected order in separate transaction before exception causes rollback
            saveRejectedOrder(event, symbol, ex.getMessage(), true); // true = non-retryable (not-found error)

            // Wrap in NonRetryableOrderException to signal error handler to skip retries
            throw new NonRetryableOrderException(
                    "Order rejected due to missing resource: " + ex.getMessage(),
                    ex);

        } catch (Exception ex) {
            log.error("Unexpected error processing order event: orderId={}, error={}", event.orderId(), ex.getMessage(),
                    ex);

            // Save rejected order in separate transaction before exception causes rollback
            saveRejectedOrder(event, symbol, ex.getMessage(), true); // true = non-retryable (unexpected error)

            // Wrap in NonRetryableOrderException for non-business-logic errors
            throw new NonRetryableOrderException(
                    "Unexpected error during order event processing: " + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Saves a REJECTED order in a separate transaction and publishes a trade
     * lifecycle event.
     * 
     * Uses Propagation.REQUIRES_NEW to ensure the order is persisted even if
     * the parent transaction rolls back due to an exception.
     * 
     * DEFENSIVE HANDLING: If order already exists (duplicate idempotencyKey),
     * updates
     * the status to REJECTED instead of failing.
     * 
     * TRADE EVENT PUBLISHING: After successful save, publishes a trade event to
     * notify downstream systems (Settlement, Risk Dashboard) of the rejection.
     * 
     * @param event           the order event
     * @param symbol          the normalized order symbol
     * @param rejectionReason the reason the order was rejected (exception message)
     * @param isNonRetryable  true if this is a non-retryable error (not-found),
     *                        false if retryable
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveRejectedOrder(OrderEvent event, String symbol, String rejectionReason, boolean isNonRetryable) {
        log.info("Attempting to save REJECTED order: orderId={}, accountId={}, reason={}, isNonRetryable={}",
                event.orderId(), event.accountId(), rejectionReason, isNonRetryable);

        Order savedOrder = null;
        boolean saveSucceeded = false;

        try {
            // Create new order with REJECTED status
            Order order = new Order(
                    event.accountId(),
                    symbol,
                    event.side(),
                    event.quantity(),
                    event.price(),
                    event.orderId().toString(),
                    clock);
            order.setId(event.orderId());
            order.setStatus(OrderStatus.REJECTED);

            log.debug("Created Order entity: orderId={}, status={}", order.getId(), order.getStatus());

            // Attempt to save the order
            savedOrder = orderRepository.save(order);
            saveSucceeded = true;
            log.info("Successfully inserted REJECTED order into database: orderId={}", event.orderId());

        } catch (DataIntegrityViolationException e) {
            // Order already exists (duplicate idempotencyKey): update status instead
            log.warn(
                    "Order already exists (duplicate key), attempting to update status to REJECTED: orderId={}, error={}",
                    event.orderId(), e.getMessage());

            try {
                savedOrder = orderRepository.findById(event.orderId()).orElse(null);
                if (savedOrder != null) {
                    savedOrder.setStatus(OrderStatus.REJECTED);
                    savedOrder = orderRepository.save(savedOrder);
                    saveSucceeded = true;
                    log.info("Successfully updated existing order status to REJECTED: orderId={}", event.orderId());
                } else {
                    log.error("Order not found after duplicate key error: orderId={}", event.orderId());
                }
            } catch (Exception updateEx) {
                log.error("Failed to update order status after duplicate key error: orderId={}, error={}",
                        event.orderId(), updateEx.getMessage(), updateEx);
            }
        } catch (Exception saveEx) {
            log.error("Failed to save rejected order (unexpected error): orderId={}, accountId={}, error={}",
                    event.orderId(), event.accountId(), saveEx.getMessage(), saveEx);
            throw new IllegalStateException("Failed to save rejected order: " + saveEx.getMessage(), saveEx);
        }

        // Only publish trade event if save succeeded
        if (saveSucceeded && savedOrder != null) {
            try {
                tradeEventPublisher.publish(savedOrder, OrderStatus.NEW, rejectionReason);
                log.info("Published REJECTED trade event: orderId={}, reason={}", event.orderId(), rejectionReason);
            } catch (Exception publishEx) {
                log.error("Failed to publish trade event for rejected order: orderId={}, error={}",
                        event.orderId(), publishEx.getMessage(), publishEx);
                // Don't rethrow - order is already saved, event publishing failure is not fatal
            }
        } else {
            log.error("Skipped trade event publishing - order save did not succeed: orderId={}", event.orderId());
        }
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
