package com.neueda.leap.strategies;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Position;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public class SellOrderStrategy implements OrderExecutionStrategy {
    private final Map<String, Position> positions;

    public SellOrderStrategy(Map<String, Position> positions) {
        this.positions = Objects.requireNonNull(positions);
    }

    @Override
    public void execute(Account account, PlaceOrderRequest request, String symbol) {
        String key = request.accountId() + "::" + symbol;
        Position current = positions.get(key);

        if (current == null || current.getQuantity() < request.quantity()) {
            throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
        }

        BigDecimal proceeds = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        account.credit(proceeds);

        int remaining = current.getQuantity() - request.quantity();
        if (remaining == 0) {
            positions.remove(key);
        } else {
            positions.put(key, new Position(request.accountId(), symbol, remaining, current.getAverageCost()));
        }
    }
}
