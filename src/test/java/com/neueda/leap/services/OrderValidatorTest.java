package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
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
import com.neueda.leap.time.ClockTest;

@DisplayName("OrderValidator Test Suite")
class OrderValidatorTest {
    private OrderValidator orderValidator;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private InstrumentMapper instrumentMapper;

    @Mock
    private OrderMapper orderMapper;

    private ClockTest testClock;
    private PlaceOrderRequest validRequest;
    private Account testAccount;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        orderValidator = new OrderValidator(accountMapper, instrumentMapper, orderMapper);

        testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
        testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);
        testAccount.setId(1L);
        testAccount.setStatus(AccountStatus.ACTIVE);

        testInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        validRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"),
                "ORDER-001");
    }

    @DisplayName("Successful Validation Tests")
    @Nested
    class SuccessfulValidationTests {
        @DisplayName("Should validate request successfully with all valid data")
        @Test
        void testValidateSuccess() {
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(testInstrument);

            assertDoesNotThrow(() -> orderValidator.validate(validRequest),
                    "Should not throw exception for valid request");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, times(1)).findBySymbol("AAPL");
        }

        @DisplayName("Should validate request with different order sides")
        @Test
        void testValidateSuccessWithSellOrder() {
            PlaceOrderRequest sellRequest = new PlaceOrderRequest(1L, "MSFT", OrderSide.SELL, 50,
                    new BigDecimal("300.00"), "ORDER-002");

            when(orderMapper.findByIdempotencyKey("ORDER-002")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
Instrument msftInstrument = new Instrument("MSFT", "Microsoft", "EQUITY", "USD", true);
                when(instrumentMapper.findBySymbol("MSFT")).thenReturn(msftInstrument);

            assertDoesNotThrow(() -> orderValidator.validate(sellRequest),
                    "Should validate SELL order successfully");
        }

        @DisplayName("Should normalize symbol during validation")
        @Test
        void testValidateNormalizesSymbol() {
            PlaceOrderRequest lowercaseRequest = new PlaceOrderRequest(1L, "aapl", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "ORDER-003");

            when(orderMapper.findByIdempotencyKey("ORDER-003")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(testInstrument);

            assertDoesNotThrow(() -> orderValidator.validate(lowercaseRequest));

            verify(instrumentMapper, times(1)).findBySymbol("AAPL");
        }
    }

    @DisplayName("Duplicate Order Validation Tests")
    @Nested
    class DuplicateOrderValidationTests {
        @DisplayName("Should throw DuplicateOrderException when order already submitted")
        @Test
        void testValidateDuplicateOrder() {
            Order existingOrder = new Order(testClock);
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(existingOrder);

            assertThrows(DuplicateOrderException.class, () -> orderValidator.validate(validRequest),
                    "Should throw DuplicateOrderException for duplicate idempotency key");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, never()).findById(any());
            verify(instrumentMapper, never()).findBySymbol(any());
        }

        @DisplayName("Should detect duplicate orders with different account IDs")
        @Test
        void testValidateDuplicateOrderDifferentAccount() {
            Order existingOrder = new Order(testClock);
            PlaceOrderRequest differentAccountRequest = new PlaceOrderRequest(2L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "ORDER-001");

            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(existingOrder);

            assertThrows(DuplicateOrderException.class, () -> orderValidator.validate(differentAccountRequest),
                    "Should throw DuplicateOrderException even with different account");
        }
    }

    @DisplayName("Account Validation Tests")
    @Nested
    class AccountValidationTests {
        @DisplayName("Should throw AccountNotFoundException when account not found")
        @Test
        void testValidateAccountNotFound() {
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(null);

            assertThrows(AccountNotFoundException.class, () -> orderValidator.validate(validRequest),
                    "Should throw AccountNotFoundException when account not found");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, never()).findBySymbol(any());
        }

        @DisplayName("Should throw AccountNotActiveException when account is inactive")
        @Test
        void testValidateAccountNotActive() {
            testAccount.setStatus(AccountStatus.SUSPENDED);
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);

            assertThrows(AccountNotActiveException.class, () -> orderValidator.validate(validRequest),
                    "Should throw AccountNotActiveException when account is not active");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, never()).findBySymbol(any());
        }

        @DisplayName("Should throw AccountNotFoundException for non-existent account ID")
        @Test
        void testValidateNonExistentAccountId() {
            PlaceOrderRequest invalidRequest = new PlaceOrderRequest(999L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "ORDER-004");

            when(orderMapper.findByIdempotencyKey("ORDER-004")).thenReturn(null);
            when(accountMapper.findById(999L)).thenReturn(null);

            assertThrows(AccountNotFoundException.class, () -> orderValidator.validate(invalidRequest),
                    "Should throw AccountNotFoundException for invalid account ID");
        }
    }

    @DisplayName("Instrument Validation Tests")
    @Nested
    class InstrumentValidationTests {
        @DisplayName("Should throw InstrumentNotFoundException when instrument not found")
        @Test
        void testValidateInstrumentNotFound() {
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(null);

            assertThrows(InstrumentNotFoundException.class, () -> orderValidator.validate(validRequest),
                    "Should throw InstrumentNotFoundException when instrument not found");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, times(1)).findBySymbol("AAPL");
        }

        @DisplayName("Should throw InstrumentNotFoundException when instrument is not tradable")
        @Test
        void testValidateInstrumentNotTradable() {
            Instrument nonTradableInstrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", false);
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(nonTradableInstrument);

            assertThrows(InstrumentNotFoundException.class, () -> orderValidator.validate(validRequest),
                    "Should throw InstrumentNotFoundException when instrument is not tradable");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, times(1)).findBySymbol("AAPL");
        }

        @DisplayName("Should validate various tradable instruments")
        @Test
        void testValidateVariousTradableInstruments() {
            String[] symbols = { "AAPL", "MSFT", "GOOG", "AMZN" };

            String[] stockNames = { "Apple Inc.", "Microsoft Inc.", "Google Inc.", "Amazon Inc." };
            for (int i = 0; i < symbols.length; i++) {
                String symbol = symbols[i];
                PlaceOrderRequest request = new PlaceOrderRequest(1L, symbol, OrderSide.BUY, 100,
                        new BigDecimal("150.00"), "ORDER-" + symbol);
                Instrument instrument = new Instrument(symbol, stockNames[i], "EQUITY", "USD", true);

                when(orderMapper.findByIdempotencyKey("ORDER-" + symbol)).thenReturn(null);
                when(accountMapper.findById(1L)).thenReturn(testAccount);
                when(instrumentMapper.findBySymbol(symbol)).thenReturn(instrument);

                assertDoesNotThrow(() -> orderValidator.validate(request),
                        "Should validate tradable instrument: " + symbol);
            }
        }
    }

    @DisplayName("Validation Order Tests")
    @Nested
    class ValidationOrderTests {
        @DisplayName("Should check duplicate order first before checking account")
        @Test
        void testValidatesOrderDuplicateBeforeAccount() {
            Order existingOrder = new Order(testClock);
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(existingOrder);
            when(accountMapper.findById(1L)).thenReturn(null);

            assertThrows(DuplicateOrderException.class, () -> orderValidator.validate(validRequest),
                    "Should throw DuplicateOrderException before checking account");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, never()).findById(any());
        }

        @DisplayName("Should check account before checking instrument")
        @Test
        void testValidatesAccountBeforeInstrument() {
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(null);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(null);

            assertThrows(AccountNotFoundException.class, () -> orderValidator.validate(validRequest),
                    "Should throw AccountNotFoundException before checking instrument");

            verify(orderMapper, times(1)).findByIdempotencyKey("ORDER-001");
            verify(accountMapper, times(1)).findById(1L);
            verify(instrumentMapper, never()).findBySymbol(any());
        }

        @DisplayName("Should check instrument exists before checking tradability")
        @Test
        void testValidatesInstrumentExistsBeforeTradability() {
            when(orderMapper.findByIdempotencyKey("ORDER-001")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(null);

            assertThrows(InstrumentNotFoundException.class, () -> orderValidator.validate(validRequest),
                    "Should throw InstrumentNotFoundException when instrument not found");
        }
    }

    @DisplayName("Edge Case Tests")
    @Nested
    class EdgeCaseTests {
        @DisplayName("Should handle case-insensitive symbol normalization")
        @Test
        void testValidateSymbolNormalization() {
            PlaceOrderRequest mixedCaseRequest = new PlaceOrderRequest(1L, "ApPl", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "ORDER-005");

            when(orderMapper.findByIdempotencyKey("ORDER-005")).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("APPL")).thenReturn(testInstrument);

            assertDoesNotThrow(() -> orderValidator.validate(mixedCaseRequest));
            verify(instrumentMapper, times(1)).findBySymbol("APPL");
        }

        @DisplayName("Should handle idempotency key normalization")
        @Test
        void testValidateIdempotencyKeyNormalization() {
            PlaceOrderRequest requestWithSpaces = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "  ORDER-001  ");

            when(orderMapper.findByIdempotencyKey(any())).thenReturn(null);
            when(accountMapper.findById(1L)).thenReturn(testAccount);
            when(instrumentMapper.findBySymbol("AAPL")).thenReturn(testInstrument);

            assertDoesNotThrow(() -> orderValidator.validate(requestWithSpaces));
            verify(orderMapper, times(1)).findByIdempotencyKey(any());
        }
    }
}
