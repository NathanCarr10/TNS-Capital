package com.neueda.leap.strategy.errors;

public class StrategyNotFoundException extends StrategyException {

    public StrategyNotFoundException(Long id) {
        super("STR-404", "Strategy not found: " + id);
    }
}
