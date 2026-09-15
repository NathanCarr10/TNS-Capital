package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PositionResponseTest {

    @Test
    void testPositionResponseCreation() {
        // ARRANGE
        Long accountId = 1L;
        String symbol = "AAPL";
        Integer quantity = 100;
        BigDecimal averageCost = new BigDecimal("150.00");
        BigDecimal marketValue = new BigDecimal("15000.00");
        
        // ACT
        PositionResponse response = new PositionResponse(accountId, symbol, quantity, averageCost, marketValue);
        
        // ASSERT
        assertEquals(accountId, response.accountId());
        assertEquals(symbol, response.symbol());
        assertEquals(quantity, response.quantity());
        assertEquals(averageCost, response.averageCost());
        assertEquals(marketValue, response.marketValue());
    }

    @Test
    void testPositionResponseWithDifferentSymbols() {
        // ARRANGE
        PositionResponse aapl = new PositionResponse(
                1L, "AAPL", 100, new BigDecimal("150.00"), new BigDecimal("15000.00"));
        PositionResponse msft = new PositionResponse(
                1L, "MSFT", 100, new BigDecimal("300.00"), new BigDecimal("30000.00"));
        
        // ACT & ASSERT
        assertNotEquals(aapl.symbol(), msft.symbol());
    }

    @Test
    void testPositionResponseZeroQuantity() {
        // ARRANGE
        PositionResponse response = new PositionResponse(
                1L, "AAPL", 0, new BigDecimal("150.00"), new BigDecimal("0.00"));
        
        // ACT & ASSERT
        assertEquals(0, response.quantity());
    }
}
