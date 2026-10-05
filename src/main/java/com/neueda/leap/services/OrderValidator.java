package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotTradableException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.utils.InputNormalizer;

import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Validates order requests before execution: business rules 1-3 and 8 of the
 * specification (account exists and is active, instrument exists and is
 * tradable, idempotency key unused). Funds and holdings are checked by the
 * execution strategies.
 */
@Service
public class OrderValidator {
    private final AccountRepository accountRepository;
    private final InstrumentRepository instrumentRepository;
    private final OrderRepository orderRepository;

    public OrderValidator(
            AccountRepository accountRepository,
            InstrumentRepository instrumentRepository,
            OrderRepository orderRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.orderRepository = Objects.requireNonNull(orderRepository);
    }

    public void validate(PlaceOrderRequest request) {
        // Check duplicate; keys are stored normalized, so look up the normalized form
        String idempotencyKey = InputNormalizer.normalize(request.idempotencyKey());
        if (orderRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateOrderException("Order already submitted: " + request.idempotencyKey());
        }

        // Check account exists and active
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + request.accountId()));
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account not active: " + request.accountId());
        }

        // Check instrument exists and tradable
        String symbol = InputNormalizer.normalize(request.symbol());
        Instrument instrument = instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found: " + symbol));
        if (!instrument.isTradable()) {
            throw new InstrumentNotTradableException("Instrument not tradable: " + symbol);
        }
    }
}
