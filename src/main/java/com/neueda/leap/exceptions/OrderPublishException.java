package com.neueda.leap.exceptions;

/**
 * An order could not be queued on Kafka (broker down or too slow to answer),
 * so it will not be executed. Returned as 503 SYS-503.
 */
public class OrderPublishException extends TradingException {
    public OrderPublishException(String message, Throwable cause) {
        super("SYS-503", message, cause);
    }
}
