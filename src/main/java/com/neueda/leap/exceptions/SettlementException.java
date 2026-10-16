package com.neueda.leap.exceptions;

import com.neueda.leap.kafka.events.OrderEvent;

/**
 * A fill could not be settled (e.g. the cash was spent by another order
 * first), so the order was stored as REJECTED. Carries the order as an
 * OrderEvent so it can be captured in the DLQ and replayed.
 */
public class SettlementException extends TradingException {
    private final transient OrderEvent orderEvent;

    public SettlementException(OrderEvent orderEvent, RuntimeException cause) {
        super(errorCodeOf(cause), cause.getMessage(), cause);
        this.orderEvent = orderEvent;
    }

    public OrderEvent getOrderEvent() {
        return orderEvent;
    }

    /** False when replaying cannot help, e.g. the account no longer exists */
    public boolean isRetryable() {
        return !(getCause() instanceof AccountNotFoundException);
    }
}
