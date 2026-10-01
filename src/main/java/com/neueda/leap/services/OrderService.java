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
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates order placement: validates, executes, and persists.
 */
@Service
@Transactional
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
