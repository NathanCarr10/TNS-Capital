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

/**
 * Orchestrates order placement workflow: validates, processes, and persists orders.
 */
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

    /**
     * Places an order after validation. Processes based on order side (BUY/SELL).
     * Orders are persisted regardless of success/failure for audit trail.
     */
    public Order placeOrder(PlaceOrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        
        validateOrderRequest(request);
        String normalizedSymbol = normalizeSymbol(request.symbol());
        
        Order order = createOrder(request, normalizedSymbol);

        try {
            processOrderBySide(request.accountId(), request, normalizedSymbol);
            order.setStatus(OrderStatus.FILLED);
        } catch (RuntimeException ex) {
            order.setStatus(OrderStatus.REJECTED);
            throw ex;
        } finally {
            persistOrder(order);
        }

        return order;
    }

    /**
     * Validates all order request requirements. Throws specific exceptions for each violation.
     */
    private void validateOrderRequest(PlaceOrderRequest request) {
        checkOrderNotDuplicate(request.idempotencyKey());
        
        Account account = findAccountOrThrow(request.accountId());
        checkAccountIsActive(account, request.accountId());
        
        String symbol = normalizeSymbol(request.symbol());
        checkInstrumentIsAvailable(symbol);
    }

    private void checkOrderNotDuplicate(String idempotencyKey) {
        if (ordersByIdempotencyKey.containsKey(idempotencyKey)) {
            throw new DuplicateOrderException(
                    "Order already submitted for idempotency key: " + idempotencyKey);
        }
    }

    private Account findAccountOrThrow(Long accountId) {
        Account account = accountsById.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        return account;
    }

    private void checkAccountIsActive(Account account, Long accountId) {
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account is not active: " + accountId);
        }
    }

    private void checkInstrumentIsAvailable(String symbol) {
        Instrument instrument = findInstrument(symbol);
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException("Instrument not available for trading: " + symbol);
        }
    }

    private Order createOrder(PlaceOrderRequest request, String symbol) {
        return new Order(
                request.accountId(),
                symbol,
                request.side(),
                request.quantity(),
                request.price(),
                request.idempotencyKey());
    }

    private void processOrderBySide(Long accountId, PlaceOrderRequest request, String symbol) {
        Account account = accountsById.get(accountId);
        
        if (request.side() == OrderSide.BUY) {
            executeBuyOrder(account, request, symbol);
        } else {
            executeSellOrder(account, request, symbol);
        }
    }

    private void persistOrder(Order order) {
        ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
    }

    public Optional<Order> findOrderByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(ordersByIdempotencyKey.get(idempotencyKey));
    }

    public Optional<Position> findPosition(Long accountId, String symbol) {
        return Optional.ofNullable(getPosition(accountId, symbol));
    }

    /**
     * Executes a BUY order: debits account and updates position.
     */
    private void executeBuyOrder(Account account, PlaceOrderRequest request, String symbol) {
        BigDecimal totalCost = calculateOrderCost(request);
        account.debit(totalCost);
        updatePositionForBuy(request.accountId(), symbol, request.quantity(), request.price());
    }

    /**
     * Executes a SELL order: validates holdings, credits account, and updates position.
     */
    private void executeSellOrder(Account account, PlaceOrderRequest request, String symbol) {
        Position currentPosition = getPosition(request.accountId(), symbol);
        validateSufficientHoldings(currentPosition, request.quantity(), symbol);
        
        BigDecimal proceeds = calculateOrderProceeds(request);
        account.credit(proceeds);
        updatePositionForSell(currentPosition, request.quantity());
    }

    private BigDecimal calculateOrderCost(PlaceOrderRequest request) {
        return request.price().multiply(BigDecimal.valueOf(request.quantity()));
    }

    private BigDecimal calculateOrderProceeds(PlaceOrderRequest request) {
        return request.price().multiply(BigDecimal.valueOf(request.quantity()));
    }

    private void updatePositionForBuy(Long accountId, String symbol, int quantity, BigDecimal price) {
        Position currentPosition = getPosition(accountId, symbol);

        if (isNewPosition(currentPosition)) {
            createNewPosition(accountId, symbol, quantity, price);
        } else {
            updateExistingPosition(currentPosition, quantity, price);
        }
    }

    private void updatePositionForSell(Position position, int quantityToSell) {
        int remainingQuantity = position.getQuantity() - quantityToSell;

        if (remainingQuantity == 0) {
            removePosition(position.getAccountId(), position.getSymbol());
        } else {
            Position updatedPosition = new Position(
                    position.getAccountId(),
                    position.getSymbol(),
                    remainingQuantity,
                    position.getAverageCost());
            savePosition(updatedPosition);
        }
    }

    private void validateSufficientHoldings(Position position, int requestedQuantity, String symbol) {
        if (position == null || position.getQuantity() == null || position.getQuantity() < requestedQuantity) {
            throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
        }
    }

    private boolean isNewPosition(Position position) {
        return position == null || position.getQuantity() == null || position.getQuantity() == 0;
    }

    private void createNewPosition(Long accountId, String symbol, int quantity, BigDecimal price) {
        Position newPosition = new Position(accountId, symbol, quantity, price);
        savePosition(newPosition);
    }

    private void updateExistingPosition(Position position, int quantity, BigDecimal price) {
        position.apply(quantity, price);
        savePosition(position);
    }

    private Position getPosition(Long accountId, String symbol) {
        return positionsByAccountAndSymbol.get(buildPositionKey(accountId, symbol));
    }

    private void savePosition(Position position) {
        String key = buildPositionKey(position.getAccountId(), position.getSymbol());
        positionsByAccountAndSymbol.put(key, position);
    }

    private void removePosition(Long accountId, String symbol) {
        positionsByAccountAndSymbol.remove(buildPositionKey(accountId, symbol));
    }

    /**
     * Finds an instrument by symbol, with case-insensitive fallback.
     */
    private Instrument findInstrument(String symbol) {
        Instrument instrument = instrumentsBySymbol.get(symbol);
        if (instrument != null) {
            return instrument;
        }

        // Fallback: search with normalization
        for (Map.Entry<String, Instrument> entry : instrumentsBySymbol.entrySet()) {
            if (normalizeSymbol(entry.getKey()).equals(symbol)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private String buildPositionKey(Long accountId, String symbol) {
        return accountId + "::" + normalizeSymbol(symbol);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}