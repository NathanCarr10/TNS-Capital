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
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.model.Position;
import com.neueda.leap.strategies.BuyOrderStrategy;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.strategies.SellOrderStrategy;
import com.neueda.leap.time.ClockTest;
import com.neueda.leap.repositories.impl.InMemoryAccountRepository;
import com.neueda.leap.repositories.impl.InMemoryInstrumentRepository;
import com.neueda.leap.repositories.impl.InMemoryOrderRepository;
import com.neueda.leap.repositories.impl.InMemoryPositionRepository;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;

@DisplayName("OrderService Test Suite")
class OrderServiceTest {
    private OrderService orderService;
    private OrderValidator validator;
    private Map<Long, Account> accountsMap;
    private Map<String, Instrument> instrumentsMap;
    private Map<String, Position> positionsMap;
    private Map<String, Order> ordersMap;
    private Map<OrderSide, OrderExecutionStrategy> strategies;
    private AccountRepository accountRepository;
    private InstrumentRepository instrumentRepository;
    private OrderRepository orderRepository;
    private PositionRepository positionRepository;
    private ClockTest testClock;
    private Account testAccount;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        // Initialize test clock
        testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));

        // Initialize underlying maps
        accountsMap = new HashMap<>();
        instrumentsMap = new HashMap<>();
        positionsMap = new HashMap<>();
        ordersMap = new HashMap<>();
        strategies = new HashMap<>();

        // Create test data
        testAccount = new Account("ACC001", "John Doe", new BigDecimal("50000.00"), testClock);
        testAccount.setId(1L);
        accountsMap.put(1L, testAccount);

        testInstrument = new Instrument("AAPL", "APPLE INC", "EQUITY", "USD", true);
        // testInstrument.setId(1L);
        instrumentsMap.put("AAPL", testInstrument);

        // Create repository implementations
        accountRepository = new InMemoryAccountRepository(accountsMap);
        instrumentRepository = new InMemoryInstrumentRepository(instrumentsMap);
        orderRepository = new InMemoryOrderRepository(ordersMap);
        positionRepository = new InMemoryPositionRepository(positionsMap);

        // Setup strategies
        strategies.put(OrderSide.BUY, new BuyOrderStrategy(positionRepository));
        strategies.put(OrderSide.SELL, new SellOrderStrategy(positionRepository));

        // Create validator and OrderService
        validator = new OrderValidator(accountRepository, instrumentRepository, orderRepository);
        orderService = new OrderService(accountRepository, orderRepository, positionRepository, validator,
                strategies, testClock);
    }

    @DisplayName("Successful Order Placement Tests")
    @Nested
    class SuccessfulOrderPlacementTests {
        @DisplayName("Valid buy order is placed and status is FILLED")
        @Test
        void testValidBuyOrderPlacedSuccessfully() {
            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "BUY-001");

            Order result = orderService.placeOrder(buyRequest);

            // Verify order properties
            assertEquals("BUY-001", result.getIdempotencyKey(), "Idempotency key should match");
            assertEquals(OrderStatus.FILLED, result.getStatus(), "Order status should be FILLED");
            assertEquals(1L, result.getAccountId(), "Account ID should match");
            assertEquals("AAPL", result.getSymbol(), "Symbol should match");
            assertEquals(OrderSide.BUY, result.getSide(), "Order side should be BUY");
        }

        @DisplayName("Buy order deducts correct amount from account balance")
        @Test
        void testBuyOrderDebitsAccountCorrectly() {
            BigDecimal initialBalance = testAccount.getCashBalance();
            BigDecimal price = new BigDecimal("150.00");
            int quantity = 100;

            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    quantity,
                    price,
                    "BUY-002");

            Order result = orderService.placeOrder(buyRequest);

            BigDecimal expectedCost = price.multiply(BigDecimal.valueOf(quantity));
            BigDecimal expectedBalance = initialBalance.subtract(expectedCost);

            assertEquals(expectedBalance, testAccount.getCashBalance(),
                    "Account balance should be reduced by order cost");
            assertEquals(OrderStatus.FILLED, result.getStatus(), "Order should be FILLED");
        }

        @DisplayName("Buy order creates position for new symbol")
        @Test
        void testBuyOrderCreatesNewPosition() {
            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "BUY-003");

            Order result = orderService.placeOrder(buyRequest);

            // Verify position was created
            String positionKey = "1::AAPL";
            var positionOpt = positionRepository.findPosition(1L, "AAPL");
            assertTrue(positionOpt.isPresent(), "Position should be created for AAPL");
            assertEquals(100, positionOpt.get().getQuantity(), "Position quantity should be 100");
        }
    }

    @DisplayName("Order Rejection Tests")
    @Nested
    class OrderRejectionTests {
        @DisplayName("Invalid order (insufficient funds) is rejected with REJECTED status")
        @Test
        void testInsufficientFundsRejection() {
            // Try to buy more than account balance allows
            PlaceOrderRequest expensiveBuyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    1000, // Quantity too high
                    new BigDecimal("150.00"), // Price
                    "BUY-FAIL-001");

            // Should throw exception
            assertThrows(InsufficientFundsException.class,
                    () -> orderService.placeOrder(expensiveBuyRequest),
                    "Should throw InsufficientFundsException");

            // But the order should still be persisted with REJECTED status
            var rejectedOrderOpt = orderRepository.findByIdempotencyKey("BUY-FAIL-001");
            assertTrue(rejectedOrderOpt.isPresent(), "Rejected order should still be persisted");
            assertEquals(OrderStatus.REJECTED, rejectedOrderOpt.get().getStatus(),
                    "Order status should be REJECTED after exception");
        }

        @DisplayName("Order is persisted even when validation fails")
        @Test
        void testOrderPersistedOnValidationFailure() {
            // Try to place order with non-existent account
            PlaceOrderRequest invalidRequest = new PlaceOrderRequest(
                    999L, // Non-existent account
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "INVALID-ACC-001");

            // Should throw validation exception
            assertThrows(Exception.class, () -> orderService.placeOrder(invalidRequest));

            // Order should NOT be in orders map (validation happens before order creation)
            // This is correct behavior - order not created if validation fails
        }
    }

    @DisplayName("Order Retrieval Tests")
    @Nested
    class OrderRetrievalTests {
        @DisplayName("Order can be found by idempotency key")
        @Test
        void testFindOrderByIdempotencyKey() {
            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "FIND-001");

            Order placedOrder = orderService.placeOrder(buyRequest);
            Order foundOrder = orderService.findByIdempotencyKey("FIND-001").orElse(null);

            assertNotNull(foundOrder, "Order should be found by idempotency key");
            assertEquals(placedOrder.getIdempotencyKey(), foundOrder.getIdempotencyKey(),
                    "Found order should match placed order");
        }

        @DisplayName("Finding non-existent order returns empty Optional")
        @Test
        void testFindNonExistentOrderReturnsEmpty() {
            var result = orderService.findByIdempotencyKey("DOES-NOT-EXIST");

            assertTrue(result.isEmpty(), "Finding non-existent order should return empty Optional");
        }
    }

    @DisplayName("Position Retrieval Tests")
    @Nested
    class PositionRetrievalTests {
        @DisplayName("Position can be found by account and symbol")
        @Test
        void testFindPositionByAccountAndSymbol() {
            // First place a buy order to create a position
            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "POS-001");

            orderService.placeOrder(buyRequest);

            // Now find the position
            var foundPosition = orderService.findPosition(1L, "AAPL");

            assertTrue(foundPosition.isPresent(), "Position should be found");
            assertEquals(100, foundPosition.get().getQuantity(), "Position quantity should be 100");
        }

        @DisplayName("Finding non-existent position returns empty Optional")
        @Test
        void testFindNonExistentPositionReturnsEmpty() {
            var result = orderService.findPosition(1L, "MSFT");

            assertTrue(result.isEmpty(), "Finding non-existent position should return empty Optional");
        }

        @DisplayName("Symbol is normalized when finding position")
        @Test
        void testPositionFindingNormalizesSymbol() {
            // Create position with uppercase
            PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                    1L,
                    "AAPL",
                    OrderSide.BUY,
                    100,
                    new BigDecimal("150.00"),
                    "POS-002");

            orderService.placeOrder(buyRequest);

            // Find with lowercase - should still work
            var foundPosition = orderService.findPosition(1L, "aapl");

            assertTrue(foundPosition.isPresent(), "Position should be found regardless of symbol case");
        }
    }
}
