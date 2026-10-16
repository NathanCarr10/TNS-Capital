package com.neueda.leap.exceptions;

public class PriceUnavailableException extends ServiceUnavailableException {

    public PriceUnavailableException(String message) {
        super("PRC-503", message);
    }
}
