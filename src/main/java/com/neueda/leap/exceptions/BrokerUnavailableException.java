package com.neueda.leap.exceptions;

/**
 * The Broker Simulator could not be reached or refused the order. The order
 * is rejected and captured in the DLQ as retryable, so an admin can replay it
 * once the broker is back.
 */
public class BrokerUnavailableException extends TradingException {
    public BrokerUnavailableException(String message, Throwable cause) {
        super("BRK-503", message, cause);
    }
}
