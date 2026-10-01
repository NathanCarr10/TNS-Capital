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

    @SuppressWarnings("null")
    public Order placeOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);

        validator.validate(request);
        String symbol = InputNormalizer.normalize(request.symbol());
        Order order = new Order(request.accountId(), symbol, request.side(), request.quantity(),
                request.price(), request.idempotencyKey(), clock);

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
            order.setStatus(OrderStatus.FILLED);
        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            order.setStatus(OrderStatus.REJECTED);
            throw ex;
        } catch (Exception ex) {
            order.setStatus(OrderStatus.REJECTED);
            throw new IllegalStateException("Unexpected error during order execution", ex);
        } finally {
            orderRepository.save(order);
        }

        // Publish order event to Kafka (when order is placed)
        orderEventPublisher.publish(order);

        // Publish trade event to Kafka (when order is filled)
        if (order.getStatus() == OrderStatus.FILLED) {
            tradeEventPublisher.publish(order);
        }

        return order;
    }

    /**
     * Processes an order event from Kafka asynchronously.
     * 
     * Called by OrderMessageListener when an order event is received from the Kafka
     * topic.
     * Validates and executes the order, creating a new Order entity if it doesn't
     * already exist
     * (based on order ID).
     * 
     * @param event the order event to process
     * @return the processed order
     * @throws Exception if validation or execution fails
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

            // Retrieve account
            Account account = accountRepository.findById(event.accountId())
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + event.accountId()));

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
            order.setStatus(OrderStatus.FILLED);

            // Save the successfully processed order
            orderRepository.save(order);

            // Publish trade event if order was filled
            if (order.getStatus() == OrderStatus.FILLED) {
                tradeEventPublisher.publish(order);
            }

            return order;

        } catch (InsufficientFundsException | InsufficientHoldingsException ex) {
            log.warn("Order rejected due to insufficient resources: orderId={}, error={}", event.orderId(),
                    ex.getMessage());

            // Save rejected order in separate transaction before exception causes rollback
            saveRejectedOrder(event, symbol);

            throw ex;

        } catch (Exception ex) {
            log.error("Unexpected error processing order event: orderId={}, error={}", event.orderId(), ex.getMessage(),
                    ex);

            // Save rejected order in separate transaction before exception causes rollback
            saveRejectedOrder(event, symbol);

            throw new IllegalStateException("Unexpected error during order event processing: " + ex.getMessage(), ex);
        }
    }

    /**
     * Saves a REJECTED order in a separate transaction.
     * 
     * Uses Propagation.REQUIRES_NEW to ensure the order is persisted even if
     * the parent transaction rolls back due to an exception.
     * 
     * @param event  the order event
     * @param symbol the normalized order symbol
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveRejectedOrder(OrderEvent event, String symbol) {
        try {
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
            orderRepository.save(order);
            log.info("Saved REJECTED order: orderId={}", event.orderId());
        } catch (Exception ex) {
            log.error("Failed to save rejected order: orderId={}, error={}", event.orderId(), ex.getMessage());
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
     * When an order is cancelled, it is archived to OrderHistory for audit trail.
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

        // Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Archive cancelled order to history for audit trail
        OrderHistory history = new OrderHistory(order, clock);
        orderHistoryRepository.save(history);

        return order;
    }
}
