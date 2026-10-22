package com.neueda.leap.strategy.errors;

public class InstrumentNotTradableException extends StrategyException {

    public InstrumentNotTradableException(String symbol) {
        super("INS-404", "Instrument not found or not tradable: " + symbol);
    }
}
