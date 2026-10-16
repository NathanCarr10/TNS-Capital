package com.neueda.leap.exceptions;

/**
 * The request clashes with what is already stored, e.g. a reused idempotency
 * key or cancelling an order that has already filled. Returned as 409.
 */
public abstract class ConflictException extends TradingException {
    protected ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
