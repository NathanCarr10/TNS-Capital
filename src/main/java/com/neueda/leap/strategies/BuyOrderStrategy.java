package com.neueda.leap.strategies;

import com.neueda.leap.domain.Account;
import com.neueda.leap.domain.Position;
import com.neueda.leap.dtos.PlaceOrderRequest;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public class BuyOrderStrategy implements OrderExecutionStrategy {
    private final Map<String, Position> positions;

    public BuyOrderStrategy(Map<String, Position> positions) {
        this.positions = Objects.requireNonNull(positions);
    }

    @Override
    public void execute(Account account, PlaceOrderRequest request, String symbol) {
        BigDecimal cost = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        account.debit(cost);

        String key = request.accountId() + "::" + symbol;
        Position current = positions.get(key);
        if (current == null) {
            positions.put(key, new Position(request.accountId(), symbol, request.quantity(), request.price()));
        } else {
            current.apply(request.quantity(), request.price());
        }
    }
}
