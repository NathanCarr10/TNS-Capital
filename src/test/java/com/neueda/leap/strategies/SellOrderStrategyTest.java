package com.neueda.leap.strategies;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.neueda.leap.dtos.PlaceOrderRequest;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.InsufficientHoldingsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Position;
import com.neueda.leap.time.ClockTest;
import com.neueda.leap.repositories.impl.InMemoryPositionRepository;
import com.neueda.leap.repositories.PositionRepository;

@DisplayName("SellOrderStrategy Test Suite")
class SellOrderStrategyTest {
        private SellOrderStrategy sellStrategy;
        private BuyOrderStrategy buyStrategy;
        private Map<String, Position> positionsMap;
        private PositionRepository positionRepository;
        private Account testAccount;
        private ClockTest testClock;

        @BeforeEach
        void setUp() {
                testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
                positionsMap = new HashMap<>();

                // Create test account
                testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);

                // Create position repository
                positionRepository = new InMemoryPositionRepository(positionsMap);

                // Initialize both strategies with repository
                sellStrategy = new SellOrderStrategy(positionRepository);
                buyStrategy = new BuyOrderStrategy(positionRepository);
        }

        @DisplayName("Holding Validation Tests")
        @Nested
        class HoldingValidationTests {
                @DisplayName("Sell without holding throws InsufficientHoldingsException")
                @Test
                void testSellWithoutHoldingsThrowsException() {
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        new BigDecimal("150.00"),
                                        "SELL-NO-HOLD");

                        assertThrows(InsufficientHoldingsException.class,
                                        () -> sellStrategy.execute(testAccount, sellRequest, "AAPL"),
                                        "Should throw InsufficientHoldingsException when no position exists");
                }

                @DisplayName("Sell more than held throws InsufficientHoldingsException")
                @Test
                void testSellMoreThanHeldThrowsException() {
                        // First buy 100 shares
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-001");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        // Now try to sell 150 shares (more than held)
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        150, // Trying to sell more than 100 held
                                        new BigDecimal("160.00"),
                                        "SELL-TOO-MUCH");

                        assertThrows(InsufficientHoldingsException.class,
                                        () -> sellStrategy.execute(testAccount, sellRequest, "AAPL"),
                                        "Should throw exception when selling more than held");
                }

                @DisplayName("Selling exactly held amount succeeds")
                @Test
                void testSellExactlyHeldAmountSucceeds() {
                        // Buy 100 shares
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-EXACT");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        // Sell exactly 100 shares
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        new BigDecimal("160.00"),
                                        "SELL-EXACT");

                        // Should not throw exception
                        assertDoesNotThrow(() -> sellStrategy.execute(testAccount, sellRequest, "AAPL"),
                                        "Should successfully sell exact amount held");
                }
        }

        @DisplayName("Account Credit Tests")
        @Nested
        class AccountCreditTests {
                @DisplayName("Sell order credits correct amount to account")
                @Test
                void testSellOrderCreditsAccountCorrectly() {
                        // Buy first
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-CREDIT");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        BigDecimal balanceAfterBuy = testAccount.getCashBalance();

                        // Now sell
                        BigDecimal sellPrice = new BigDecimal("160.00");
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        sellPrice,
                                        "SELL-CREDIT");

                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        // Expected proceeds: 100 shares * $160 = $16,000
                        BigDecimal expectedProceeds = sellPrice.multiply(BigDecimal.valueOf(100));
                        BigDecimal expectedBalance = balanceAfterBuy.add(expectedProceeds);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Account balance should be increased by sale proceeds");
                }

                @DisplayName("Sell profit is correctly calculated (selling at higher price)")
                @Test
                void testSellProfitCalculation() {
                        BigDecimal initialBalance = testAccount.getCashBalance();

                        // Buy 100 shares at $150
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-PROFIT");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        // Sell 100 shares at $160
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        new BigDecimal("160.00"),
                                        "SELL-PROFIT");
                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        // Cost: 100 * $150 = $15,000
                        // Revenue: 100 * $160 = $16,000
                        // Net: $100,000 - $15,000 + $16,000 = $101,000
                        BigDecimal expectedBalance = new BigDecimal("101000.00");

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Final balance should reflect buy cost minus sell proceeds");
                }

                @DisplayName("Multiple consecutive sells credit amounts correctly")
                @ParameterizedTest(name = "Sell {0} shares at {1}")
                @CsvSource({
                                "50, 160.00",
                                "30, 165.00",
                                "20, 170.00"
                })
                void testMultipleSellsCredit(int quantity, String price) {
                        // First buy 100 shares total
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-MULTI");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        BigDecimal balanceAfterBuy = testAccount.getCashBalance();

                        // Sell 50 at $160
                        PlaceOrderRequest sell1 = new PlaceOrderRequest(1L, "AAPL", OrderSide.SELL, 50,
                                        new BigDecimal("160.00"), "SELL-MULTI-1");
                        sellStrategy.execute(testAccount, sell1, "AAPL");

                        // Sell 30 at $165
                        PlaceOrderRequest sell2 = new PlaceOrderRequest(1L, "AAPL", OrderSide.SELL, 30,
                                        new BigDecimal("165.00"), "SELL-MULTI-2");
                        sellStrategy.execute(testAccount, sell2, "AAPL");

                        // Sell 20 at $170
                        PlaceOrderRequest sell3 = new PlaceOrderRequest(1L, "AAPL", OrderSide.SELL, 20,
                                        new BigDecimal("170.00"), "SELL-MULTI-3");
                        sellStrategy.execute(testAccount, sell3, "AAPL");

                        // Total proceeds: (50*160) + (30*165) + (20*170) = 8000 + 4950 + 3400 = 16350
                        BigDecimal expectedProceeds = new BigDecimal("16350.00");
                        BigDecimal expectedBalance = balanceAfterBuy.add(expectedProceeds);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Balance should reflect all sales proceeds");
                }
        }

        @DisplayName("Position Removal Tests")
        @Nested
        class PositionRemovalTests {
                @DisplayName("Position is removed when all holdings are sold")
                @Test
                void testPositionRemovedWhenFullySold() {
                        // Buy 100 shares
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-REMOVE");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        String positionKey = "1::AAPL";
                        assertTrue(positionsMap.containsKey(positionKey), "Position should exist after buy");

                        // Sell all 100 shares
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        new BigDecimal("160.00"),
                                        "SELL-REMOVE");
                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        assertFalse(positionsMap.containsKey(positionKey),
                                        "Position should be removed after selling all holdings");
                }

                @DisplayName("Position is updated with remaining quantity when partially sold")
                @Test
                void testPositionUpdatedWhenPartiallySold() {
                        // Buy 100 shares at $150
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-PARTIAL");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        String positionKey = "1::AAPL";
                        Position originalPosition = positionsMap.get(positionKey);
                        assertEquals(100, originalPosition.getQuantity(), "Should have 100 shares after buy");

                        // Sell 30 shares at $160
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        30,
                                        new BigDecimal("160.00"),
                                        "SELL-PARTIAL");
                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        Position updatedPosition = positionsMap.get(positionKey);
                        assertNotNull(updatedPosition, "Position should still exist");
                        assertEquals(70, updatedPosition.getQuantity(),
                                        "Position quantity should be updated to 70 (100 - 30)");
                }

                @DisplayName("Average cost is preserved when position is partially sold")
                @Test
                void testAverageCostPreservedAfterPartialSale() {
                        // Buy 100 shares at $150
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-COST");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        String positionKey = "1::AAPL";
                        Position originalPosition = positionsMap.get(positionKey);
                        BigDecimal originalAvgCost = originalPosition.getAverageCost();

                        // Sell 30 shares at higher price
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        30,
                                        new BigDecimal("160.00"),
                                        "SELL-COST");
                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        Position updatedPosition = positionsMap.get(positionKey);
                        BigDecimal newAvgCost = updatedPosition.getAverageCost();

                        // Average cost should remain the same (we're not buying more shares)
                        assertEquals(originalAvgCost, newAvgCost,
                                        "Average cost should be preserved when selling");
                }
        }

        @DisplayName("Edge Case Tests")
        @Nested
        class EdgeCaseTests {
                @DisplayName("Sell minimum quantity (1 share)")
                @Test
                void testSellMinimumQuantity() {
                        // Buy 1 share
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        1,
                                        new BigDecimal("150.00"),
                                        "BUY-MIN");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        // Sell 1 share
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        1,
                                        new BigDecimal("160.00"),
                                        "SELL-MIN");

                        assertDoesNotThrow(() -> sellStrategy.execute(testAccount, sellRequest, "AAPL"),
                                        "Should handle selling minimum quantity");

                        assertFalse(positionsMap.containsKey("1::AAPL"),
                                        "Position should be removed after selling single share");
                }

                @DisplayName("Sell with fractional prices (cents)")
                @Test
                void testSellFractionalPrices() {
                        // Buy at fractional price
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.25"),
                                        "BUY-FRAC");
                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        BigDecimal balanceAfterBuy = testAccount.getCashBalance();

                        // Sell at different fractional price
                        PlaceOrderRequest sellRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.SELL,
                                        100,
                                        new BigDecimal("160.75"), // Fractional price
                                        "SELL-FRAC");
                        sellStrategy.execute(testAccount, sellRequest, "AAPL");

                        BigDecimal expectedProceeds = new BigDecimal("160.75").multiply(BigDecimal.valueOf(100));
                        BigDecimal expectedBalance = balanceAfterBuy.add(expectedProceeds);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Should correctly handle fractional prices");
                }

                @DisplayName("Multiple symbols: selling one doesn't affect others")
                @Test
                void testSellMultipleSymbolsIndependently() {
                        // Buy AAPL and MSFT
                        PlaceOrderRequest buyAAPL = new PlaceOrderRequest(
                                        1L, "AAPL", OrderSide.BUY, 100, new BigDecimal("150.00"), "BUY-AAPL");
                        PlaceOrderRequest buyMSFT = new PlaceOrderRequest(
                                        1L, "MSFT", OrderSide.BUY, 50, new BigDecimal("300.00"), "BUY-MSFT");

                        buyStrategy.execute(testAccount, buyAAPL, "AAPL");
                        buyStrategy.execute(testAccount, buyMSFT, "MSFT");

                        // Sell only AAPL
                        PlaceOrderRequest sellAAPL = new PlaceOrderRequest(
                                        1L, "AAPL", OrderSide.SELL, 100, new BigDecimal("160.00"), "SELL-AAPL");
                        sellStrategy.execute(testAccount, sellAAPL, "AAPL");

                        // AAPL position should be gone
                        assertFalse(positionsMap.containsKey("1::AAPL"), "AAPL position should be removed");

                        // MSFT position should still exist
                        assertTrue(positionsMap.containsKey("1::MSFT"), "MSFT position should still exist");
                        assertEquals(50, positionsMap.get("1::MSFT").getQuantity(),
                                        "MSFT quantity should be unchanged");
                }
        }
}
