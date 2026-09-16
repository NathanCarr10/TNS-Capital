package com.neueda.leap.services;

import com.neueda.leap.domain.Account;
import com.neueda.leap.domain.Instrument;
import com.neueda.leap.domain.Order;
import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Validates order requests before execution.
 */
public class OrderValidator {
    private final Map<Long, Account> accounts;
    private final Map<String, Instrument> instruments;
    private final Map<String, Order> orders;

    public OrderValidator(
            Map<Long, Account> accounts,
            Map<String, Instrument> instruments,
            Map<String, Order> orders) {
        this.accounts = Objects.requireNonNull(accounts);
        this.instruments = Objects.requireNonNull(instruments);
        this.orders = Objects.requireNonNull(orders);
    }

    public void validate(PlaceOrderRequest request) {
        // Check duplicate
        if (orders.containsKey(request.idempotencyKey())) {
            throw new DuplicateOrderException("Order already submitted: " + request.idempotencyKey());
        }

        // Check account exists and active
        Account account = accounts.get(request.accountId());
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + request.accountId());
        }
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account not active: " + request.accountId());
        }

        // Check instrument exists and tradable
        String symbol = normalizeSymbol(request.symbol());
        Instrument instrument = instruments.get(symbol);
        if (instrument == null || !instrument.isTradable()) {
            throw new InstrumentNotFoundException("Instrument not found: " + symbol);
        }
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase(Locale.ROOT);
    }
}
