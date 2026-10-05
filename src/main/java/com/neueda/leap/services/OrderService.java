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
     * CRITICAL GUARANTEE: The order is ALWAYS persisted to the database:
     * - Created as NEW immediately after validation
     * - Updated to FILLED if strategy execution succeeds
     * - Updated to REJECTED if strategy execution fails or validation fails
     * 
     * This ensures orders are never lost, even if exceptions occur.
     * 
     * DEFENSIVE VALIDATION: Even though REST layer validates Account/Instrument
     * existence, this method also validates defensively to catch race conditions
     * (e.g., account deleted between REST validation and async processing).
     * 
     * NO RETRY LOGIC: All exceptions are terminal - the order is persisted with
     * REJECTED status and the message is routed to DLQ. No retries are attempted.
     * 
     * @param event the order event to process
     * @return the processed order
     * @throws Exception for any errors (no retries will be attempted)
     */
    @Transactional
    public Order processOrderEvent(OrderEvent event) {
        Objects.requireNonNull(event, "Order event cannot be null");

        String symbol = InputNormalizer.normalize(event.symbol());
        Order order = null;

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

            // Create order entity as NEW (constructor automatically sets status = NEW)
            order = new Order(
                    event.accountId(),
                    symbol,
                    event.side(),
                    event.quantity(),
                    event.price(),
                    event.orderId().toString(),
                    clock);
            order.setId(event.orderId());

            // CRITICAL: Save order as NEW in separate transaction
            // This ensures it's persisted IMMEDIATELY and survives even if strategy
            // execution fails
            saveOrderInSeparateTransaction(order);
            log.info("Saved order as NEW: orderId={}, accountId={}", event.orderId(), event.accountId());

            // Retrieve and execute strategy
            OrderExecutionStrategy strategy = strategies.get(event.side());
            if (strategy == null) {
                throw new IllegalStateException("No strategy registered for order side: " + event.side());
            }

            strategy.execute(account, request, symbol);
            OrderStatus previousStatus = order.getStatus();
            order.setStatus(OrderStatus.FILLED);

            // Update order to FILLED status
            orderRepository.save(order);
            log.info("Updated order status to FILLED: orderId={}", event.orderId());

            // Publish trade event if order was filled
            if (order.getStatus() == OrderStatus.FILLED) {
                tradeEventPublisher.publish(order, previousStatus, null);
            }

            return order;

        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            log.warn("Order rejected due to insufficient resources: orderId={}, error={}", event.orderId(),
                    ex.getMessage());

            // Update order status to REJECTED and publish trade event
            if (order != null) {
                updateOrderToRejectedAndPublishEvent(order, OrderStatus.NEW, ex.getMessage());
            } else {
                // Order creation failed - create and save REJECTED order
                Order rejectedOrder = new Order(
                        event.accountId(),
                        symbol,
                        event.side(),
                        event.quantity(),
                        event.price(),
                        event.orderId().toString(),
                        clock);
                rejectedOrder.setId(event.orderId());
                rejectedOrder.setStatus(OrderStatus.REJECTED);
                saveOrderInSeparateTransaction(rejectedOrder);
                log.info("Saved rejected order: orderId={}, reason={}", event.orderId(), ex.getMessage());
                tradeEventPublisher.publish(rejectedOrder, OrderStatus.NEW, ex.getMessage());
            }

            // Rethrow exception to trigger error handler (which will route to DLQ without
            // retries)
            throw ex;

        } catch (AccountNotFoundException | InstrumentNotFoundException ex) {
            log.warn("Order rejected due to missing resource (non-retryable): orderId={}, error={}, type={}",
                    event.orderId(), ex.getMessage(), ex.getClass().getSimpleName());

            // Create and save REJECTED order (couldn't create earlier due to validation
            // failure)
            Order rejectedOrder = new Order(
                    event.accountId(),
                    symbol,
                    event.side(),
                    event.quantity(),
                    event.price(),
                    event.orderId().toString(),
                    clock);
            rejectedOrder.setId(event.orderId());
            rejectedOrder.setStatus(OrderStatus.REJECTED);
            saveOrderInSeparateTransaction(rejectedOrder);
            log.info("Saved rejected order due to missing resource: orderId={}, reason={}", event.orderId(),
                    ex.getMessage());
            tradeEventPublisher.publish(rejectedOrder, OrderStatus.NEW, ex.getMessage());

            // Rethrow exception to trigger error handler (which will route to DLQ without
            // retries)
            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected error processing order event: orderId={}, error={}", event.orderId(), ex.getMessage(),
                    ex);

            // Update or create rejected order
            if (order != null) {
                updateOrderToRejectedAndPublishEvent(order, OrderStatus.NEW, ex.getMessage());
            } else {
                Order rejectedOrder = new Order(
                        event.accountId(),
                        symbol,
                        event.side(),
                        event.quantity(),
                        event.price(),
                        event.orderId().toString(),
                        clock);
                rejectedOrder.setId(event.orderId());
                rejectedOrder.setStatus(OrderStatus.REJECTED);
                saveOrderInSeparateTransaction(rejectedOrder);
                log.info("Saved rejected order due to unexpected error: orderId={}, reason={}", event.orderId(),
                        ex.getMessage());
                tradeEventPublisher.publish(rejectedOrder, OrderStatus.NEW, ex.getMessage());
            }

            // Rethrow exception to trigger error handler (which will route to DLQ without
            // retries)
            throw ex;
        }
    }

    /**
     * Saves an order in a separate transaction (REQUIRES_NEW).
     * 
     * This ensures the order is persisted immediately and survives even if
     * the parent transaction rolls back due to an exception.
     * 
     * @param order the order to save
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveOrderInSeparateTransaction(Order order) {
        orderRepository.save(order);
    }

    /**
     * Updates order status to REJECTED and publishes a trade event in separate
     * transaction.
     * 
     * This ensures the status update and event publishing survive even if the
     * parent
     * transaction rolls back.
     * 
     * @param order           the order to update
     * @param previousStatus  the previous order status (for event publishing)
     * @param rejectionReason the reason for rejection
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void updateOrderToRejectedAndPublishEvent(Order order, OrderStatus previousStatus, String rejectionReason) {
        order.setStatus(OrderStatus.REJECTED);
        orderRepository.save(order);
        log.info("Updated order status to REJECTED: orderId={}", order.getId());

        // Publish trade event after status is persisted
        tradeEventPublisher.publish(order, previousStatus, rejectionReason);
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
