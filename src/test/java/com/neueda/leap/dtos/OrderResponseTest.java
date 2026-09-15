package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;

class OrderResponseTest {

    @Test
    void testOrderResponseCreation() {
        // ARRANGE
        Long orderId = 1L;
        LocalDateTime createdOn = LocalDateTime.of(2026, 9, 15, 12, 0, 0);
        
        // ACT
        OrderResponse response = new OrderResponse(
                orderId, 1L, "AAPL", OrderSide.BUY, 100, 
                new BigDecimal("150.50"), OrderStatus.NEW, createdOn);
        
        // ASSERT
        assertEquals(orderId, response.id());
        assertEquals(1L, response.accountId());
        assertEquals("AAPL", response.symbol());
        assertEquals(OrderSide.BUY, response.side());
        assertEquals(100, response.quantity());
        assertEquals(new BigDecimal("150.50"), response.price());
        assertEquals(OrderStatus.NEW, response.status());
        assertEquals(createdOn, response.createdOn());
    }

    @Test
    void testOrderResponseWithFilledStatus() {
        // ARRANGE
        LocalDateTime createdOn = LocalDateTime.now();
        OrderResponse response = new OrderResponse(
                2L, 1L, "AAPL", OrderSide.BUY, 100, 
                new BigDecimal("150.50"), OrderStatus.FILLED, createdOn);
        
        // ACT & ASSERT
        assertEquals(OrderStatus.FILLED, response.status());
    }

    @Test
    void testOrderResponseWithSellSide() {
        // ARRANGE
        LocalDateTime createdOn = LocalDateTime.now();
        OrderResponse response = new OrderResponse(
                3L, 2L, "MSFT", OrderSide.SELL, 50, 
                new BigDecimal("300.00"), OrderStatus.NEW, createdOn);
        
        // ACT & ASSERT
        assertEquals(OrderSide.SELL, response.side());
    }
}
