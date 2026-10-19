package com.neueda.leap.strategy.engine;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import com.neueda.leap.strategy.domain.Position;
import com.neueda.leap.strategy.domain.SignalOutcome;
import com.neueda.leap.strategy.domain.SignalSource;
import com.neueda.leap.strategy.domain.Strategy;
import com.neueda.leap.strategy.domain.StrategyRepository;
import com.neueda.leap.strategy.domain.StrategySignal;
import com.neueda.leap.strategy.domain.StrategySignalRepository;
import com.neueda.leap.strategy.domain.StrategyStatus;
import com.neueda.leap.strategy.marketdata.ClosedBar;
import com.neueda.leap.strategy.rules.Signal;
import com.neueda.leap.strategy.rules.TradingRule;
import com.neueda.leap.strategy.trading.MarketOrder;
import com.neueda.leap.strategy.trading.OrderRefusedException;
import com.neueda.leap.strategy.trading.TradingApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs the ACTIVE strategies on each closed 1-minute bar of their symbol.
 *
 * A strategy buys its quantity on BUY only when FLAT, and sells it on SELL
 * only when LONG, through the trading API. Each signal is stored with the
 * order it placed, or why it failed. The position follows the order: if the
 * order the strategy last placed ends REJECTED or CANCELLED, the position
 * goes back to what it was before it.
 */
@Component
public class StrategyEngine {
    private static final Logger log = LoggerFactory.getLogger(StrategyEngine.class);
    private static final Set<String> NOT_EXECUTED = Set.of("REJECTED", "CANCELLED");

    private final StrategyRepository strategies;
    private final StrategySignalRepository signals;
    private final PlatformLookup platform;
    private final TradingApiClient tradingApi;

    public StrategyEngine(StrategyRepository strategies, StrategySignalRepository signals, PlatformLookup platform,
            TradingApiClient tradingApi) {
        this.strategies = Objects.requireNonNull(strategies);
        this.signals = Objects.requireNonNull(signals);
        this.platform = Objects.requireNonNull(platform);
        this.tradingApi = Objects.requireNonNull(tradingApi);
    }

    public void onBar(ClosedBar bar) {
        List<Strategy> active = strategies.findBySymbolAndStatus(bar.symbol(), StrategyStatus.ACTIVE);
        for (Strategy strategy : active) {
            try {
                evaluate(strategy, bar);
            } catch (RuntimeException e) {
                // One strategy's failure must not stop the others
                log.error("Strategy {} failed on the {} bar of {}", strategy.getId(), bar.time(), bar.symbol(), e);
            }
        }
    }

    private void evaluate(Strategy strategy, ClosedBar bar) {
        TradingRule rule = strategy.rule();
        Signal signal = rule.evaluate(bar.closes());
        if (signal == Signal.HOLD) {
            return;
        }
        Position held = reconciledPosition(strategy);
        if (signal == Signal.BUY && held == Position.FLAT) {
            trade(strategy, "BUY", Position.LONG, bar);
        } else if (signal == Signal.SELL && held == Position.LONG) {
            trade(strategy, "SELL", Position.FLAT, bar);
        }
    }

    /**
     * The strategy's position, corrected if the last order it placed was not executed.
     */
    Position reconciledPosition(Strategy strategy) {
        Optional<StrategySignal> last = signals.findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(
                strategy.getId(), SignalOutcome.PLACED);
        if (last.isEmpty() || last.get().getOrderId() == null) {
            return strategy.getPosition();
        }
        // No status: the order is not in the database yet; it was accepted, so count it
        String status = platform.orderStatuses(List.of(last.get().getOrderId())).get(last.get().getOrderId());
        if (status == null || !NOT_EXECUTED.contains(status)) {
            return strategy.getPosition();
        }
        Position before = "BUY".equals(last.get().getSide()) ? Position.FLAT : Position.LONG;
        if (before != strategy.getPosition()) {
            log.info("Strategy {}: its last {} order was {}, so its position is {} again", strategy.getId(),
                    last.get().getSide(), status, before);
            strategies.updatePosition(strategy.getId(), before);
        }
        return before;
    }

    private void trade(Strategy strategy, String side, Position after, ClosedBar bar) {
        StrategySignal signal = new StrategySignal(strategy.getId(), side, bar.close(), bar.time(), bar.source(),
                bar.replayId());
        MarketOrder order = new MarketOrder(strategy.getAccountId(), strategy.getSymbol(), side,
                strategy.getQuantity(), idempotencyKey(strategy, side, bar), bar.close());
        try {
            UUID orderId = tradingApi.place(order);
            signal.placed(orderId);
            strategies.updatePosition(strategy.getId(), after);
            log.info("Strategy {} placed {} {} x{} for account {} (order {})", strategy.getId(), side,
                    strategy.getSymbol(), strategy.getQuantity(), strategy.getAccountId(), orderId);
        } catch (OrderRefusedException e) {
            signal.failed(e.getErrorCode() + " " + e.getMessage());
            log.warn("Strategy {}: the trading API refused its {} order: {} {}", strategy.getId(), side,
                    e.getErrorCode(), e.getMessage());
        }
        try {
            signals.save(signal);
        } catch (DataIntegrityViolationException e) {
            // The bar was already handled: a redelivered market-data message
            log.info("Strategy {} already acted on the {} bar of {}", strategy.getId(), bar.time(), bar.symbol());
        }
    }

    /**
     * Unique per strategy, bar and side, so a redelivered bar cannot place a second
     * order: the API refuses a key it has seen (ORD-409).
     */
    static String idempotencyKey(Strategy strategy, String side, ClosedBar bar) {
        String source = bar.source() == SignalSource.LIVE ? "L" : "R" + bar.replayId().substring(0, 8);
        return "stg-" + strategy.getId() + "-" + source + "-" + bar.time().getEpochSecond() / 60 + "-" + side;
    }
}
