package com.neueda.leap.services;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.mappers.AccountMapper;
import com.neueda.leap.mappers.InstrumentMapper;
import com.neueda.leap.mappers.OrderMapper;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.utils.InputNormalizer;

import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Validates order requests before execution.
 * 
 * Retrieves validation data directly from MyBatis mappers for account,
 * instrument, and order entities.
 */
@Service
public class OrderValidator {
    private final AccountMapper accountMapper;
    private final InstrumentMapper instrumentMapper;
    private final OrderMapper orderMapper;

    public OrderValidator(
            AccountMapper accountMapper,
            InstrumentMapper instrumentMapper,
            OrderMapper orderMapper) {
        this.accountMapper = Objects.requireNonNull(accountMapper);
        this.instrumentMapper = Objects.requireNonNull(instrumentMapper);
        this.orderMapper = Objects.requireNonNull(orderMapper);
    }

    public void validate(PlaceOrderRequest request) {
        // Check duplicate
        Order order = orderMapper.findByIdempotencyKey(request.idempotencyKey());
        if (order != null) {
            throw new DuplicateOrderException("Order already submitted: " + request.idempotencyKey());
        }

        // Check account exists and active
        Account account = accountMapper.findById(request.accountId());
        if (account == null) {
            throw new AccountNotFoundException("Account not found: " + request.accountId());
        }
        if (!account.isActive()) {
            throw new AccountNotActiveException("Account not active: " + request.accountId());
        }

        // Check instrument exists and tradable
        String symbol = InputNormalizer.normalize(request.symbol());
        Instrument instrument = instrumentMapper.findBySymbol(symbol);
        if (instrument == null) {
            throw new InstrumentNotFoundException("Instrument not found: " + symbol);
        }
        if (!instrument.isTradable()) {
            throw new InstrumentNotFoundException("Instrument not tradable: " + symbol);
        }
    }
}
