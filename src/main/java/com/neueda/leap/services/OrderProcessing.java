package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.time.Clock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.utils.PositionKeyFactory;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates order placement: validates, executes, and persists.
 */
@Service
public class OrderProcessing {
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final OrderValidator validator;
    private final Map<OrderSide, OrderExecutionStrategy> strategies;
    private final Clock clock;

    public OrderProcessing(
            AccountRepository accountRepository,
            OrderRepository orderRepository,
            PositionRepository positionRepository,
            OrderValidator validator,
            Map<OrderSide, OrderExecutionStrategy> strategies,
            Clock clock) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.positionRepository = Objects.requireNonNull(positionRepository);
        this.validator = Objects.requireNonNull(validator);
        this.strategies = Objects.requireNonNull(strategies);
        this.clock = Objects.requireNonNull(clock);
    }

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

        return order;
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return orderRepository.findByIdempotencyKey(key);
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        return positionRepository.findByAccountIdAndSymbol(accountId, InputNormalizer.normalize(symbol));
    }

    public void cancelOrder(Order order) {
        // Sets order status to CANCELLED and persists; prevents double-cancellation by only allowing NEW orders
        Objects.requireNonNull(order);
        if (!order.getStatus().equals(OrderStatus.NEW)) {
            throw new IllegalStateException("Cannot cancel order with status: " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }
}
