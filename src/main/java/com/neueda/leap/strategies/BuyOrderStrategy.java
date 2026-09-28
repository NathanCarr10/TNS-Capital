package com.neueda.leap.strategies;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.exceptions.InsufficientFundsException;

import java.math.BigDecimal;
import java.util.Objects;

public class BuyOrderStrategy implements OrderExecutionStrategy {
    private final PositionRepository positionRepository;

    public BuyOrderStrategy(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    @Override
    public void execute(Account account, PlaceOrderRequest request, String symbol) {
        BigDecimal cost = request.price().multiply(BigDecimal.valueOf(request.quantity()));

        // Phase 1: Validate before mutation
        if (account.getCashBalance().compareTo(cost) < 0) {
            throw new InsufficientFundsException("Insufficient funds for buy order");
        }

        // Phase 2: Perform debit operation
        account.debit(cost);

        try {
            // Phase 2: Perform position update
            var currentPosition = positionRepository.findByAccountIdAndSymbol(request.accountId(), symbol);

            if (currentPosition.isEmpty()) {
                positionRepository.save(new Position(request.accountId(), symbol, request.quantity(), request.price()));
            } else {
                Position position = currentPosition.get();
                position.apply(request.quantity(), request.price());
                positionRepository.save(position);
            }
        } catch (Exception ex) {
            // Phase 3: Rollback on exception
            account.credit(cost); // Undo debit
            throw new IllegalStateException("Position update failed; account restored", ex);
        }
    }
}
