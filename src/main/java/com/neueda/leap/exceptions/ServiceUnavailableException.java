package com.neueda.leap.exceptions;

/**
 * A service the platform depends on (Kafka, the market data API, the Broker
 * Simulator) cannot be used right now. Trying again later may succeed.
 */
public abstract class ServiceUnavailableException extends TradingException {
    protected ServiceUnavailableException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected ServiceUnavailableException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
