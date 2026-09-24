package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.utils.InputNormalizer;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Validates order requests before execution.
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
        // Check duplicate
        if (orderRepository.findByIdempotencyKey(request.idempotencyKey()).isPresent()) {
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
            throw new InstrumentNotFoundException("Instrument not tradable: " + symbol);
        }
    }
}
