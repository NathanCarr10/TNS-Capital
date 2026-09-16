package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.time.ClockTest;

@DisplayName("Order Test Suite")
class OrderTest {
    private Order order;
    private ClockTest testClock;

    @BeforeEach
    void setUp() {
        testClock = new ClockTest(Instant.parse("2026-09-16T10:00:00Z"));
        order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-12345", testClock);
    }

    @DisplayName("Constructor with all parameters initializes correctly")
    @Test
    void testOrderConstructor() {
        assertEquals(1L, order.getAccountId(), "Account ID should match constructor argument");
        assertEquals("AAPL", order.getSymbol(), "Symbol should match constructor argument");
        assertEquals(OrderSide.BUY, order.getSide(), "Order side should be BUY");
        assertEquals(100, order.getQuantity(), "Quantity should match constructor argument");
        assertEquals(new BigDecimal("150.50"), order.getPrice(), "Price should match constructor argument");
        assertEquals(OrderStatus.NEW, order.getStatus(), "New order should have NEW status");
        assertEquals("ID-12345", order.getIdempotencyKey(), "Idempotency key should match constructor argument");
        assertNotNull(order.getId(), "Order should have a generated UUID");
        assertNotNull(order.getCreatedOn(), "Order should have a creation timestamp");
    }

    @DisplayName("Default constructor generates ID and timestamp")
    @Test
    void testOrderDefaultConstructor() {
        Order emptyOrder = new Order(testClock);
        assertNotNull(emptyOrder.getId(), "Default constructor should generate UUID");
        assertNotNull(emptyOrder.getCreatedOn(), "Default constructor should set creation timestamp");
        assertNull(emptyOrder.getSymbol(), "Symbol should be null in default constructor");
        assertNull(emptyOrder.getStatus(), "Status should be null in default constructor");
    }

    @DisplayName("Order Status Transition Tests")
    @Nested
    class StatusTransitionTests {
        @DisplayName("Order status can be changed via setStatus()")
        @ParameterizedTest(name = "Order status changed to {0}")
        @ValueSource(strings = { "FILLED", "REJECTED", "CANCELLED" })
        void testSetStatusToValidValues(String statusStr) {
            OrderStatus newStatus = OrderStatus.valueOf(statusStr);
            order.setStatus(newStatus);
            assertEquals(newStatus, order.getStatus(),
                    "Order status should be updated to " + statusStr);
        }

        @DisplayName("Order transitions from NEW to FILLED")
        @Test
        void testOrderFilled() {
            order.setStatus(OrderStatus.FILLED);
            assertEquals(OrderStatus.FILLED, order.getStatus(),
                    "Order should have FILLED status after transition");
        }
    }

    @DisplayName("Order Side and Symbol Tests")
    @Nested
    class OrderSideSymbolTests {
        @DisplayName("BUY order created correctly")
        @Test
        void testBuyOrder() {
            assertEquals(OrderSide.BUY, order.getSide(), "Order should have BUY side");
        }

        @DisplayName("SELL order created correctly")
        @Test
        void testSellOrder() {
            Order sellOrder = new Order(2L, "MSFT", OrderSide.SELL, 50, new BigDecimal("300.00"), "ID-67890",
                    testClock);
            assertEquals(OrderSide.SELL, sellOrder.getSide(), "Sell order should have SELL side");
            assertEquals("MSFT", sellOrder.getSymbol(), "Sell order should have correct symbol");
            assertEquals(50, sellOrder.getQuantity(), "Sell order should have correct quantity");
        }

        @DisplayName("Different orders maintain distinct symbols and prices")
        @Test
        void testOrdersWithDifferentSymbols() {
            Order aapl = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-1", testClock);
            Order msft = new Order(1L, "MSFT", OrderSide.BUY, 100, new BigDecimal("300.75"), "ID-2", testClock);
            assertNotEquals(aapl.getSymbol(), msft.getSymbol(),
                    "Orders for different symbols should be distinct");
            assertNotEquals(aapl.getPrice(), msft.getPrice(),
                    "Orders at different prices should have different prices");
        }
    }

    @DisplayName("Order Uniqueness Tests")
    @Nested
    class OrderUniquenessTests {
        @DisplayName("Each order has unique UUID")
        @Test
        void testOrdersHaveUniqueIds() {
            Order order1 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-1", testClock);
            Order order2 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-2", testClock);
            assertNotEquals(order1.getId(), order2.getId(),
                    "Different orders should have different UUIDs");
        }

        @DisplayName("Idempotency key distinguishes orders with same parameters")
        @Test
        void testIdempotencyKeyDifferentiatesOrders() {
            Order order1 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-SAME-1", testClock);
            Order order2 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-SAME-2", testClock);
            assertNotEquals(order1.getIdempotencyKey(), order2.getIdempotencyKey(),
                    "Different idempotency keys should distinguish orders");
            assertEquals(order1.getSymbol(), order2.getSymbol(),
                    "Orders should have same symbol");
            assertEquals(order1.getPrice(), order2.getPrice(),
                    "Orders should have same price");
        }
    }
}
