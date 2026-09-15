package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.OrderSide;

class PlaceOrderRequestTest {

    @Test
    void testPlaceOrderRequestCreation() {
        // ARRANGE
        Long accountId = 1L;
        String symbol = "AAPL";
        OrderSide side = OrderSide.BUY;
        Integer quantity = 100;
        BigDecimal price = new BigDecimal("150.50");
        String idempotencyKey = "ID-12345";
        
        // ACT
        PlaceOrderRequest request = new PlaceOrderRequest(
                accountId, symbol, side, quantity, price, idempotencyKey);
        
        // ASSERT
        assertEquals(accountId, request.accountId());
        assertEquals(symbol, request.symbol());
        assertEquals(side, request.side());
        assertEquals(quantity, request.quantity());
        assertEquals(price, request.price());
        assertEquals(idempotencyKey, request.idempotencyKey());
    }

    @Test
    void testPlaceOrderRequestWithSellSide() {
        // ARRANGE
        PlaceOrderRequest request = new PlaceOrderRequest(
                2L, "MSFT", OrderSide.SELL, 50, new BigDecimal("300.00"), "ID-67890");
        
        // ACT & ASSERT
        assertEquals(OrderSide.SELL, request.side());
        assertEquals(50, request.quantity());
    }
}
