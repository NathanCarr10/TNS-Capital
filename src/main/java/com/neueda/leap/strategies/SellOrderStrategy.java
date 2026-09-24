package com.neueda.leap.strategies;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.PositionRepository;

import java.math.BigDecimal;
import java.util.Objects;

public class SellOrderStrategy implements OrderExecutionStrategy {
    private final PositionRepository positionRepository;

    public SellOrderStrategy(PositionRepository positionRepository) {
        this.positionRepository = Objects.requireNonNull(positionRepository);
    }

    @Override
    public void execute(Account account, PlaceOrderRequest request, String symbol) {
        // Phase 1: Validate position exists and has sufficient quantity
        var currentPosition = positionRepository.findByAccountIdAndSymbol(request.accountId(), symbol)
                .orElseThrow(() -> new InsufficientHoldingsException("No position for symbol: " + symbol));

        if (currentPosition.getQuantity() < request.quantity()) {
            throw new InsufficientHoldingsException("Insufficient holdings for symbol: " + symbol);
        }

        // Phase 2: Perform credit operation
        BigDecimal proceeds = request.price().multiply(BigDecimal.valueOf(request.quantity()));
        account.credit(proceeds);

        try {
            // Phase 2: Update position
            int remaining = currentPosition.getQuantity() - request.quantity();
            if (remaining == 0) {
                positionRepository.deleteByAccountIdAndSymbol(request.accountId(), symbol);
            } else {
                Position updatedPosition = new Position(request.accountId(), symbol, remaining,
                        currentPosition.getAverageCost());
                positionRepository.save(updatedPosition);
            }
        } catch (Exception ex) {
            // Phase 3: Rollback on exception
            account.debit(proceeds); // Undo credit
            throw new IllegalStateException("Position update failed; account restored", ex);
        }
    }
}
