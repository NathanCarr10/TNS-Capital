package com.neueda.leap.exceptions;

/**
 * Something the request names (an account, instrument, order or position)
 * does not exist. Returned as 404.
 */
public abstract class ResourceNotFoundException extends TradingException {
    protected ResourceNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
