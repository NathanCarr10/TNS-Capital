package com.neueda.leap.controllers;

import com.neueda.leap.security.AccountAccess;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.ClockTest;

/**
 * Unit tests for REST layer validation in OrderController.
 * 
 * Tests verify that:
 * 1. Invalid account returns 404 without publishing to Kafka
 * 2. Invalid instrument returns 404 without publishing to Kafka
 * 3. Valid orders are published to Kafka
 */
@DisplayName("OrderController REST Validation Tests")
class OrderControllerValidationTest {
    private OrderController orderController;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private AccountAccess accountAccess;

    // These tests cover validation, not ownership, so they run as an admin
    private final Authentication admin = new TestingAuthenticationToken("admin", "n/a", "ROLE_ADMIN");

    private PlaceOrderRequest validRequest;
    private Long accountId = 1L;
    private String symbol = "AAPL";
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true);

        MockitoAnnotations.openMocks(this);

        orderController = new OrderController(
                orderRepository,
                accountRepository,
                instrumentRepository,
                orderHistoryRepository,
                orderService,
                orderEventPublisher,
                accountAccess);
        when(accountAccess.isAdmin(admin)).thenReturn(true);

        validRequest = new PlaceOrderRequest(
                accountId,
                symbol,
                OrderSide.BUY,
                100,
                new BigDecimal("150.00"),
                UUID.randomUUID().toString());
    }

    @DisplayName("placeOrder - REST Validation Tests")
    @Nested
    class RestValidationTests {

        @Test
        @DisplayName("Should reject order with 404 if account not found (fail-fast)")
        void testAccountNotFoundReturn404() {
            // Arrange
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            // Act & Assert
            AccountNotFoundException exception = assertThrows(
                    AccountNotFoundException.class,
                    () -> orderController.placeOrder(validRequest, admin));

            assertTrue(exception.getMessage().contains("Account not found"));

            // Verify Kafka was NOT called
            verify(orderEventPublisher, never()).publishEvent(any(), any());
        }

        @Test
        @DisplayName("Should reject order with 404 if instrument not found (fail-fast)")
        void testInstrumentNotFoundReturn404() {
            // Arrange
            Account testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"),
                    new ClockTest());
            testAccount.setId(accountId);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

            // Act & Assert
            InstrumentNotFoundException exception = assertThrows(
                    InstrumentNotFoundException.class,
                    () -> orderController.placeOrder(validRequest, admin));

            assertTrue(exception.getMessage().contains("Instrument not found"));

            // Verify Kafka was NOT called
            verify(orderEventPublisher, never()).publishEvent(any(), any());
        }

        @Test
        @DisplayName("Should accept order with 202 if account and instrument exist")
        void testValidOrderReturn202Accepted() {
            // Arrange
            Account testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"),
                    new ClockTest());
            testAccount.setId(accountId);

            Instrument testInstrument = new Instrument(instrument);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.of(testInstrument));

            // Act
            ResponseEntity<java.util.Map<String, Object>> response = orderController.placeOrder(validRequest, admin);

            // Assert
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("ACCEPTED", response.getBody().get("status"));
            assertTrue(response.getBody().containsKey("orderId"));

            // Verify Kafka WAS called
            verify(orderEventPublisher, times(1)).publishEvent(any(), eq(accountId));
        }

        @Test
        @DisplayName("Should return 202 even if same validation happens in async processing")
        void testDualLayerValidationDesign() {
            // This test documents the defensive validation design:
            // - REST layer validates and returns 404 immediately
            // - Async (OrderService) also validates to catch race conditions
            // - But only if account/instrument deleted between REST check and async
            // processing

            // Arrange: REST validation passes
            Account testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"),
                    new ClockTest());
            testAccount.setId(accountId);

            Instrument testInstrument = new Instrument(instrument);

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.of(testInstrument));

            // Act
            ResponseEntity<java.util.Map<String, Object>> response = orderController.placeOrder(validRequest, admin);

            // Assert
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

            // Verify order was published to Kafka
            verify(orderEventPublisher, times(1)).publishEvent(any(), eq(accountId));
        }
    }

    @DisplayName("placeOrder - Error Response Handling")
    @Nested
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should propagate AccountNotFoundException to GlobalExceptionHandler")
        void testExceptionPropagation() {
            // Arrange
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            // Act & Assert
            // The exception is thrown and should be caught by @ControllerAdvice
            // GlobalExceptionHandler
            // which returns a 404 ErrorResponse
            assertThrows(AccountNotFoundException.class, () -> orderController.placeOrder(validRequest, admin));
        }
    }
}
