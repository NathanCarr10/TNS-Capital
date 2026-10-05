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
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotTradableException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.repositories.AccountRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates order placement.
 *
 * Orders are placed in two steps:
 * 1. submitOrder (called by the API) checks the request-level rules (duplicate
 *    key, account, instrument) so the client gets the spec's error code
 *    straight away, then publishes the order to Kafka.
 * 2. processOrderEvent (called by the Kafka listener) executes it. Business
 *    rule failures are final outcomes, so the order is saved as REJECTED and
 *    nothing is retried; only unexpected errors are rethrown for retry and the
 *    dead-letter queue.
 */
@Service
@Transactional
@Slf4j
public class OrderService {
    private final AccountRepository accountRepository;
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
            OrderRepository orderRepository,
            OrderHistoryRepository orderHistoryRepository,
            PositionRepository positionRepository,
            OrderValidator validator,
            Map<OrderSide, OrderExecutionStrategy> strategies,
            Clock clock,
            OrderEventPublisher orderEventPublisher,
            TradeEventPublisher tradeEventPublisher) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.orderHistoryRepository = Objects.requireNonNull(orderHistoryRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.validator = Objects.requireNonNull(validator);
        this.strategies = Objects.requireNonNull(strategies);
        this.clock = Objects.requireNonNull(clock);
        this.orderEventPublisher = Objects.requireNonNull(orderEventPublisher);
        this.tradeEventPublisher = Objects.requireNonNull(tradeEventPublisher);
    }

    /**
     * Validates an order request and hands it to Kafka for execution.
     *
     * Throws DuplicateOrderException, AccountNotFoundException,
     * AccountNotActiveException or InstrumentNotFoundException when a rule fails,
     * and OrderSubmissionException when Kafka does not acknowledge the event.
     *
     * @return the ID the order will be stored under, for the client to poll
     */
    @Transactional(readOnly = true)
    public UUID submitOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);
        validator.validate(request);

        UUID orderId = UUID.randomUUID();
        OrderEvent event = new OrderEvent(
                orderId,
                request.accountId(),
                InputNormalizer.normalize(request.symbol()),
                request.side(),
                request.quantity(),
                request.price(),
                clock.now(),
                InputNormalizer.normalize(request.idempotencyKey()));

        orderEventPublisher.publishEvent(event, request.accountId());
        return orderId;
    }

    /**
     * Executes an order event from Kafka.
     *
     * Redelivered events (same order ID) and events whose idempotency key is
     * already used return the stored order without executing again.
     *
     * The order is saved as REJECTED, and the exception is not rethrown, when
     * the account is not active, the instrument is not tradable, or funds or
     * holdings are insufficient. The validator and strategies throw these before
     * changing any balance or position, so committing the REJECTED row is safe.
     *
     * AccountNotFoundException and InstrumentNotFoundException (unknown symbol)
     * are rethrown: the order row cannot be saved without them, and the Kafka
     * error handler sends them straight to the dead-letter queue. Anything else
     * is rethrown for the error handler to retry.
     *
     * @param event the order event to process
     * @return the stored order
     */
    public Order processOrderEvent(OrderEvent event) {
        Objects.requireNonNull(event, "Order event cannot be null");

        Optional<Order> existingOrder = orderRepository.findById(event.orderId());
        if (existingOrder.isPresent()) {
            log.info("Order already exists, skipping re-processing: orderId={}", event.orderId());
            return existingOrder.get();
        }

        String idempotencyKey = event.idempotencyKey() != null
                ? InputNormalizer.normalize(event.idempotencyKey())
                : event.orderId().toString();
        Optional<Order> sameKey = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (sameKey.isPresent()) {
            log.warn("Duplicate idempotency key, order not executed: orderId={}, existingOrderId={}",
                    event.orderId(), sameKey.get().getId());
            return sameKey.get();
        }

        String symbol = InputNormalizer.normalize(event.symbol());
        PlaceOrderRequest request = new PlaceOrderRequest(
                event.accountId(),
                symbol,
                event.side(),
                event.quantity(),
                event.price(),
                idempotencyKey);

        Order order = new Order(event.accountId(), symbol, event.side(), event.quantity(), event.price(),
                idempotencyKey, clock);
        order.setId(event.orderId());

        String rejectionReason = null;
        try {
            validator.validate(request);

            Account account = accountRepository.findById(event.accountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + event.accountId()));

            OrderExecutionStrategy strategy = strategies.get(event.side());
            if (strategy == null) {
                throw new IllegalStateException("No strategy registered for order side: " + event.side());
            }

            strategy.execute(account, request, symbol);
            order.setStatus(OrderStatus.FILLED);
        } catch (AccountNotActiveException | InstrumentNotTradableException
                | InsufficientFundsException | InsufficientHoldingsException ex) {
            rejectionReason = ex.getMessage();
            order.reject(rejectionReason);
            log.info("Order rejected: orderId={}, reason={}", event.orderId(), rejectionReason);
        }

        orderRepository.save(order);
        publishTradeEventAfterCommit(order, OrderStatus.NEW, rejectionReason);
        return order;
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(InputNormalizer.normalize(key));
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

        publishTradeEventAfterCommit(order, previousStatus, "User requested cancellation");

        return order;
    }

    /**
     * Publishes the lifecycle event only once the order's transaction commits, so
     * consumers never see an event for a change that was rolled back.
     */
    private void publishTradeEventAfterCommit(Order order, OrderStatus previousStatus, String reason) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tradeEventPublisher.publish(order, previousStatus, reason);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tradeEventPublisher.publish(order, previousStatus, reason);
            }
        });
    }
}
