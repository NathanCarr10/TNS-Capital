package com.neueda.leap.strategy.errors;

/**
 * The strategy, or the account, is not the caller's; or the caller has no
 * trading account (support and admin users) and tried to create or change one.
 */
public class NotOwnerException extends StrategyException {

    public NotOwnerException(String message) {
        super("AUTH-403", message);
    }
}
