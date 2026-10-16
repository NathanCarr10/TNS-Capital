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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.AccountStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.AccountNotActiveException;
import com.neueda.leap.exceptions.AccountNotFoundException;
import com.neueda.leap.exceptions.DuplicateOrderException;
import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.exceptions.LimitNotMarketableException;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.exceptions.OrderCancellationConflictException;
import com.neueda.leap.exceptions.OrderNotFoundException;
import com.neueda.leap.exceptions.OrderPublishException;
import com.neueda.leap.exceptions.PriceUnavailableException;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.exceptions.BrokerUnavailableException;
import com.neueda.leap.exceptions.SettlementException;
import com.neueda.leap.kafka.events.ExecutionEvent;
import com.neueda.leap.model.Execution;
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
import com.neueda.leap.time.FixedClock;
import org.springframework.transaction.PlatformTransactionManager;
import com.neueda.leap.execution.BrokerSimulatorClient;
import com.neueda.leap.repositories.ExecutionRepository;
import com.neueda.leap.marketdata.PriceProvider;
import com.neueda.leap.marketdata.PriceQuote;
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

    @Mock
    private BrokerSimulatorClient brokerClient;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private PriceProvider priceProvider;

    private FixedClock testClock;
    private Account testAccount;
    private PlaceOrderRequest placeOrderRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testClock = new FixedClock(Instant.parse("2026-09-17T10:00:00Z"));
        testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);
        testAccount.setId(1L);

        // Create strategy map
        Map<OrderSide, OrderExecutionStrategy> strategies = new EnumMap<>(OrderSide.class);
        strategies.put(OrderSide.BUY, buyStrategy);
        strategies.put(OrderSide.SELL, sellStrategy);

        orderService = new OrderService(accountRepository, instrumentRepository, orderRepository,
                orderHistoryRepository, positionRepository, validator, strategies, testClock, orderEventPublisher,
                tradeEventPublisher,
                transactionManager,
                brokerClient,
                executionRepository,
                priceProvider);

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

        @DisplayName("Should fail fast for an account that is not ACTIVE, storing and publishing nothing")
        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = { "SUSPENDED", "CLOSED" })
        void testSubmitInactiveAccount(AccountStatus status) {
            testAccount.setStatus(status);

            assertThrows(AccountNotActiveException.class, () -> orderService.submitOrder(placeOrderRequest));
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher, priceProvider);
        }

        @DisplayName("Should fail fast for an instrument that is not tradable, storing and publishing nothing")
        @Test
        void testSubmitUntradableInstrument() {
            when(instrumentRepository.findBySymbol("AAPL"))
                    .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", false)));
            PlaceOrderRequest noPrice = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, null, "ORDER-001");

            InstrumentNotFoundException ex = assertThrows(InstrumentNotFoundException.class,
                    () -> orderService.submitOrder(noPrice));
            assertTrue(ex.getMessage().contains("not tradable"));
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher, priceProvider);
        }

        @DisplayName("Should price an order without a price at the live price, rounded to the cent")
        @Test
        void testSubmitWithoutPriceUsesLivePrice() {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.of(
                    new PriceQuote("AAPL", new BigDecimal("227.4651"), Instant.parse("2026-10-12T14:30:00Z"))));
            PlaceOrderRequest noPrice = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, null, "ORDER-001");

            Order order = orderService.submitOrder(noPrice);

            assertEquals(new BigDecimal("227.47"), order.getPrice());
            verify(orderEventPublisher).publish(order);
        }

        @DisplayName("Should keep the price given in the request when there is no live price")
        @Test
        void testSubmitWithPriceKeepsIt() {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.empty());

            assertEquals(new BigDecimal("150.00"), orderService.submitOrder(placeOrderRequest).getPrice());
        }

        @DisplayName("Should accept a limit that would fill at the live price, keeping the limit")
        @ParameterizedTest(name = "{0} limit 150.00, market {1}")
        @CsvSource({ "BUY, 150.009", "BUY, 120.00", "SELL, 149.991", "SELL, 180.00" })
        void testSubmitMarketableLimit(OrderSide side, String market) {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.of(
                    new PriceQuote("AAPL", new BigDecimal(market), Instant.parse("2026-10-16T14:30:00Z"))));
            PlaceOrderRequest limit = new PlaceOrderRequest(1L, "AAPL", side, 100, new BigDecimal("150.00"),
                    "ORDER-001");

            Order order = orderService.submitOrder(limit);

            assertEquals(new BigDecimal("150.00"), order.getPrice());
            verify(orderEventPublisher).publish(order);
        }

        @DisplayName("Should accept a limit at the live price rounded to the cent in the trader's favour")
        @ParameterizedTest(name = "{0} limit {1}, market 227.4651")
        @CsvSource({ "BUY, 227.46", "BUY, 227.47", "SELL, 227.47", "SELL, 227.46" })
        void testSubmitLimitAtRoundedMarket(OrderSide side, String limit) {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.of(
                    new PriceQuote("AAPL", new BigDecimal("227.4651"), Instant.parse("2026-10-16T14:30:00Z"))));

            Order order = orderService.submitOrder(
                    new PlaceOrderRequest(1L, "AAPL", side, 100, new BigDecimal(limit), "ORDER-001"));

            assertEquals(new BigDecimal(limit), order.getPrice());
        }

        @DisplayName("Should refuse a limit that would not fill at the live price, storing nothing (ORD-422)")
        @ParameterizedTest(name = "{0} limit {1}, market 227.4651")
        @CsvSource({ "BUY, 1.00, 227.46", "BUY, 227.45, 227.46", "SELL, 10000.00, 227.47", "SELL, 227.48, 227.47" })
        void testSubmitLimitNotMarketable(OrderSide side, String limit, String bound) {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.of(
                    new PriceQuote("AAPL", new BigDecimal("227.4651"), Instant.parse("2026-10-16T14:30:00Z"))));
            PlaceOrderRequest request = new PlaceOrderRequest(1L, "AAPL", side, 100, new BigDecimal(limit),
                    "ORDER-001");

            LimitNotMarketableException ex = assertThrows(LimitNotMarketableException.class,
                    () -> orderService.submitOrder(request));
            assertEquals("ORD-422", ex.getErrorCode());
            assertTrue(ex.getMessage().contains("(" + bound + ")"), ex.getMessage());
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher);
        }

        @DisplayName("Should reject an order without a price when no live price is available, storing nothing")
        @Test
        void testSubmitWithoutPriceAndNoLivePrice() {
            when(priceProvider.latestPrice("AAPL")).thenReturn(Optional.empty());
            PlaceOrderRequest noPrice = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, null, "ORDER-001");

            assertThrows(PriceUnavailableException.class, () -> orderService.submitOrder(noPrice));
            verify(orderRepository, never()).saveAndFlush(any());
            verifyNoInteractions(orderEventPublisher);
        }

        @DisplayName("Should never ask for a live price for an unknown instrument")
        @Test
        void testSubmitUnknownInstrumentWithoutPrice() {
            when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.empty());
            PlaceOrderRequest noPrice = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, 100, null, "ORDER-001");

            assertThrows(InstrumentNotFoundException.class, () -> orderService.submitOrder(noPrice));
            verifyNoInteractions(priceProvider);
        }

        @DisplayName("Should store the order as REJECTED when Kafka cannot take it")
        @Test
        void testSubmitRejectsUnqueuedOrder() {
            OrderPublishException failure = new OrderPublishException("Could not queue order", new RuntimeException("down"));
            doThrow(failure).when(orderEventPublisher).publish(any(Order.class));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThrows(OrderPublishException.class, () -> orderService.submitOrder(placeOrderRequest));

            ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(saved.capture());
            assertEquals(OrderStatus.REJECTED, saved.getValue().getStatus());
            assertTrue(saved.getValue().getStatusReason().startsWith("Could not be queued for execution"));
            verify(tradeEventPublisher).publish(eq(saved.getValue()), eq(OrderStatus.NEW), anyString());
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

    @DisplayName("processOrderEvent with a stored NEW order (Trade Executor)")
    @Nested
    class StoredOrderProcessingTests {
        private Order storedOrder;
        private OrderEvent event;

        @BeforeEach
        void storeNewOrder() {
            storedOrder = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "CLIENT-KEY", testClock);
            event = eventFor(storedOrder);
            when(orderRepository.findById(storedOrder.getId())).thenReturn(Optional.of(storedOrder));
            when(orderRepository.findWithLockById(storedOrder.getId())).thenReturn(Optional.of(storedOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
            when(instrumentRepository.findBySymbol("AAPL"))
                    .thenReturn(Optional.of(new Instrument("AAPL", "Apple Inc.", "EQUITY", "USD", true)));
        }

        private OrderEvent eventFor(Order order) {
            return new OrderEvent(order.getId(), order.getAccountId(), order.getSymbol(), order.getSide(),
                    order.getQuantity(), order.getPrice(), order.getCreatedOn(), order.getIdempotencyKey());
        }

        @DisplayName("Should send an affordable order to the broker, leaving it NEW and moving no cash")
        @Test
        void testSendsStoredOrderToBroker() {
            Order result = orderService.processOrderEvent(event);

            assertSame(storedOrder, result);
            assertEquals(OrderStatus.NEW, result.getStatus());
            assertEquals("CLIENT-KEY", result.getIdempotencyKey());
            // No live price (the mock has none): the broker fills relative to the limit
            verify(brokerClient).submit(storedOrder, null);
            verifyNoInteractions(buyStrategy, tradeEventPublisher);
            assertEquals(new BigDecimal("100000.00"), testAccount.getCashBalance());
        }

        @DisplayName("Should send the live price, rounded to the cent, with the order to the broker")
        @Test
        void testSendsMarketPriceToBroker() {
            when(priceProvider.latestPrice(storedOrder.getSymbol())).thenReturn(Optional.of(new PriceQuote(
                    storedOrder.getSymbol(), new BigDecimal("148.1249"), Instant.parse("2026-10-16T14:30:00Z"))));

            orderService.processOrderEvent(event);

            verify(brokerClient).submit(storedOrder, new BigDecimal("148.12"));
        }

        @DisplayName("Should reject an order the account cannot afford at its limit price, without the broker")
        @Test
        void testRejectsUnaffordableOrder() {
            Order tooBig = new Order(1L, "AAPL", OrderSide.BUY, 1000, new BigDecimal("150.00"), "BIG", testClock);
            when(orderRepository.findById(tooBig.getId())).thenReturn(Optional.of(tooBig));
            when(orderRepository.findWithLockById(tooBig.getId())).thenReturn(Optional.of(tooBig));

            OrderEvent tooBigEvent = eventFor(tooBig);
            assertThrows(InsufficientFundsException.class, () -> orderService.processOrderEvent(tooBigEvent));

            assertEquals(OrderStatus.REJECTED, tooBig.getStatus());
            verifyNoInteractions(brokerClient);
            verify(tradeEventPublisher).publish(tooBig, OrderStatus.NEW, "Insufficient funds for buy order");
        }

        @DisplayName("Should reject a SELL without enough holdings")
        @Test
        void testRejectsSellWithoutHoldings() {
            Order sell = new Order(1L, "AAPL", OrderSide.SELL, 10, new BigDecimal("150.00"), "SELL", testClock);
            when(orderRepository.findById(sell.getId())).thenReturn(Optional.of(sell));
            when(orderRepository.findWithLockById(sell.getId())).thenReturn(Optional.of(sell));
            when(positionRepository.findByAccountIdAndSymbol(1L, "AAPL"))
                    .thenReturn(Optional.of(new Position(1L, "AAPL", 5, new BigDecimal("100.00"))));

            OrderEvent sellEvent = eventFor(sell);
            assertThrows(InsufficientHoldingsException.class, () -> orderService.processOrderEvent(sellEvent));
            verifyNoInteractions(brokerClient);
        }

        @DisplayName("Should reject the order (retryable) when the broker cannot be reached")
        @Test
        void testRejectsWhenBrokerUnavailable() {
            doThrow(new BrokerUnavailableException("down", new RuntimeException())).when(brokerClient).submit(any(), any());

            assertThrows(BrokerUnavailableException.class, () -> orderService.processOrderEvent(event));
            assertEquals(OrderStatus.REJECTED, storedOrder.getStatus());
        }

        @DisplayName("Should not send an order cancelled while it waited")
        @Test
        void testSkipsCancelledOrder() {
            // Cancelled between the first lookup and the locked one
            Order cancelled = new Order(storedOrder, testClock);
            cancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findWithLockById(storedOrder.getId())).thenReturn(Optional.of(cancelled));

            orderService.processOrderEvent(event);

            verifyNoInteractions(brokerClient, tradeEventPublisher);
        }
    }

    @DisplayName("settleExecution (fills from the broker)")
    @Nested
    class SettlementTests {
        private Order working;

        @BeforeEach
        void orderAtTheBroker() {
            working = new Order(1L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "KEY", testClock);
            when(orderRepository.findWithLockById(working.getId())).thenReturn(Optional.of(working));
            when(orderRepository.findById(working.getId())).thenReturn(Optional.of(working));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        }

        private ExecutionEvent fillAt(String price) {
            return new ExecutionEvent(UUID.randomUUID(), working.getId(), 1L, "AAPL", OrderSide.BUY, 10,
                    new BigDecimal(price), "SIM", Instant.parse("2026-10-12T10:00:00Z"));
        }

        @DisplayName("Should settle at the fill price, record the execution and mark the order FILLED")
        @Test
        void testSettlesFill() {
            ExecutionEvent fill = fillAt("149.50");

            Order result = orderService.settleExecution(fill);

            assertSame(working, result);
            assertEquals(OrderStatus.FILLED, working.getStatus());
            ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
            verify(buyStrategy).execute(eq(testAccount), request.capture(), eq("AAPL"));
            assertEquals(new BigDecimal("149.50"), request.getValue().price());
            ArgumentCaptor<Execution> execution = ArgumentCaptor.forClass(Execution.class);
            verify(executionRepository).save(execution.capture());
            assertEquals(fill.executionId(), execution.getValue().getId());
            assertEquals(new BigDecimal("149.50"), execution.getValue().getPrice());
            assertEquals("SIM", execution.getValue().getVenue());
            verify(tradeEventPublisher).publish(working, OrderStatus.NEW, null);
        }

        @DisplayName("Should ignore a fill for an order that is no longer NEW")
        @Test
        void testIgnoresFillForCancelledOrder() {
            working.setStatus(OrderStatus.CANCELLED);

            assertNull(orderService.settleExecution(fillAt("149.50")));

            verifyNoInteractions(buyStrategy, executionRepository, tradeEventPublisher);
        }

        @DisplayName("Should reject the order when the cash was spent before the fill arrived")
        @Test
        void testRejectsWhenNoLongerAffordable() {
            doThrow(new InsufficientFundsException("Insufficient funds for buy order"))
                    .when(buyStrategy).execute(any(), any(), any());

            ExecutionEvent fill = fillAt("149.50");
            SettlementException ex = assertThrows(SettlementException.class, () -> orderService.settleExecution(fill));

            assertEquals(working.getId(), ex.getOrderEvent().orderId());
            assertEquals("KEY", ex.getOrderEvent().idempotencyKey());
            assertTrue(ex.isRetryable());
            assertEquals(OrderStatus.REJECTED, working.getStatus());
            verify(executionRepository, never()).save(any());
            verify(tradeEventPublisher).publish(working, OrderStatus.NEW, "Insufficient funds for buy order");
        }

        @DisplayName("Should reject a fill for an account that was suspended meanwhile")
        @Test
        void testRejectsInactiveAccount() {
            testAccount.setStatus(AccountStatus.SUSPENDED);

            ExecutionEvent fill = fillAt("149.50");
            assertThrows(SettlementException.class, () -> orderService.settleExecution(fill));
            verifyNoInteractions(buyStrategy);
        }
    }
}
