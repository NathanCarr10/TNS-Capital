package com.neueda.leap.services;

import com.neueda.leap.domain.Account;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.domain.Order;
import com.neueda.leap.domain.Position;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OrderProcessing {

    private final Map<Long, Account> accountsById;
    private final Map<String, Instrument> instrumentsBySymbol;
    private final Map<String, Position> positionsByAccountAndSymbol;
    private final Map<String, Order> ordersByIdempotencyKey;

    public OrderProcessing(
            Map<Long, Account> accountsById,
            Map<String, Instrument> instrumentsBySymbol,
            Map<String, Position> positionsByAccountAndSymbol,
            Map<String, Order> ordersByIdempotencyKey) {
        this.accountsById = Objects.requireNonNull(accountsById);
        this.instrumentsBySymbol = Objects.requireNonNull(instrumentsBySymbol);
        this.positionsByAccountAndSymbol = Objects.requireNonNull(positionsByAccountAndSymbol);
        this.ordersByIdempotencyKey = Objects.requireNonNull(ordersByIdempotencyKey);
    }

    public Order placeOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (ordersByIdempotencyKey.containsKey(request.idempotencyKey())) {
            throw new DuplicateOrderException(
                    "Order already submitted for idempotency key: " + request.idempotencyKey());
        }

        Account account = accountsById.get(request.accountId());
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + request.accountId());
        }

        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + request.accountId());
        }

        String symbol = normaliseSymbol(request.symbol());
        Instrument instrument = findInstrument(symbol);
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException("Instrument not available for trading: " + symbol);
        }

        Order order = new Order(
                request.accountId(),
                symbol,
                request.side(),
                request.quantity(),
                request.price(),
                request.idempotencyKey());

        try {
            if (request.side() == OrderSide.BUY) {
                processBuy(account, request, symbol);
            } else {
                processSell(account, request, symbol);
            }

            order.setStatus(OrderStatus.FILLED);
            ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
            return order;
        } catch (RuntimeException ex) {
            order.setStatus(OrderStatus.REJECTED);
            ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
            throw ex;
        }
    }

    public Optional<Order> findOrderByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(ordersByIdempotencyKey.get(idempotencyKey));
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        return Optional.ofNullable(positionsByAccountAndSymbol.get(positionKey(accountId, symbol)));
    }

    private void processBuy(Account account, PlaceOrderRequest request, String symbol) {
        BigDecimal totalCost = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        account.debit(totalCost);

        String positionKey = positionKey(request.accountId(), symbol);
        Position currentPosition = positionsByAccountAndSymbol.get(positionKey);

        if (currentPosition == null || currentPosition.getQuantity() == null || currentPosition.getQuantity() == 0) {
            positionsByAccountAndSymbol.put(
                    positionKey,
                    new Position(request.accountId(), symbol, request.quantity(), request.price()));
            return;
        }

        currentPosition.apply(request.quantity(), request.price());
    }

    private void processSell(Account account, PlaceOrderRequest request, String symbol) {
        String positionKey = positionKey(request.accountId(), symbol);
        Position currentPosition = positionsByAccountAndSymbol.get(positionKey);

        if (currentPosition == null || currentPosition.getQuantity() == null
                || currentPosition.getQuantity() < request.quantity()) {
            throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
        }

        BigDecimal proceeds = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        account.credit(proceeds);

        int remainingQuantity = currentPosition.getQuantity() - request.quantity();

        if (remainingQuantity == 0) {
            positionsByAccountAndSymbol.remove(positionKey);
            return;
        }

        positionsByAccountAndSymbol.put(
                positionKey,
                new Position(
                        currentPosition.getAccountId(),
                        currentPosition.getSymbol(),
                        remainingQuantity,
                        currentPosition.getAverageCost()));
    }

        private Instrument findInstrument(String symbol) {
            Instrument instrument = instrumentsBySymbol.get(symbol);
            if (instrument != null) {
                return instrument;
        }

            for (Map.Entry<String, Instrument> entry : instrumentsBySymbol.entrySet()) {
                if (normaliseSymbol(entry.getKey()).equals(symbol)) {
                    return entry.getValue();
                }
            }

            return null;
    }

    private String positionKey(Long accountId, String symbol) {
        return accountId + "::" + normaliseSymbol(symbol);
    }

    private String normaliseSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}