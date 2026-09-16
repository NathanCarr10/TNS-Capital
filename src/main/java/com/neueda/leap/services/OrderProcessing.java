package com.neueda.leap.services;

import com.neueda.leap.domain.Account;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.domain.Order;
import com.neueda.leap.domain.Position;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.time.SystemClock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates order placement: validates, executes, and persists.
 */
public class OrderProcessing {
    private final Map<Long, Account> accounts;
    private final Map<String, Instrument> instruments;
    private final Map<String, Position> positions;
    private final Map<String, Order> orders;
    private final OrderValidator validator;
    private final Map<OrderSide, OrderExecutionStrategy> strategies;

    public OrderProcessing(
            Map<Long, Account> accounts,
            Map<String, Instrument> instruments,
            Map<String, Position> positions,
            Map<String, Order> orders,
            OrderValidator validator,
            Map<OrderSide, OrderExecutionStrategy> strategies) {
        this.accounts = Objects.requireNonNull(accounts);
        this.instruments = Objects.requireNonNull(instruments);
        this.positions = Objects.requireNonNull(positions);
        this.orders = Objects.requireNonNull(orders);
        this.validator = Objects.requireNonNull(validator);
        this.strategies = Objects.requireNonNull(strategies);
    }

    public Order placeOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request);

        validator.validate(request);
        String symbol = normalizeSymbol(request.symbol());
        Order order = new Order(request.accountId(), symbol, request.side(), request.quantity(),
                request.price(), request.idempotencyKey(), SystemClock.INSTANCE);

        try {
            Account account = accounts.get(request.accountId());
            OrderExecutionStrategy strategy = strategies.get(request.side());
            strategy.execute(account, request, symbol);
            order.setStatus(OrderStatus.FILLED);
        } catch (Exception ex) {
            order.setStatus(OrderStatus.REJECTED);
            throw ex;
        } finally {
            orders.put(order.getIdempotencyKey(), order);
        }

        return order;
    }

    public Optional<Order> findByIdempotencyKey(String key) {
        return Optional.ofNullable(orders.get(key));
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        String key = accountId + "::" + normalizeSymbol(symbol);
        return Optional.ofNullable(positions.get(key));
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
