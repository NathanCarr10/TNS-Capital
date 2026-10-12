package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.time.ClockTest;
import org.springframework.transaction.PlatformTransactionManager;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;

@DisplayName("OrderService Test Suite")
class OrderServiceTest {
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

        orderService = new OrderService(accountRepository, instrumentRepository, orderRepository,
                orderHistoryRepository, positionRepository, validator, strategies, testClock, orderEventPublisher,
                tradeEventPublisher,
                transactionManager);

        placeOrderRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"),
                "ORDER-001");
    }

    @DisplayName("submitOrder Tests")
    @Nested
    class SubmitOrderTests {
        @BeforeEach
        void accountAndInstrumentExist() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol("AAPL"))
                    .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true)));
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @DisplayName("Should store the order as NEW, then publish it")
        @Test
        void testSubmitStoresThenPublishes() {
            Order order = orderService.submitOrder(placeOrderRequest);

            assertEquals(OrderStatus.NEW, order.getStatus());
            var inOrder = inOrder(orderRepository, orderEventPublisher);
            inOrder.verify(orderRepository).saveAndFlush(order);
            inOrder.verify(orderEventPublisher).publish(order);
        }

        @DisplayName("Should fail fast for an unknown account, storing and publishing nothing")
        @Test
        void testSubmitUnknownAccount() {
            when(accountRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(AccountNotFoundException.class, () -> orderService.submitOrder(placeOrderRequest));
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher);
        }

        @DisplayName("Should fail fast for an unknown instrument, storing and publishing nothing")
        @Test
        void testSubmitUnknownInstrument() {
            when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());

            assertThrows(InstrumentNotFoundException.class, () -> orderService.submitOrder(placeOrderRequest));
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher);
        }

        @DisplayName("Should not publish an order whose idempotency key was already used")
        @Test
        void testSubmitDuplicateKey() {
            when(orderRepository.findByIdempotencyKey("ORDER-001")).thenReturn(Optional.of(
                    new Order(1L, "AAPL", OrderSide.BUY, 1, new BigDecimal("1.00"), "ORDER-001", testClock)));

            assertThrows(DuplicateOrderException.class, () -> orderService.submitOrder(placeOrderRequest));
            verifyNoInteractions(orderEventPublisher);
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

            when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(position));

            Optional<Position> result = orderService.findPosition(1L, "AAPL");

            assertTrue(result.isPresent());
            assertEquals("AAPL", result.get().getSymbol());
            assertEquals(100, result.get().getQuantity());
            verify(positionRepository, times(1)).findByAccountIdAndSymbol(1L, "AAPL");
        }

        @DisplayName("Should return empty Optional when position not found")
        @Test
        void testFindPositionNotFound() {
            when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.empty());

            Optional<Position> result = orderService.findPosition(1L, "AAPL");

            assertFalse(result.isPresent());
            verify(positionRepository, times(1)).findByAccountIdAndSymbol(1L, "AAPL");
        }

        @DisplayName("Should normalize symbol before searching")
        @Test
        void testFindPositionNormalizeSymbol() {
            Position position = new Position(1L, "AAPL", 100, new BigDecimal("150.00"));

            when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL")).thenReturn(Optional.of(position));

            // Pass lowercase symbol
            orderService.findPosition(1L, "aapl");

            // Should be normalized to uppercase
            verify(positionRepository, times(1)).findByAccountIdAndSymbol(1L, "AAPL");
        }
    }

    @DisplayName("cancelOrder Tests")
    @Nested
    class CancelOrderTests {
        @DisplayName("Should cancel NEW order successfully")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderSuccess() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-001", testClock);

            when(orderRepository.findWithLockById(orderId)).thenReturn(Optional.of(order));

            Order result = orderService.cancelOrder(orderId);

            assertNotNull(result);
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            verify(orderRepository, times(1)).findWithLockById(orderId);
            verify(orderRepository, times(1)).save(order);
        }

        @DisplayName("Should throw OrderNotFoundException when order not found")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findWithLockById(orderId)).thenReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderNotFoundException when order not found");
            verify(orderRepository, never()).save(any(Order.class));
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is FILLED")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderFilledConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-002", testClock);
            order.setStatus(OrderStatus.FILLED); // Transition from NEW to FILLED

            when(orderRepository.findWithLockById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is FILLED");
            verify(orderRepository, never()).save(any(Order.class));
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is REJECTED")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderRejectedConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-003", testClock);
            order.setStatus(OrderStatus.REJECTED); // Transition from NEW to REJECTED

            when(orderRepository.findWithLockById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is REJECTED");
            verify(orderRepository, never()).save(any(Order.class));
        }

        @DisplayName("Should throw OrderCancellationConflictException when order is already CANCELLED")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderAlreadyCancelledConflict() {
            UUID orderId = UUID.randomUUID();
            Order order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-004", testClock);
            order.setStatus(OrderStatus.CANCELLED); // Transition from NEW to CANCELLED

            when(orderRepository.findWithLockById(orderId)).thenReturn(Optional.of(order));

            assertThrows(OrderCancellationConflictException.class, () -> orderService.cancelOrder(orderId),
                    "Should throw OrderCancellationConflictException when order is already CANCELLED");
            verify(orderRepository, never()).save(any(Order.class));
        }

        @DisplayName("Should throw NullPointerException for null order ID")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderNullOrderId() {
            assertThrows(NullPointerException.class, () -> orderService.cancelOrder(null),
                    "Should throw NullPointerException for null order ID");
            verify(orderRepository, never()).findById(any());
            verify(orderRepository, never()).save(any(Order.class));
        }
    }

    @DisplayName("GetOrdersByAccountId Test Suite")
    @Nested
    class GetOrdersByAccountIdTests {
        @DisplayName("Should return list of orders for valid account")
        @Test
        void testGetOrdersByAccountIdSuccess() {
            Long accountId = 1L;
            Order order1 = new Order(accountId, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "ORDER-001",
                    testClock);
            Order order2 = new Order(accountId, "GOOGL", OrderSide.SELL, 50, new BigDecimal("200.00"), "ORDER-002",
                    testClock);
            List<Order> orders = Arrays.asList(order1, order2);

            when(orderRepository.findByAccountId(accountId)).thenReturn(orders);

            List<Order> result = orderService.getOrdersByAccountId(accountId);

            assertEquals(2, result.size(), "Should return 2 orders");
            assertEquals("AAPL", result.get(0).getSymbol(), "First order symbol should be AAPL");
            assertEquals("GOOGL", result.get(1).getSymbol(), "Second order symbol should be GOOGL");
            verify(orderRepository, times(1)).findByAccountId(accountId);
        }

        @DisplayName("Should return empty list when account has no orders")
        @Test
        void testGetOrdersByAccountIdEmptyList() {
            Long accountId = 2L;
            List<Order> emptyOrders = Collections.emptyList();

            when(orderRepository.findByAccountId(accountId)).thenReturn(emptyOrders);

            List<Order> result = orderService.getOrdersByAccountId(accountId);

            assertTrue(result.isEmpty(), "Should return empty list");
            verify(orderRepository, times(1)).findByAccountId(accountId);
        }

        @DisplayName("Should throw NullPointerException for null account ID")
        @Test
        void testGetOrdersByAccountIdNullAccountId() {
            assertThrows(NullPointerException.class, () -> orderService.getOrdersByAccountId(null),
                    "Should throw NullPointerException for null account ID");
            verify(orderRepository, never()).findByAccountId(any());
        }
    }

    @DisplayName("acceptOrder Tests")
    @Nested
    class AcceptOrderTests {
        @DisplayName("Should store the order as NEW with the client's idempotency key")
        @Test
        void testAcceptOrderStoresNewOrder() {
            when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Order order = orderService.acceptOrder(placeOrderRequest);

            assertEquals(OrderStatus.NEW, order.getStatus());
            assertEquals("ORDER-001", order.getIdempotencyKey());
            assertEquals(1L, order.getAccountId());
            verify(orderRepository).saveAndFlush(order);
        }

        @DisplayName("Should reject a reused idempotency key")
        @Test
        void testAcceptOrderDuplicateKey() {
            Order earlier = new Order(1L, "AAPL", OrderSide.BUY, 1, new BigDecimal("1.00"), "ORDER-001", testClock);
            when(orderRepository.findByIdempotencyKey("ORDER-001")).thenReturn(Optional.of(earlier));

            assertThrows(DuplicateOrderException.class, () -> orderService.acceptOrder(placeOrderRequest));
            verify(orderRepository, never()).saveAndFlush(any(Order.class));
        }
    }

    @DisplayName("processOrderEvent with a stored NEW order")
    @Nested
    class StoredOrderProcessingTests {
        private Order storedOrder;
        private OrderEvent event;

        @BeforeEach
        void storeNewOrder() {
            storedOrder = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "CLIENT-KEY", testClock);
            event = new OrderEvent(storedOrder.getId(), 1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"),
                    storedOrder.getCreatedOn(), "CLIENT-KEY");
            when(orderRepository.findById(storedOrder.getId())).thenReturn(Optional.of(storedOrder));
            when(orderRepository.findWithLockById(storedOrder.getId())).thenReturn(Optional.of(storedOrder));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol("AAPL"))
                    .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true)));
        }

        @DisplayName("Should fill the stored order, keeping the client's key")
        @Test
        void testFillsStoredOrder() {
            Order result = orderService.processOrderEvent(event);

            assertSame(storedOrder, result);
            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals("CLIENT-KEY", result.getIdempotencyKey());
            verify(buyStrategy).execute(eq(testAccount), any(PlaceOrderRequest.class), eq("AAPL"));
            verify(tradeEventPublisher).publish(storedOrder, OrderStatus.NEW, null);
        }

        @DisplayName("Should mark the stored order REJECTED when execution fails")
        @Test
        void testRejectsStoredOrder() {
            doThrow(new InsufficientFundsException("Insufficient funds for buy order"))
                    .when(buyStrategy).execute(any(), any(), any());

            assertThrows(InsufficientFundsException.class, () -> orderService.processOrderEvent(event));

            assertEquals(OrderStatus.REJECTED, storedOrder.getStatus());
            verify(orderRepository).save(storedOrder);
            verify(tradeEventPublisher).publish(storedOrder, OrderStatus.NEW, "Insufficient funds for buy order");
        }

        @DisplayName("Should not execute an order cancelled while it waited")
        @Test
        void testSkipsCancelledOrder() {
            // Cancelled between the first lookup and the locked one
            Order cancelled = new Order(storedOrder, testClock);
            cancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findWithLockById(storedOrder.getId())).thenReturn(Optional.of(cancelled));

            orderService.processOrderEvent(event);

            verify(buyStrategy, never()).execute(any(), any(), any());
            verify(tradeEventPublisher, never()).publish(any(), any(), any());
        }
    }
}
