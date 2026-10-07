package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.time.ClockTest;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for exception differentiation in OrderService.
 * 
 * Tests verify that:
 * 1. Not-found exceptions (AccountNotFoundException,
 * InstrumentNotFoundException)
 * are wrapped in NonRetryableOrderException
 * 2. Business logic exceptions (InsufficientFundsException) are re-thrown as-is
 * 3. Defensive validation catches missing resources
 */
@DisplayName("OrderService Exception Differentiation Tests")
class OrderServiceExceptionDifferentiationTest {
    private OrderService orderService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryRepository orderHistoryRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private OrderValidator validator;

    @Mock
    private OrderExecutionStrategy buyStrategy;

    @Mock
    private OrderExecutionStrategy sellStrategy;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ClockTest testClock;
    private UUID orderId;
    private Long accountId = 1L;
    private String symbol = "AAPL";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
        orderId = UUID.randomUUID();

        // Create strategy map
        Map<OrderSide, OrderExecutionStrategy> strategies = new EnumMap<>(OrderSide.class);
        strategies.put(OrderSide.BUY, buyStrategy);
        strategies.put(OrderSide.SELL, sellStrategy);

        orderService = new OrderService(
                accountRepository,
                instrumentRepository,
                orderRepository,
                orderHistoryRepository,
                positionRepository,
                validator,
                strategies,
                testClock,
                orderEventPublisher,
                tradeEventPublisher,
                transactionManager);
    }

    @DisplayName("processOrderEvent - Not-Found Exceptions")
    @Nested
    class NotFoundExceptionTests {

        @Test
        @DisplayName("Should wrap AccountNotFoundException in NonRetryableOrderException")
        @SuppressWarnings("null")
        void testAccountNotFoundIsNonRetryable() {
            // Arrange
            var event = new com.neueda.leap.kafka.events.OrderEvent(
                    orderId, accountId, symbol, OrderSide.BUY, 100, new BigDecimal("150.00"), Instant.now());

            // Mock: Order doesn't exist yet, so processOrderEvent will try to create it
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
            doNothing().when(validator).validate(any()); // Validation passes

            // Mock: Account not found
            when(accountRepository.findById(accountId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            NonRetryableOrderException exception = assertThrows(
                    NonRetryableOrderException.class,
                    () -> orderService.processOrderEvent(event));

            // Verify it wraps AccountNotFoundException
            assertNotNull(exception.getOriginalException());
            assertInstanceOf(AccountNotFoundException.class, exception.getOriginalException());
            assertTrue(exception.getMessage().contains("Account not found"));

            // Verify rejected order was saved (in separate transaction) with the same
            // reason that is recorded in dlq_messages.failure_reason
            verify(orderRepository, atLeastOnce()).save(argThat(order -> order.getStatus() == OrderStatus.REJECTED
                    && ("Account not found: " + accountId).equals(order.getStatusReason())));
        }

        @Test
        @DisplayName("Should wrap InstrumentNotFoundException in NonRetryableOrderException")
        @SuppressWarnings("null")
        void testInstrumentNotFoundIsNonRetryable() {
            // Arrange
            var event = new com.neueda.leap.kafka.events.OrderEvent(
                    orderId, accountId, symbol, OrderSide.BUY, 100, new BigDecimal("150.00"), Instant.now());

            Account testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);
            testAccount.setId(accountId);

            // Mock: Order doesn't exist yet
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
            doNothing().when(validator).validate(any());

            // Mock: Account exists
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));

            // Mock: Instrument not found (defensive check)
            when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.empty());

            // Act & Assert
            NonRetryableOrderException exception = assertThrows(
                    NonRetryableOrderException.class,
                    () -> orderService.processOrderEvent(event));

            // Verify it wraps InstrumentNotFoundException
            assertNotNull(exception.getOriginalException());
            assertInstanceOf(InstrumentNotFoundException.class, exception.getOriginalException());
            assertTrue(exception.getMessage().contains("Instrument not found"));

            // Verify rejected order was saved
            verify(orderRepository, atLeastOnce()).save(argThat(order -> order.getStatus() == OrderStatus.REJECTED));
        }
    }

    @DisplayName("processOrderEvent - Business Logic Exceptions")
    @Nested
    class BusinessLogicExceptionTests {

        @Test
        @DisplayName("Should re-throw InsufficientFundsException without wrapping")
        @SuppressWarnings("null")
        void testInsufficientFundsIsRetryable() {
            // Arrange
            var event = new com.neueda.leap.kafka.events.OrderEvent(
                    orderId, accountId, symbol, OrderSide.BUY, 100, new BigDecimal("150.00"), Instant.now());

            Account testAccount = new Account("ACC001", "John Doe", new BigDecimal("100.00"), testClock);
            testAccount.setId(accountId);

            Instrument testInstrument = new Instrument(symbol, "Test Instrument", "USD", "EQUITY", true);

            // Mock: Order doesn't exist yet
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
            doNothing().when(validator).validate(any());

            // Mock: Account and Instrument exist
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol(symbol)).thenReturn(Optional.of(testInstrument));

            // Mock: Strategy throws InsufficientFundsException (business logic error)
            doThrow(new InsufficientFundsException("Insufficient balance for order"))
                    .when(buyStrategy).execute(any(), any(), any());

            // Act & Assert
            InsufficientFundsException exception = assertThrows(
                    InsufficientFundsException.class,
                    () -> orderService.processOrderEvent(event));

            // Verify it's NOT wrapped in NonRetryableOrderException
            assertEquals("Insufficient balance for order", exception.getMessage());

            // Verify rejected order was saved (because business logic validation failed)
            verify(orderRepository, atLeastOnce()).save(argThat(order -> order.getStatus() == OrderStatus.REJECTED));
        }
    }

    @DisplayName("processOrderEvent - Race Condition Handling")
    @Nested
    class RaceConditionTests {

        @Test
        @DisplayName("Should handle account deleted between REST validation and async processing")
        @SuppressWarnings("null")
        void testAccountDeletedAfterRestValidation() {
            // This test verifies the defensive validation layer catches the race condition

            // Arrange
            var event = new com.neueda.leap.kafka.events.OrderEvent(
                    orderId, accountId, symbol, OrderSide.BUY, 100, new BigDecimal("150.00"), Instant.now());

            // Mock: Order doesn't exist yet
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
            doNothing().when(validator).validate(any());

            // Mock: Account was deleted between REST validation and async processing
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            // Act & Assert
            NonRetryableOrderException exception = assertThrows(
                    NonRetryableOrderException.class,
                    () -> orderService.processOrderEvent(event));

            // Verify it's marked as non-retryable
            assertInstanceOf(AccountNotFoundException.class, exception.getOriginalException());

            // Verify order was still rejected and persisted
            verify(orderRepository, atLeastOnce()).save(argThat(order -> order.getStatus() == OrderStatus.REJECTED));
        }
    }

    @DisplayName("processOrderEvent - Idempotency")
    @Nested
    class IdempotencyTests {

        @Test
        @DisplayName("Should return existing order if already processed (idempotency check)")
        @SuppressWarnings("null")
        void testIdempotencyCheck() {
            // Arrange
            var event = new com.neueda.leap.kafka.events.OrderEvent(
                    orderId, accountId, symbol, OrderSide.BUY, 100, new BigDecimal("150.00"), Instant.now());

            Order existingOrder = new Order(accountId, symbol, OrderSide.BUY, 100,
                    new BigDecimal("150.00"), orderId.toString(), testClock);
            existingOrder.setId(orderId);
            existingOrder.setStatus(OrderStatus.FILLED);

            // Mock: Order already exists
            when(orderRepository.findById(any(UUID.class))).thenReturn(Optional.of(existingOrder));

            // Act
            Order result = orderService.processOrderEvent(event);

            // Assert
            assertNotNull(result);
            assertEquals(orderId, result.getId());
            assertEquals(OrderStatus.FILLED, result.getStatus());

            // Verify no additional processing occurred
            verify(accountRepository, never()).findById(anyLong());
            verify(instrumentRepository, never()).findBySymbol(any());
        }
    }
}
