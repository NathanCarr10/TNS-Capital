package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.time.ClockTest;

@DisplayName("OrderService Test Suite")
class OrderServiceTest {
    private OrderService orderService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private OrderValidator validator;

    @Mock
    private OrderExecutionStrategy buyStrategy;

    @Mock
    private OrderExecutionStrategy sellStrategy;

    private ClockTest testClock;
    private Account testAccount;
    private PlaceOrderRequest placeOrderRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
        testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);
        testAccount.setId(1L);

        // Create strategy map
        Map<OrderSide, OrderExecutionStrategy> strategies = new HashMap<>();
        strategies.put(OrderSide.BUY, buyStrategy);
        strategies.put(OrderSide.SELL, sellStrategy);

        orderService = new OrderService(accountRepository, orderRepository, positionRepository, validator,
                strategies, testClock);

        placeOrderRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"),
                "ORDER-001");
    }

    @DisplayName("placeOrder Tests")
    @Nested
    class PlaceOrderTests {
        @DisplayName("Should place buy order successfully")
        @Test
        void testPlaceOrderBuySuccess() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Order result = orderService.placeOrder(placeOrderRequest);

            assertNotNull(result);
            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(1L, result.getAccountId());
            assertEquals("AAPL", result.getSymbol());
            assertEquals(OrderSide.BUY, result.getSide());
            assertEquals(100, result.getQuantity());
            assertEquals(new BigDecimal("150.00"), result.getPrice());

            verify(validator, times(1)).validate(placeOrderRequest);
            verify(accountRepository, times(1)).findById(1L);
            verify(buyStrategy, times(1)).execute(testAccount, placeOrderRequest, "AAPL");
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @DisplayName("Should place sell order successfully")
        @Test
        void testPlaceOrderSellSuccess() {
            PlaceOrderRequest sellRequest = new PlaceOrderRequest(1L, "MSFT", OrderSide.SELL, 50,
                    new BigDecimal("300.00"), "ORDER-002");
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Order result = orderService.placeOrder(sellRequest);

            assertNotNull(result);
            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(OrderSide.SELL, result.getSide());

            verify(validator, times(1)).validate(sellRequest);
            verify(sellStrategy, times(1)).execute(testAccount, sellRequest, "MSFT");
            verify(orderRepository, times(1)).save(any(Order.class));
        }

        @DisplayName("Should throw NullPointerException for null request")
        @Test
        void testPlaceOrderNullRequest() {
            assertThrows(NullPointerException.class, () -> orderService.placeOrder(null),
                    "Should throw NullPointerException for null request");
            verify(validator, never()).validate(any());
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should set order status to REJECTED on InsufficientFundsException")
        @Test
        void testPlaceOrderInsufficientFundsThrowsException() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new InsufficientFundsException("Insufficient funds")).when(buyStrategy).execute(any(), any(),
                    any());

            assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(placeOrderRequest),
                    "Should propagate InsufficientFundsException");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().getStatus());
        }

        @DisplayName("Should set order status to REJECTED on InsufficientHoldingsException")
        @Test
        void testPlaceOrderInsufficientHoldingsThrowsException() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new InsufficientHoldingsException("Insufficient holdings")).when(sellStrategy).execute(any(),
                    any(), any());

            PlaceOrderRequest sellRequest = new PlaceOrderRequest(1L, "GOOG", OrderSide.SELL, 100,
                    new BigDecimal("130.00"), "ORDER-003");

            assertThrows(InsufficientHoldingsException.class, () -> orderService.placeOrder(sellRequest),
                    "Should propagate InsufficientHoldingsException");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().getStatus());
        }

        @DisplayName("Should set order status to REJECTED on generic Exception")
        @Test
        void testPlaceOrderGenericExceptionThrowsException() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new RuntimeException("Unexpected error")).when(buyStrategy).execute(any(), any(), any());

            assertThrows(IllegalStateException.class, () -> orderService.placeOrder(placeOrderRequest),
                    "Should throw IllegalStateException for unexpected errors");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().getStatus());
        }

        @DisplayName("Should wrap AccountNotFoundException in IllegalStateException")
        @Test
        void testPlaceOrderAccountNotFound() {
            when(accountRepository.findById(999L)).thenReturn(Optional.empty());
            PlaceOrderRequest invalidRequest = new PlaceOrderRequest(999L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "ORDER-004");

            assertThrows(IllegalStateException.class, () -> orderService.placeOrder(invalidRequest),
                    "Should wrap exception in IllegalStateException");

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(orderCaptor.capture());
            assertEquals(OrderStatus.REJECTED, orderCaptor.getValue().getStatus());
        }

        @DisplayName("Should throw IllegalStateException when strategy not found")
        @Test
        void testPlaceOrderStrategyNotFound() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            // Create service with empty strategies map
            Map<OrderSide, OrderExecutionStrategy> emptyStrategies = new HashMap<>();
            OrderService serviceWithoutStrategies = new OrderService(accountRepository, orderRepository,
                    positionRepository, validator, emptyStrategies, testClock);

            assertThrows(IllegalStateException.class,
                    () -> serviceWithoutStrategies.placeOrder(placeOrderRequest),
                    "Should throw IllegalStateException when strategy not found");
        }

        @DisplayName("Should save order even on exception")
        @Test
        void testPlaceOrderSavesOrderOnException() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new InsufficientFundsException("Insufficient funds")).when(buyStrategy).execute(any(), any(),
                    any());

            assertThrows(InsufficientFundsException.class, () -> orderService.placeOrder(placeOrderRequest));

            verify(orderRepository, times(1)).save(any(Order.class));
        }
    }

    @DisplayName("findByIdempotencyKey Tests")
    @Nested
    class FindByIdempotencyKeyTests {
        @DisplayName("Should find order by idempotency key")
        @Test
        void testFindByIdempotencyKeySuccess() {
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-001", testClock);

            when(orderRepository.findByIdempotencyKey("ORDER-001")).thenReturn(Optional.of(order));

            Optional<Order> result = orderService.findByIdempotencyKey("ORDER-001");

            assertTrue(result.isPresent());
            assertEquals("ORDER-001", result.get().getIdempotencyKey());
            verify(orderRepository, times(1)).findByIdempotencyKey("ORDER-001");
        }

        @DisplayName("Should return empty Optional when order not found")
        @Test
        void testFindByIdempotencyKeyNotFound() {
            when(orderRepository.findByIdempotencyKey("NONEXISTENT")).thenReturn(Optional.empty());

            Optional<Order> result = orderService.findByIdempotencyKey("NONEXISTENT");

            assertFalse(result.isPresent());
            verify(orderRepository, times(1)).findByIdempotencyKey("NONEXISTENT");
        }

        @DisplayName("Should handle null key gracefully")
        @Test
        void testFindByIdempotencyKeyNullKey() {
            when(orderRepository.findByIdempotencyKey(null)).thenReturn(Optional.empty());

            Optional<Order> result = orderService.findByIdempotencyKey(null);

            assertFalse(result.isPresent());
            verify(orderRepository, times(1)).findByIdempotencyKey(null);
        }
    }

    @DisplayName("findPosition Tests")
    @Nested
    class FindPositionTests {
        @DisplayName("Should find position successfully")
        @Test
        void testFindPositionSuccess() {
            Position position = new Position(1L, "AAPL", 100, new BigDecimal("150.00"));

            when(positionRepository.findPosition(1L, "AAPL")).thenReturn(Optional.of(position));

            Optional<Position> result = orderService.findPosition(1L, "AAPL");

            assertTrue(result.isPresent());
            assertEquals("AAPL", result.get().getSymbol());
            assertEquals(100, result.get().getQuantity());
            verify(positionRepository, times(1)).findPosition(1L, "AAPL");
        }

        @DisplayName("Should return empty Optional when position not found")
        @Test
        void testFindPositionNotFound() {
            when(positionRepository.findPosition(1L, "AAPL")).thenReturn(Optional.empty());

            Optional<Position> result = orderService.findPosition(1L, "AAPL");

            assertFalse(result.isPresent());
            verify(positionRepository, times(1)).findPosition(1L, "AAPL");
        }

        @DisplayName("Should normalize symbol before searching")
        @Test
        void testFindPositionNormalizeSymbol() {
            Position position = new Position(1L, "AAPL", 100, new BigDecimal("150.00"));

            when(positionRepository.findPosition(1L, "AAPL")).thenReturn(Optional.of(position));

            // Pass lowercase symbol
            orderService.findPosition(1L, "aapl");

            // Should be normalized to uppercase
            verify(positionRepository, times(1)).findPosition(1L, "AAPL");
        }
    }

    @DisplayName("cancelOrder Tests")
    @Nested
    class CancelOrderTests {
        @DisplayName("Should cancel NEW order successfully")
        @Test
        void testCancelOrderSuccess() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-001", testClock);

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            Order result = orderService.cancelOrder(orderId);

            assertNotNull(result);
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            verify(orderRepository, times(1)).findById(orderId);
            verify(orderRepository, times(1)).save(order);
        }

        @DisplayName("Should throw OrderNotFoundException when order not found")
        @Test
        void testCancelOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderNotFoundException when order not found");
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is FILLED")
        @Test
        void testCancelOrderFilledConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-002", testClock);
            order.setStatus(OrderStatus.FILLED); // Transition from NEW to FILLED

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is FILLED");
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is REJECTED")
        @Test
        void testCancelOrderRejectedConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-003", testClock);
            order.setStatus(OrderStatus.REJECTED); // Transition from NEW to REJECTED

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is REJECTED");
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is already CANCELLED")
        @Test
        void testCancelOrderAlreadyCancelledConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-004", testClock);
            order.setStatus(OrderStatus.CANCELLED); // Transition from NEW to CANCELLED

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is already CANCELLED");
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should throw NullPointerException for null order ID")
        @Test
        void testCancelOrderNullOrderId() {
            assertThrows(NullPointerException.class, () -> orderService.cancelOrder(null),
                    "Should throw NullPointerException for null order ID");
            verify(orderRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }
    }
}
