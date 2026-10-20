package com.neueda.leap.exceptions;

/**
 * Exception thrown when a newsletter issue cannot be found by ID.
 */
public class NewsletterNotFoundException extends TradingException {
    public NewsletterNotFoundException(String message) {
        super("NWS-404", message);
    }
}
