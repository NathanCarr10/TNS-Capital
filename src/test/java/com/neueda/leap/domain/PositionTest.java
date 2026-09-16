package com.neueda.leap.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Position Test Suite")
class PositionTest {
    private Position position;

    @BeforeEach
    void setUp() {
        position = new Position(1L, "AAPL", 100, new BigDecimal("150.00"));
    }

    @DisplayName("Constructor initializes all fields correctly")
    @Test
    void testPositionConstructor() {
        assertEquals(1L, position.getAccountId(), "Account ID should match constructor argument");
        assertEquals("AAPL", position.getSymbol(), "Symbol should match constructor argument");
        assertEquals(100, position.getQuantity(), "Quantity should match constructor argument");
        assertEquals(new BigDecimal("150.00"), position.getAverageCost(), "Average cost should match constructor argument");
    }

    @DisplayName("Apply Operation Tests")
    @Nested
    class ApplyTests {
        @DisplayName("Apply to zero quantity position initializes average cost")
        @Test
        void testApplyWhenQuantityIsZero() {
            Position newPosition = new Position(1L, "AAPL", 0, new BigDecimal("0.00"));
            newPosition.apply(50, new BigDecimal("160.00"));
            assertEquals(50, newPosition.getQuantity(), "Quantity should be 50 after apply");
            assertEquals(new BigDecimal("160.00"), newPosition.getAverageCost(), 
                "Average cost should equal the new purchase price when quantity was zero");
        }

        @DisplayName("Apply additional quantity calculates correct average cost")
        @ParameterizedTest(name = "Position 100@150 + {0}@{1} = Avg {2}")
        @CsvSource({
            "100, 160.00, 155.00",
            "50, 160.00, 153.33",
            "200, 150.00, 150.00"
        })
        void testApplyAdditionalQuantityAverageCost(int qty, String price, String expectedAvg) {
            position.apply(qty, new BigDecimal(price));
            assertEquals(100 + qty, position.getQuantity(), 
                "Total quantity should be sum of existing and new quantity");
            assertEquals(new BigDecimal(expectedAvg), position.getAverageCost(), 
                "Average cost should correctly blend old and new purchases");
        }

        @DisplayName("Multiple apply operations accumulate correctly")
        @Test
        void testApplyMultipleTimes() {
            position.apply(50, new BigDecimal("155.00"));
            position.apply(50, new BigDecimal("160.00"));
            assertEquals(200, position.getQuantity(), "Total quantity should be 200 after two apply operations");
            assertTrue(position.getAverageCost().compareTo(BigDecimal.ZERO) > 0,
                "Average cost should remain positive after multiple applies");
        }
    }

    @DisplayName("Market Value Calculation Tests")
    @Nested
    class MarketValueTests {
        @DisplayName("Market value calculated as quantity times current price")
        @ParameterizedTest(name = "100 shares @ {0} = Market Value {1}")
        @CsvSource({
            "150.00, 15000.00",
            "160.00, 16000.00",
            "100.00, 10000.00",
            "200.50, 20050.00"
        })
        void testMarketValueCalculation(String price, String expectedValue) {
            BigDecimal marketValue = position.marketValue(new BigDecimal(price));
            assertEquals(new BigDecimal(expectedValue), marketValue, 
                "Market value should be quantity multiplied by current price");
        }

        @DisplayName("Market value is zero when position quantity is zero")
        @Test
        void testMarketValueWithZeroQuantity() {
            Position zeroPosition = new Position(1L, "AAPL", 0, new BigDecimal("150.00"));
            BigDecimal marketValue = zeroPosition.marketValue(new BigDecimal("160.00"));
            assertEquals(new BigDecimal("0.00"), marketValue, 
                "Market value should be zero when position has zero quantity");
        }

        @DisplayName("Higher current price results in higher market value")
        @Test
        void testMarketValueIncreaseWithPrice() {
            BigDecimal marketValueLow = position.marketValue(new BigDecimal("150.00"));
            BigDecimal marketValueHigh = position.marketValue(new BigDecimal("200.00"));
            assertTrue(marketValueHigh.compareTo(marketValueLow) > 0, 
                "Market value should increase as current price increases");
            assertEquals(new BigDecimal("15000.00"), marketValueLow, 
                "Market value at original price should be 100 * 150");
            assertEquals(new BigDecimal("20000.00"), marketValueHigh, 
                "Market value at higher price should be 100 * 200");
        }
    }
}
