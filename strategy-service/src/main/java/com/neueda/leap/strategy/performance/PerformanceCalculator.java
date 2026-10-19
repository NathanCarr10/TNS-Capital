package com.neueda.leap.strategy.performance;

import com.neueda.leap.strategy.accounts.PlatformLookup.FilledOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Compares the strategies' trading with the trader's own (proposal stretch:
 * strategy P&L compared with the trader's own trading).
 *
 * Each side keeps its own book per symbol at average cost: a BUY adds shares
 * at its price; a SELL realizes (price - average cost) on the shares sold.
 * A side that sells more than it bought (e.g. the trader selling shares a
 * strategy bought) realizes only on the shares it held.
 */
public class PerformanceCalculator {

    /**
     * @param filled         the account's filled orders, oldest first
     * @param strategyOrders the ids of the orders its strategies placed
     */
    public PerformanceResponse compare(Long accountId, List<FilledOrder> filled, Set<UUID> strategyOrders) {
        Book strategies = new Book();
        Book manual = new Book();
        filled.forEach(order -> (strategyOrders.contains(order.id()) ? strategies : manual).apply(order));
        return new PerformanceResponse(accountId, strategies.result(), manual.result());
    }

    private static final class Book {
        private final Map<String, Holding> holdings = new HashMap<>();
        private int trades;
        private BigDecimal bought = BigDecimal.ZERO;
        private BigDecimal sold = BigDecimal.ZERO;
        private BigDecimal realized = BigDecimal.ZERO;

        void apply(FilledOrder order) {
            trades++;
            BigDecimal value = order.price().multiply(BigDecimal.valueOf(order.quantity()));
            Holding holding = holdings.computeIfAbsent(order.symbol(), symbol -> new Holding());
            if ("BUY".equals(order.side())) {
                bought = bought.add(value);
                holding.shares += order.quantity();
                holding.cost = holding.cost.add(value);
                return;
            }
            sold = sold.add(value);
            int closed = Math.min(order.quantity(), holding.shares);
            if (closed == 0) {
                return;
            }
            BigDecimal averageCost = holding.cost.divide(BigDecimal.valueOf(holding.shares), 8, RoundingMode.HALF_UP);
            BigDecimal closedCost = averageCost.multiply(BigDecimal.valueOf(closed));
            realized = realized.add(order.price().multiply(BigDecimal.valueOf(closed)).subtract(closedCost));
            holding.shares -= closed;
            holding.cost = holding.shares == 0 ? BigDecimal.ZERO : holding.cost.subtract(closedCost);
        }

        BookPerformance result() {
            BigDecimal open = holdings.values().stream().map(h -> h.cost).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new BookPerformance(trades, money(bought), money(sold), money(realized), money(open));
        }

        private static BigDecimal money(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static final class Holding {
        private int shares;
        private BigDecimal cost = BigDecimal.ZERO;
    }
}
