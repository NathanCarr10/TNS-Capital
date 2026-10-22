package com.neueda.leap.exceptions;

public class DuplicateInstrumentException extends TradingException {

    public DuplicateInstrumentException(String message) {
        super("INS-409", message);
    }
}
