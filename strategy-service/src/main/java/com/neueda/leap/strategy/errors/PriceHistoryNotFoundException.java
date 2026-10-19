package com.neueda.leap.strategy.errors;

public class PriceHistoryNotFoundException extends StrategyException {

    public PriceHistoryNotFoundException(String message) {
        super("PRC-404", message);
    }
}
