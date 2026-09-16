package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.time.ClockTest;

class OrderTest {
    private Order order;
    private ClockTest testClock;

    @BeforeEach
    void setUp() {
        testClock = new ClockTest(Instant.parse("2026-09-16T10:00:00Z"));
        order = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-12345", testClock);
    }

    @Test
    void testOrderConstructor() {
        // ARRANGE: order created in setUp

        // ACT & ASSERT: verify initialization
        assertEquals(1L, order.getAccountId());
        assertEquals("AAPL", order.getSymbol());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(100, order.getQuantity());
        assertEquals(new BigDecimal("150.50"), order.getPrice());
        assertEquals(OrderStatus.NEW, order.getStatus());
        assertEquals("ID-12345", order.getIdempotencyKey());
        assertNotNull(order.getId());
        assertNotNull(order.getCreatedOn());
    }

    @Test
    void testOrderDefaultConstructor() {
        // ARRANGE: create empty order
        Order emptyOrder = new Order(testClock);

        // ACT & ASSERT: verify defaults
        assertNotNull(emptyOrder.getId());
        assertNotNull(emptyOrder.getCreatedOn());
        assertNull(emptyOrder.getSymbol());
        assertNull(emptyOrder.getStatus());
    }

    @Test
    void testSetStatus() {
        // ARRANGE: order created in setUp

        // ACT
        order.setStatus(OrderStatus.FILLED);

        // ASSERT
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void testSellOrder() {
        // ARRANGE
        Order sellOrder = new Order(2L, "MSFT", OrderSide.SELL, 50, new BigDecimal("300.00"), "ID-67890", testClock);

        // ACT & ASSERT
        assertEquals(OrderSide.SELL, sellOrder.getSide());
        assertEquals(50, sellOrder.getQuantity());
    }

    @Test
    void testOrderWithDifferentPrices() {
        // ARRANGE
        Order order1 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.50"), "ID-1", testClock);
        Order order2 = new Order(1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("160.75"), "ID-2", testClock);

        // ACT & ASSERT
        assertNotEquals(order1.getPrice(), order2.getPrice());
    }
}
