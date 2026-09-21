package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.time.ClockTest;
import com.neueda.leap.repositories.impl.InMemoryAccountRepository;
import com.neueda.leap.repositories.impl.InMemoryInstrumentRepository;
import com.neueda.leap.repositories.impl.InMemoryOrderRepository;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;

@DisplayName("OrderValidator Test Suite")
class OrderValidatorTest {
        private OrderValidator validator;
        private Map<Long, Account> accountsMap;
        private Map<String, Instrument> instrumentsMap;
        private Map<String, Order> ordersMap;
        private AccountRepository accountRepository;
        private InstrumentRepository instrumentRepository;
        private OrderRepository orderRepository;
        private ClockTest testClock;
        private Account activeAccount;
        private Instrument tradableInstrument;

        @BeforeEach
        void setUp() {
                // Initialize test clock
                testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));

                // Initialize underlying maps
                accountsMap = new HashMap<>();
                instrumentsMap = new HashMap<>();
                ordersMap = new HashMap<>();

                // Create an active test account
                activeAccount = new Account("ACC001", "John Doe", new BigDecimal("50000.00"), testClock);
                activeAccount.setId(1L);
                accountsMap.put(1L, activeAccount);

                // Create a tradable test instrument
                tradableInstrument = new Instrument("AAPL", "APPLE INC", "EQUITY", "USD", true);
                // tradableInstrument.setId(1L);
                instrumentsMap.put("AAPL", tradableInstrument);

                // Create repository implementations
                accountRepository = new InMemoryAccountRepository(accountsMap);
                instrumentRepository = new InMemoryInstrumentRepository(instrumentsMap);
                orderRepository = new InMemoryOrderRepository(ordersMap);

                // Initialize validator with repositories
                validator = new OrderValidator(accountRepository, instrumentRepository, orderRepository);
        }

        @DisplayName("Valid Order Validation Tests")
        @Nested
        class ValidOrderTests {
                @DisplayName("Valid order request passes validation")
                @Test
                void testValidOrderPasses() {
                        PlaceOrderRequest validRequest = new PlaceOrderRequest(
                                        1L, // accountId
                                        "AAPL", // symbol
                                        OrderSide.BUY, // side
                                        100, // quantity
                                        new BigDecimal("150.00"), // price
                                        "ORDER-001" // idempotencyKey
                        );

                        // Should not throw any exception
                        assertDoesNotThrow(() -> validator.validate(validRequest),
                                        "Valid order request should pass validation");
                }

                @DisplayName("Symbol normalization works correctly")
                @Test
                void testSymbolNormalization() {
                        PlaceOrderRequest requestWithLowercase = new PlaceOrderRequest(
                                        1L,
                                        "aapl", // lowercase symbol
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "ORDER-002");

                        // Should not throw - validator normalizes to uppercase
                        assertDoesNotThrow(() -> validator.validate(requestWithLowercase),
                                        "Lowercase symbol should be normalized and pass validation");
                }

                @DisplayName("Symbol with whitespace is trimmed and normalized")
                @Test
                void testSymbolWhitespaceTrimming() {
                        PlaceOrderRequest requestWithWhitespace = new PlaceOrderRequest(
                                        1L,
                                        "  AAPL  ", // symbol with spaces
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "ORDER-003");

                        assertDoesNotThrow(() -> validator.validate(requestWithWhitespace),
                                        "Symbol with whitespace should be trimmed and pass validation");
                }
        }

        @DisplayName("Duplicate Order Detection Tests")
        @Nested
        class DuplicateOrderTests {
                @DisplayName("Duplicate order throws DuplicateOrderException")
                @Test
                void testDuplicateOrderThrowsException() {
                        String idempotencyKey = "DUPLICATE-KEY";

                        // Pre-populate the orders map with an existing order
                        Order existingOrder = new Order(1L, "AAPL", OrderSide.BUY, 100,
                                        new BigDecimal("150.00"), idempotencyKey, testClock);
                        ordersMap.put(idempotencyKey, existingOrder);

                        // Try to validate a request with the same idempotency key
                        PlaceOrderRequest duplicateRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        50,
                                        new BigDecimal("150.00"),
                                        idempotencyKey);

                        DuplicateOrderException exception = assertThrows(DuplicateOrderException.class,
                                        () -> validator.validate(duplicateRequest),
                                        "Should throw DuplicateOrderException for duplicate idempotency key");

                        assertTrue(exception.getMessage().contains(idempotencyKey),
                                        "Exception message should contain the idempotency key");
                }
        }

        @DisplayName("Account Validation Tests")
        @Nested
        class AccountValidationTests {
                @DisplayName("Non-existent account throws AccountNotFoundException")
                @Test
                void testAccountNotFoundThrowsException() {
                        PlaceOrderRequest requestWithInvalidAccount = new PlaceOrderRequest(
                                        999L, // Non-existent account ID
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "ORDER-004");

                        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class,
                                        () -> validator.validate(requestWithInvalidAccount),
                                        "Should throw AccountNotFoundException for non-existent account");

                        assertTrue(exception.getMessage().contains("999"),
                                        "Exception message should contain the account ID");
                }

                @DisplayName("Inactive account throws AccountNotActiveException")
                @Test
                void testInactiveAccountThrowsException() {
                        // Create an inactive account
                        Account inactiveAccount = new Account();
                        accountsMap.put(2L, inactiveAccount);

                        PlaceOrderRequest requestWithInactiveAccount = new PlaceOrderRequest(
                                        2L, // ID of inactive account
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "ORDER-005");

                        AccountNotActiveException exception = assertThrows(AccountNotActiveException.class,
                                        () -> validator.validate(requestWithInactiveAccount),
                                        "Should throw AccountNotActiveException for inactive account");

                        assertTrue(exception.getMessage().contains("2"),
                                        "Exception message should contain the account ID");
                }
        }

        @DisplayName("Instrument Validation Tests")
        @Nested
        class InstrumentValidationTests {
                @DisplayName("Non-existent instrument throws InstrumentNotFoundException")
                @Test
                void testInstrumentNotFoundThrowsException() {
                        PlaceOrderRequest requestWithInvalidInstrument = new PlaceOrderRequest(
                                        1L,
                                        "INVALID", // Non-existent symbol
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "ORDER-006");

                        InstrumentNotFoundException exception = assertThrows(InstrumentNotFoundException.class,
                                        () -> validator.validate(requestWithInvalidInstrument),
                                        "Should throw InstrumentNotFoundException for non-existent instrument");

                        assertTrue(exception.getMessage().contains("INVALID"),
                                        "Exception message should contain the symbol");
                }

                @DisplayName("Non-tradable instrument throws InstrumentNotFoundException")
                @Test
                void testNonTradableInstrumentThrowsException() {
                        // Create a non-tradable instrument
                        Instrument nonTradable = new Instrument("MSFT", "MICROSOFT", "USD", "EQUITY", false);
                        instrumentsMap.put("MSFT", nonTradable);

                        PlaceOrderRequest requestWithNonTradable = new PlaceOrderRequest(
                                        1L,
                                        "MSFT",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("300.00"),
                                        "ORDER-007");

                        InstrumentNotFoundException exception = assertThrows(InstrumentNotFoundException.class,
                                        () -> validator.validate(requestWithNonTradable),
                                        "Should throw InstrumentNotFoundException for non-tradable instrument");

                        assertTrue(exception.getMessage().contains("MSFT"),
                                        "Exception message should contain the symbol");
                }
        }
}
