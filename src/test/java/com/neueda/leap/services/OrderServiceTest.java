package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
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
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.InstrumentNotTradableException;
import com.neueda.leap.exceptions.OrderSubmissionException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.OrderHistoryRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.time.ClockTest;
import com.neueda.leap.kafka.OrderEventPublisher;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.OrderEvent;

@DisplayName("OrderService Test Suite")
class OrderServiceTest {
    private OrderService orderService;

    @Mock
    private AccountRepository accountRepository;

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
        Map<OrderSide, OrderExecutionStrategy> strategies = new EnumMap<>(OrderSide.class);
        strategies.put(OrderSide.BUY, buyStrategy);
        strategies.put(OrderSide.SELL, sellStrategy);

        orderService = new OrderService(accountRepository, orderRepository, orderHistoryRepository,
                positionRepository, validator, strategies, testClock, orderEventPublisher, tradeEventPublisher);

        placeOrderRequest = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"),
                "ORDER-001");
    }

    private OrderEvent buyEvent(UUID orderId, String key) {
        return new OrderEvent(orderId, 1L, "aapl", OrderSide.BUY, 100, new BigDecimal("150.00"),
                testClock.now(), key);
    }

    @DisplayName("submitOrder Tests")
    @Nested
    class SubmitOrderTests {
        @DisplayName("Should validate, then publish an event carrying the client's idempotency key")
        @Test
        void testSubmitOrderPublishesEventWithKey() {
            PlaceOrderRequest request = new PlaceOrderRequest(1L, "aapl", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "client-key-1");

            UUID orderId = orderService.submitOrder(request);

            verify(validator).validate(request);
            ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
            verify(orderEventPublisher).publishEvent(eventCaptor.capture(), eq(1L));
            OrderEvent event = eventCaptor.getValue();
            assertEquals(orderId, event.orderId());
            assertEquals("CLIENT-KEY-1", event.idempotencyKey());
            assertEquals("AAPL", event.symbol());
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should give a repeated request the same order ID, so it cannot become a second order")
        @Test
        void testSubmitOrderSameKeySameOrderId() {
            PlaceOrderRequest request = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "client-key-1");
            PlaceOrderRequest otherAccount = new PlaceOrderRequest(2L, "AAPL", OrderSide.BUY, 100,
                    new BigDecimal("150.00"), "client-key-1");

            UUID first = orderService.submitOrder(request);
            UUID second = orderService.submitOrder(request);

            assertEquals(first, second);
            assertNotEquals(first, orderService.submitOrder(otherAccount));
        }

        @DisplayName("Should not publish when validation fails")
        @Test
        void testSubmitOrderValidationFailureDoesNotPublish() {
            doThrow(new DuplicateOrderException("Order already submitted")).when(validator).validate(any());

            assertThrows(DuplicateOrderException.class, () -> orderService.submitOrder(placeOrderRequest));

            verify(orderEventPublisher, never()).publishEvent(any(), any());
        }

        @DisplayName("Should propagate a failed publish so the API does not report the order as accepted")
        @Test
        void testSubmitOrderPublishFailurePropagates() {
            doThrow(new OrderSubmissionException("broker down", new RuntimeException()))
                    .when(orderEventPublisher).publishEvent(any(), any());

            assertThrows(OrderSubmissionException.class, () -> orderService.submitOrder(placeOrderRequest));
        }

        @DisplayName("Should throw NullPointerException for null request")
        @Test
        void testSubmitOrderNullRequest() {
            assertThrows(NullPointerException.class, () -> orderService.submitOrder(null));
            verify(validator, never()).validate(any());
        }
    }

    @DisplayName("processOrderEvent Tests")
    @Nested
    class ProcessOrderEventTests {
        @DisplayName("Should fill a buy order, store it under the client's key and publish a FILLED event")
        @Test
        void testProcessBuyFilled() {
            UUID orderId = UUID.randomUUID();
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Order result = orderService.processOrderEvent(buyEvent(orderId, "client-key-1"));

            assertEquals(OrderStatus.FILLED, result.getStatus());
            assertEquals(orderId, result.getId());
            assertEquals("CLIENT-KEY-1", result.getIdempotencyKey());
            assertEquals("AAPL", result.getSymbol());
            verify(buyStrategy).execute(eq(testAccount), any(PlaceOrderRequest.class), eq("AAPL"));
            verify(orderRepository).save(result);
            verify(tradeEventPublisher).publish(result, OrderStatus.NEW, null);
        }

        @DisplayName("Should fill a sell order with the sell strategy")
        @Test
        void testProcessSellFilled() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            OrderEvent sell = new OrderEvent(UUID.randomUUID(), 1L, "MSFT", OrderSide.SELL, 50,
                    new BigDecimal("300.00"), testClock.now(), "sell-key");

            Order result = orderService.processOrderEvent(sell);

            assertEquals(OrderStatus.FILLED, result.getStatus());
            verify(sellStrategy).execute(eq(testAccount), any(PlaceOrderRequest.class), eq("MSFT"));
            verify(buyStrategy, never()).execute(any(), any(), any());
        }

        @DisplayName("Should save a REJECTED order with the reason, without rethrowing, on insufficient funds")
        @Test
        void testProcessInsufficientFundsRejected() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new InsufficientFundsException("Insufficient funds for buy order"))
                    .when(buyStrategy).execute(any(), any(), any());

            Order result = assertDoesNotThrow(() -> orderService.processOrderEvent(buyEvent(UUID.randomUUID(), "k1")));

            assertEquals(OrderStatus.REJECTED, result.getStatus());
            assertEquals("Insufficient funds for buy order", result.getStatusReason());
            verify(orderRepository).save(result);
            verify(tradeEventPublisher).publish(result, OrderStatus.NEW, "Insufficient funds for buy order");
        }

        @DisplayName("Should save a REJECTED order on insufficient holdings")
        @Test
        void testProcessInsufficientHoldingsRejected() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new InsufficientHoldingsException("Insufficient holdings for symbol: MSFT"))
                    .when(sellStrategy).execute(any(), any(), any());
            OrderEvent sell = new OrderEvent(UUID.randomUUID(), 1L, "MSFT", OrderSide.SELL, 50,
                    new BigDecimal("300.00"), testClock.now(), "sell-key");

            Order result = orderService.processOrderEvent(sell);

            assertEquals(OrderStatus.REJECTED, result.getStatus());
            verify(orderRepository).save(result);
        }

        @DisplayName("Should save a REJECTED order when the account is not active")
        @Test
        void testProcessAccountNotActiveRejected() {
            doThrow(new AccountNotActiveException("Account not active: 1")).when(validator).validate(any());

            Order result = orderService.processOrderEvent(buyEvent(UUID.randomUUID(), "k2"));

            assertEquals(OrderStatus.REJECTED, result.getStatus());
            verify(buyStrategy, never()).execute(any(), any(), any());
            verify(orderRepository).save(result);
        }

        @DisplayName("Should save a REJECTED order when the instrument is not tradable")
        @Test
        void testProcessInstrumentNotTradableRejected() {
            doThrow(new InstrumentNotTradableException("Instrument not tradable: AAPL"))
                    .when(validator).validate(any());

            Order result = orderService.processOrderEvent(buyEvent(UUID.randomUUID(), "k3"));

            assertEquals(OrderStatus.REJECTED, result.getStatus());
            verify(orderRepository).save(result);
        }

        @DisplayName("Should rethrow for an unknown account, since the order row cannot reference it")
        @Test
        void testProcessUnknownAccountRethrows() {
            doThrow(new AccountNotFoundException("Account not found: 1")).when(validator).validate(any());

            OrderEvent event = buyEvent(UUID.randomUUID(), "k4");

            assertThrows(AccountNotFoundException.class, () -> orderService.processOrderEvent(event));

            verify(orderRepository, never()).save(any());
            verify(tradeEventPublisher, never()).publish(any(), any(), any());
        }

        @DisplayName("Should rethrow for an unknown instrument, since the order row cannot reference it")
        @Test
        void testProcessUnknownInstrumentRethrows() {
            doThrow(new InstrumentNotFoundException("Instrument not found: AAPL")).when(validator).validate(any());

            OrderEvent event = buyEvent(UUID.randomUUID(), "k5");

            assertThrows(InstrumentNotFoundException.class, () -> orderService.processOrderEvent(event));

            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should rethrow unexpected errors so the listener retries them")
        @Test
        void testProcessUnexpectedErrorRethrows() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            doThrow(new IllegalStateException("Position update failed"))
                    .when(buyStrategy).execute(any(), any(), any());

            OrderEvent event = buyEvent(UUID.randomUUID(), "k6");

            assertThrows(IllegalStateException.class, () -> orderService.processOrderEvent(event));

            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should return the stored order for a redelivered event without executing again")
        @Test
        void testProcessRedeliveredEvent() {
            UUID orderId = UUID.randomUUID();
            Order stored = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "K7", testClock);
            when(orderRepository.findById(orderId)).thenReturn(Optional.of(stored));

            Order result = orderService.processOrderEvent(buyEvent(orderId, "k7"));

            assertSame(stored, result);
            verify(validator, never()).validate(any());
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should not execute a second order with an idempotency key that is already used")
        @Test
        void testProcessDuplicateKeyNotExecuted() {
            Order first = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "K8", testClock);
            when(orderRepository.findByIdempotencyKey("K8")).thenReturn(Optional.of(first));

            Order result = orderService.processOrderEvent(buyEvent(UUID.randomUUID(), "k8"));

            assertSame(first, result);
            verify(buyStrategy, never()).execute(any(), any(), any());
            verify(orderRepository, never()).save(any());
        }

        @DisplayName("Should fall back to the order ID as the key for events captured before keys were added")
        @Test
        void testProcessEventWithoutKeyUsesOrderId() {
            UUID orderId = UUID.randomUUID();
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

            Order result = orderService.processOrderEvent(buyEvent(orderId, null));

            assertEquals(orderId.toString().toUpperCase(), result.getIdempotencyKey());
        }

        @DisplayName("Should throw IllegalStateException when no strategy is registered for the side")
        @Test
        void testProcessStrategyNotFound() {
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            OrderService serviceWithoutStrategies = new OrderService(accountRepository, orderRepository,
                    orderHistoryRepository, positionRepository, validator, new EnumMap<>(OrderSide.class), testClock,
                    orderEventPublisher, tradeEventPublisher);

            OrderEvent event = buyEvent(UUID.randomUUID(), "k9");

            assertThrows(IllegalStateException.class, () -> serviceWithoutStrategies.processOrderEvent(event));
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

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

            Order result = orderService.cancelOrder(orderId);

            assertNotNull(result);
            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            verify(orderRepository, times(1)).findById(orderId);
            verify(orderRepository, times(1)).save(order);
        }

        @DisplayName("Should throw OrderNotFoundException when order not found")
        @Test
        @SuppressWarnings("null")
        void testCancelOrderNotFound() {
            UUID orderId = UUID.randomUUID();
            when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

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

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

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

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

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

            when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

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
}
