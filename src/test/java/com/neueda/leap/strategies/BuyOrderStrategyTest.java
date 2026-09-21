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
import com.neueda.leap.exceptions.InsufficientFundsException;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.Position;
import com.neueda.leap.time.ClockTest;
import com.neueda.leap.repositories.impl.InMemoryPositionRepository;
import com.neueda.leap.repositories.PositionRepository;

@DisplayName("BuyOrderStrategy Test Suite")
class BuyOrderStrategyTest {
        private BuyOrderStrategy buyStrategy;
        private Map<String, Position> positionsMap;
        private PositionRepository positionRepository;
        private Account testAccount;
        private ClockTest testClock;

        @BeforeEach
        void setUp() {
                testClock = new ClockTest(Instant.parse("2026-09-17T10:00:00Z"));
                positionsMap = new HashMap<>();

                // Create test account with sufficient funds
                testAccount = new Account("ACC001", "John Doe", new BigDecimal("100000.00"), testClock);

                // Create position repository
                positionRepository = new InMemoryPositionRepository(positionsMap);

                // Initialize strategy with repository
                buyStrategy = new BuyOrderStrategy(positionRepository);
        }

        @DisplayName("Account Debit Tests")
        @Nested
        class AccountDebitTests {
                @DisplayName("Buy order debits correct amount from account")
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
                                        "BUY-001");

                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        BigDecimal expectedCost = price.multiply(BigDecimal.valueOf(quantity));
                        BigDecimal expectedBalance = initialBalance.subtract(expectedCost);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Account balance should be reduced by purchase cost");
                }

                @DisplayName("Buy with insufficient funds throws exception")
                @Test
                void testBuyWithInsufficientFundsThrowsException() {
                        Account poorAccount = new Account("ACC002", "Poor Trader", new BigDecimal("100.00"), testClock);

                        PlaceOrderRequest expensiveBuyRequest = new PlaceOrderRequest(
                                        2L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        1000, // Expensive purchase
                                        new BigDecimal("150.00"),
                                        "BUY-FAIL");

                        assertThrows(InsufficientFundsException.class,
                                        () -> buyStrategy.execute(poorAccount, expensiveBuyRequest, "AAPL"),
                                        "Should throw InsufficientFundsException when account balance insufficient");
                }

                @DisplayName("Multiple consecutive buys debit amounts correctly")
                @ParameterizedTest(name = "Buy {0} shares at {1}")
                @CsvSource({
                                "100, 150.00",
                                "50, 200.00",
                                "25, 300.00"
                })
                void testMultipleBuysDebitCorrectly(int quantity, String price) {
                        BigDecimal initialBalance = testAccount.getCashBalance();

                        PlaceOrderRequest request1 = new PlaceOrderRequest(1L, "AAPL", OrderSide.BUY, quantity,
                                        new BigDecimal(price), "BUY-MULTI-1");
                        PlaceOrderRequest request2 = new PlaceOrderRequest(1L, "MSFT", OrderSide.BUY, quantity,
                                        new BigDecimal(price), "BUY-MULTI-2");

                        buyStrategy.execute(testAccount, request1, "AAPL");
                        buyStrategy.execute(testAccount, request2, "MSFT");

                        BigDecimal totalCost = new BigDecimal(price)
                                        .multiply(BigDecimal.valueOf(quantity * 2));
                        BigDecimal expectedBalance = initialBalance.subtract(totalCost);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Account balance should reflect all purchase costs");
                }
        }

        @DisplayName("Position Creation Tests")
        @Nested
        class PositionCreationTests {
                @DisplayName("Buy order creates new position for symbol")
                @Test
                void testBuyCreatesNewPosition() {
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-NEW-POS");

                        buyStrategy.execute(testAccount, buyRequest, "AAPL");

                        String positionKey = "1::AAPL";
                        Position position = positionsMap.get(positionKey);

                        assertNotNull(position, "Position should be created for AAPL");
                        assertEquals(1L, position.getAccountId(), "Account ID should match");
                        assertEquals("AAPL", position.getSymbol(), "Symbol should be AAPL");
                        assertEquals(100, position.getQuantity(), "Quantity should be 100");
                }

                @DisplayName("Position key is correctly formatted with account ID and symbol")
                @Test
                void testPositionKeyFormat() {
                        PlaceOrderRequest buyRequest = new PlaceOrderRequest(
                                        42L, // Specific account ID
                                        "MSFT",
                                        OrderSide.BUY,
                                        50,
                                        new BigDecimal("300.00"),
                                        "BUY-KEY-FORMAT");

                        Account specialAccount = new Account("ACC042", "Special", new BigDecimal("50000.00"),
                                        testClock);

                        buyStrategy.execute(specialAccount, buyRequest, "MSFT");

                        String expectedKey = "42::MSFT";
                        assertTrue(positionsMap.containsKey(expectedKey),
                                        "Position should be stored with key format 'accountId::symbol'");
                }
        }

        @DisplayName("Position Update Tests")
        @Nested
        class PositionUpdateTests {
                @DisplayName("Buying same symbol updates existing position (averaging)")
                @Test
                void testBuySameSymbolUpdatesPosition() {
                        // First buy: 100 shares at $150
                        PlaceOrderRequest buy1 = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-UPDATE-1");

                        buyStrategy.execute(testAccount, buy1, "AAPL");

                        String positionKey = "1::AAPL";
                        Position positionAfterBuy1 = positionsMap.get(positionKey);
                        assertEquals(100, positionAfterBuy1.getQuantity(), "First buy should have 100 shares");

                        // Second buy: 50 shares at $160 (higher price)
                        PlaceOrderRequest buy2 = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        50,
                                        new BigDecimal("160.00"),
                                        "BUY-UPDATE-2");

                        buyStrategy.execute(testAccount, buy2, "AAPL");

                        Position positionAfterBuy2 = positionsMap.get(positionKey);
                        assertEquals(150, positionAfterBuy2.getQuantity(),
                                        "Second buy should increase quantity to 150");
                        // Average cost should be (100*150 + 50*160) / 150 = 153.33
                }

                @DisplayName("Buying different symbols creates separate positions")
                @Test
                void testBuyDifferentSymbolsCreateSeparatePositions() {
                        // Buy AAPL
                        PlaceOrderRequest buyAAPL = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.00"),
                                        "BUY-AAPL");

                        // Buy MSFT
                        PlaceOrderRequest buyMSFT = new PlaceOrderRequest(
                                        1L,
                                        "MSFT",
                                        OrderSide.BUY,
                                        50,
                                        new BigDecimal("300.00"),
                                        "BUY-MSFT");

                        buyStrategy.execute(testAccount, buyAAPL, "AAPL");
                        buyStrategy.execute(testAccount, buyMSFT, "MSFT");

                        Position aaplPosition = positionsMap.get("1::AAPL");
                        Position msftPosition = positionsMap.get("1::MSFT");

                        assertNotNull(aaplPosition, "AAPL position should exist");
                        assertNotNull(msftPosition, "MSFT position should exist");
                        assertEquals(100, aaplPosition.getQuantity(), "AAPL quantity should be 100");
                        assertEquals(50, msftPosition.getQuantity(), "MSFT quantity should be 50");
                }
        }

        @DisplayName("Edge Case Tests")
        @Nested
        class EdgeCaseTests {
                @DisplayName("Buy with minimum quantity (1 share)")
                @Test
                void testBuyMinimumQuantity() {
                        PlaceOrderRequest minBuy = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        1,
                                        new BigDecimal("150.00"),
                                        "BUY-MIN");

                        buyStrategy.execute(testAccount, minBuy, "AAPL");

                        Position position = positionsMap.get("1::AAPL");
                        assertNotNull(position, "Position should exist for 1 share");
                        assertEquals(1, position.getQuantity(), "Quantity should be 1");
                }

                @DisplayName("Buy with very large quantity")
                @Test
                void testBuyLargeQuantity() {
                        // Create account with enough funds for large purchase
                        // 100k shares at $150 = $15,000,000 needed
                        Account richAccount = new Account("ACC-RICH", "Rich Trader", new BigDecimal("20000000.00"),
                                        testClock);

                        PlaceOrderRequest largeBuy = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100000, // 100k shares
                                        new BigDecimal("150.00"),
                                        "BUY-LARGE");

                        buyStrategy.execute(richAccount, largeBuy, "AAPL");

                        Position position = positionsMap.get("1::AAPL");
                        assertEquals(100000, position.getQuantity(), "Position should handle large quantities");
                }

                @DisplayName("Buy with fractional prices (cents)")
                @Test
                void testBuyFractionalPrices() {
                        PlaceOrderRequest fractionalBuy = new PlaceOrderRequest(
                                        1L,
                                        "AAPL",
                                        OrderSide.BUY,
                                        100,
                                        new BigDecimal("150.25"), // Price with cents
                                        "BUY-FRAC");

                        BigDecimal initialBalance = testAccount.getCashBalance();
                        buyStrategy.execute(testAccount, fractionalBuy, "AAPL");

                        BigDecimal expectedCost = new BigDecimal("150.25").multiply(BigDecimal.valueOf(100));
                        BigDecimal expectedBalance = initialBalance.subtract(expectedCost);

                        assertEquals(expectedBalance, testAccount.getCashBalance(),
                                        "Should handle fractional prices correctly");
                }
        }
}
