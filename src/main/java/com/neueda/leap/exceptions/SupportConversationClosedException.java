package com.neueda.leap.exceptions;

/**
 * Exception thrown when a message is posted to a support conversation that
 * support has closed (OWASP review A04-7).
 */
public class SupportConversationClosedException extends TradingException {
    public SupportConversationClosedException(String message) {
        super("SUP-409", message);
    }
}
