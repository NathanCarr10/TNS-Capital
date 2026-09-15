package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PositionTest {
    private Position position;

    @BeforeEach
    void setUp() {
        position = new Position(1L, "AAPL", 100, new BigDecimal("150.00"));
    }

    @Test
    void testPositionConstructor() {
        // ARRANGE: position created in setUp
        
        // ACT & ASSERT: verify initialization
        assertEquals(1L, position.getAccountId());
        assertEquals("AAPL", position.getSymbol());
        assertEquals(100, position.getQuantity());
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());
    }

    @Test
    void testApplyWhenQuantityIsZero() {
        // ARRANGE
        Position newPosition = new Position(1L, "AAPL", 0, new BigDecimal("0.00"));
        
        // ACT
        newPosition.apply(50, new BigDecimal("160.00"));
        
        // ASSERT
        assertEquals(50, newPosition.getQuantity());
        assertEquals(new BigDecimal("160.00"), newPosition.getAverageCost());
    }

    @Test
    void testApplyAdditionalQuantity() {
        // ARRANGE: position created in setUp
        
        // ACT
        position.apply(100, new BigDecimal("160.00"));
        
        // ASSERT
        assertEquals(200, position.getQuantity());
        assertEquals(new BigDecimal("155.00"), position.getAverageCost());
    }

    @Test
    void testApplyMultipleTimes() {
        // ARRANGE: position created in setUp
        
        // ACT
        position.apply(50, new BigDecimal("155.00"));
        position.apply(50, new BigDecimal("160.00"));
        
        // ASSERT
        assertEquals(200, position.getQuantity());
        assertTrue(position.getAverageCost().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testMarketValue() {
        // ARRANGE: position created in setUp (100 @ 150.00)
        
        // ACT
        BigDecimal marketValue = position.marketValue(new BigDecimal("160.00"));
        
        // ASSERT
        assertEquals(new BigDecimal("16000.00"), marketValue);
    }

    @Test
    void testMarketValueWithZeroQuantity() {
        // ARRANGE
        Position zeroPosition = new Position(1L, "AAPL", 0, new BigDecimal("150.00"));
        
        // ACT
        BigDecimal marketValue = zeroPosition.marketValue(new BigDecimal("160.00"));
        
        // ASSERT
        assertEquals(new BigDecimal("0.00"), marketValue);
    }

    @Test
    void testMarketValueWithDifferentPrices() {
        // ARRANGE: position created in setUp
        
        // ACT
        BigDecimal marketValue1 = position.marketValue(new BigDecimal("150.00"));
        BigDecimal marketValue2 = position.marketValue(new BigDecimal("200.00"));
        
        // ASSERT
        assertTrue(marketValue2.compareTo(marketValue1) > 0);
    }

    @Test
    void testAverageCostCalculationPrecision() {
        // ARRANGE
        Position testPosition = new Position(1L, "AAPL", 100, new BigDecimal("100.00"));
        
        // ACT
        testPosition.apply(100, new BigDecimal("110.00"));
        
        // ASSERT
        assertEquals(new BigDecimal("105.00"), testPosition.getAverageCost());
    }
}
