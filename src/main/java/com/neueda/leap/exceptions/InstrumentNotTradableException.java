package com.neueda.leap.exceptions;

/**
 * The instrument exists but is not open for trading. A subclass of
 * InstrumentNotFoundException so the API still answers INS-404, while the
 * order processor can tell it apart from an unknown symbol: a not-tradable
 * order can be saved as REJECTED, an unknown symbol cannot (orders.symbol
 * references instruments).
 */
public class InstrumentNotTradableException extends InstrumentNotFoundException {

    public InstrumentNotTradableException(String message) {
        super(message);
    }
}
