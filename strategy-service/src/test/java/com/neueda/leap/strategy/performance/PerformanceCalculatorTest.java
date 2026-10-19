package com.neueda.leap.strategy.performance;

import com.neueda.leap.strategy.accounts.PlatformLookup.FilledOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("PerformanceCalculator")
class PerformanceCalculatorTest {
    private static final Instant T = Instant.parse("2026-10-16T14:00:00Z");
    private final PerformanceCalculator calculator = new PerformanceCalculator();

    private static FilledOrder order(String side, int quantity, String price) {
        return new FilledOrder(UUID.randomUUID(), "SPY", side, quantity, new BigDecimal(price), T);
    }

    private static BookPerformance book(int trades, String bought, String sold, String realized, String open) {
        return new BookPerformance(trades, new BigDecimal(bought), new BigDecimal(sold), new BigDecimal(realized),
                new BigDecimal(open));
    }

    @Test
    @DisplayName("Splits the strategies' orders from the trader's and realizes P&L at average cost")
    void comparesBooks() {
        FilledOrder strategyBuy = order("BUY", 2, "100");
        FilledOrder strategySell = order("SELL", 2, "110");
        List<FilledOrder> filled = List.of(order("BUY", 1, "90"), strategyBuy, order("BUY", 1, "110"), strategySell,
                order("SELL", 1, "120"));

        PerformanceResponse result = calculator.compare(7L, filled, Set.of(strategyBuy.id(), strategySell.id()));

        assertEquals(7L, result.accountId());
        assertEquals(book(2, "200.00", "220.00", "20.00", "0.00"), result.strategies());
        // Average cost 100: one sold at 120 realizes 20, one share left at cost 100
        assertEquals(book(3, "200.00", "120.00", "20.00", "100.00"), result.manual());
    }

    @Test
    @DisplayName("Realizes only on shares the book held")
    void ignoresSalesBeyondHoldings() {
        List<FilledOrder> filled = List.of(order("BUY", 1, "100"), order("SELL", 3, "90"), order("SELL", 1, "95"));

        PerformanceResponse result = calculator.compare(7L, filled, Set.of());

        assertEquals(book(3, "100.00", "365.00", "-10.00", "0.00"), result.manual());
        assertEquals(book(0, "0.00", "0.00", "0.00", "0.00"), result.strategies());
    }
}
