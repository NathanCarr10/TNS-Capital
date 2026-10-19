package com.neueda.leap.strategy.service;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import com.neueda.leap.strategy.backtest.BacktestResult;
import com.neueda.leap.strategy.backtest.Backtester;
import com.neueda.leap.strategy.backtest.DailyClose;
import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.domain.Strategy;
import com.neueda.leap.strategy.domain.StrategyRepository;
import com.neueda.leap.strategy.domain.StrategySignal;
import com.neueda.leap.strategy.domain.StrategySignalRepository;
import com.neueda.leap.strategy.domain.StrategyStatus;
import com.neueda.leap.strategy.errors.ActiveStrategyLimitException;
import com.neueda.leap.strategy.errors.InstrumentNotTradableException;
import com.neueda.leap.strategy.errors.NotOwnerException;
import com.neueda.leap.strategy.errors.PriceHistoryNotFoundException;
import com.neueda.leap.strategy.errors.StrategyNotFoundException;
import com.neueda.leap.strategy.errors.StrategyStateException;
import com.neueda.leap.strategy.marketdata.BarSeriesStore;
import com.neueda.leap.strategy.performance.PerformanceCalculator;
import com.neueda.leap.strategy.performance.PerformanceResponse;
import com.neueda.leap.strategy.domain.SignalOutcome;
import com.neueda.leap.strategy.prices.DailyPriceRepository;
import com.neueda.leap.strategy.rules.RuleFactory;
import com.neueda.leap.strategy.rules.TradingRule;
import com.neueda.leap.strategy.web.dto.BacktestRequest;
import com.neueda.leap.strategy.web.dto.CreateStrategyRequest;
import com.neueda.leap.strategy.web.dto.SignalResponse;
import com.neueda.leap.strategy.web.dto.StrategyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Strategy Lab: traders create strategies on their own account, backtest
 * rules, switch strategies on and off and follow what they did.
 *
 * Access: a trader sees and changes only the strategies on their own account.
 * Support staff see every strategy but change none, as they view traders'
 * accounts read-only in the trading app. Admins work in the analytics
 * dashboard, not here: with no account they see no strategies (docs/DECISIONS.md,
 * "Roles").
 */
@Service
public class StrategyService {
    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);
    static final BigDecimal DEFAULT_INITIAL_CASH = BigDecimal.valueOf(10_000);
    static final int DEFAULT_BACKTEST_YEARS = 5;

    private final StrategyRepository strategies;
    private final StrategySignalRepository signals;
    private final PlatformLookup platform;
    private final DailyPriceRepository prices;
    private final Backtester backtester;
    private final BarSeriesStore bars;
    private final StrategyProperties properties;
    private final Clock clock;
    private final PerformanceCalculator performance = new PerformanceCalculator();

    public StrategyService(StrategyRepository strategies, StrategySignalRepository signals, PlatformLookup platform,
            DailyPriceRepository prices, Backtester backtester, BarSeriesStore bars, StrategyProperties properties,
            Clock clock) {
        this.strategies = Objects.requireNonNull(strategies);
        this.signals = Objects.requireNonNull(signals);
        this.platform = Objects.requireNonNull(platform);
        this.prices = Objects.requireNonNull(prices);
        this.backtester = Objects.requireNonNull(backtester);
        this.bars = Objects.requireNonNull(bars);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Support gets every strategy that is not archived; traders their own.
     */
    public List<StrategyResponse> list(Caller caller) {
        List<Strategy> found = caller.support()
                ? strategies.findByStatusNotOrderByCreatedOnDesc(StrategyStatus.ARCHIVED)
                : caller.accountId()
                        .map(id -> strategies.findByAccountIdAndStatusNotOrderByCreatedOnDesc(id, StrategyStatus.ARCHIVED))
                        .orElse(List.of());
        return found.stream().map(this::toResponse).toList();
    }

    public StrategyResponse get(Caller caller, Long id) {
        return toResponse(visible(caller, id));
    }

    /**
     * Creates a PAUSED strategy on the caller's account.
     */
    @Transactional
    public StrategyResponse create(Caller caller, CreateStrategyRequest request) {
        Long accountId = caller.accountId()
                .orElseThrow(() -> new NotOwnerException("Only a trader with an account can create strategies"));
        if (!platform.isTradable(request.symbol())) {
            throw new InstrumentNotTradableException(request.symbol());
        }
        Map<String, Number> params = RuleFactory.withDefaults(request.ruleType(), request.params());
        Strategy strategy = strategies.save(new Strategy(accountId, request.name().trim(), request.symbol(),
                request.ruleType(), params, request.quantity()));
        String rule = strategy.rule().describe();
        log.info("Strategy {} created by {} on account {}: {} {}", strategy.getId(), caller.username(), accountId,
                request.symbol(), rule);
        return toResponse(strategy);
    }

    /**
     * Switches a strategy on (ACTIVE) or off (PAUSED). At most
     * strategy.max-active-per-account strategies per account can be on.
     */
    @Transactional
    public StrategyResponse changeStatus(Caller caller, Long id, StrategyStatus status) {
        Strategy strategy = owned(caller, id);
        if (strategy.getStatus() == StrategyStatus.ARCHIVED) {
            throw new StrategyStateException("An archived strategy cannot be switched on or off");
        }
        if (status == StrategyStatus.ARCHIVED) {
            throw new StrategyStateException("Delete the strategy to archive it");
        }
        if (status == StrategyStatus.ACTIVE && strategy.getStatus() != StrategyStatus.ACTIVE
                && strategies.countByAccountIdAndStatus(strategy.getAccountId(), StrategyStatus.ACTIVE)
                        >= properties.maxActivePerAccount()) {
            throw new ActiveStrategyLimitException(properties.maxActivePerAccount());
        }
        strategy.setStatus(status);
        log.info("Strategy {} switched {} by {}", id, status == StrategyStatus.ACTIVE ? "on" : "off",
                caller.username());
        return toResponse(strategies.save(strategy));
    }

    /**
     * Deletes a strategy for its owner. It is archived, not removed, so its
     * signals keep pointing at it.
     */
    @Transactional
    public void archive(Caller caller, Long id) {
        Strategy strategy = owned(caller, id);
        strategy.setStatus(StrategyStatus.ARCHIVED);
        strategies.save(strategy);
        log.info("Strategy {} deleted (archived) by {}", id, caller.username());
    }

    /**
     * The strategy's latest 100 signals, newest first, with what became of their orders.
     */
    public List<SignalResponse> signals(Caller caller, Long id) {
        Strategy strategy = visible(caller, id);
        return withOrderStatus(signals.findTop100ByStrategyIdOrderByCreatedOnDesc(id),
                Map.of(strategy.getId(), strategy));
    }

    /**
     * Signals of the caller's strategies since the given time, newest first, for
     * notifications; support gets every strategy's.
     */
    public List<SignalResponse> activity(Caller caller, Instant since) {
        List<Strategy> mine = caller.support()
                ? strategies.findAll()
                : caller.accountId().map(strategies::findByAccountId).orElse(List.of());
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, Strategy> byId = mine.stream().collect(Collectors.toMap(Strategy::getId, Function.identity()));
        return withOrderStatus(
                signals.findTop100ByStrategyIdInAndCreatedOnAfterOrderByCreatedOnDesc(byId.keySet(), since), byId);
    }

    /**
     * The account's strategy trading against the trader's own, from its filled
     * orders. Traders get their own account; support names one.
     */
    public PerformanceResponse performance(Caller caller, Long requestedAccountId) {
        Long accountId = caller.support()
                ? Optional.ofNullable(requestedAccountId)
                        .orElseThrow(() -> new NotOwnerException("Support chooses an account with accountId"))
                : caller.accountId().orElseThrow(() -> new NotOwnerException("Only a trader with an account has performance"));
        if (!caller.support() && requestedAccountId != null && !requestedAccountId.equals(accountId)) {
            throw new NotOwnerException("Account " + requestedAccountId + " is not yours");
        }
        List<Long> strategyIds = strategies.findByAccountId(accountId).stream().map(Strategy::getId).toList();
        java.util.Set<UUID> strategyOrders = signals.findByStrategyIdInAndOutcome(strategyIds, SignalOutcome.PLACED)
                .stream().map(StrategySignal::getOrderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return performance.compare(accountId, platform.filledOrders(accountId), strategyOrders);
    }

    /**
     * Backtests a rule on the daily price history the ETL loaded.
     */
    public BacktestResult backtest(BacktestRequest request) {
        TradingRule rule = RuleFactory.create(request.ruleType(), request.params());
        LocalDate to = Optional.ofNullable(request.to()).orElse(LocalDate.now(clock));
        LocalDate from = Optional.ofNullable(request.from()).orElse(to.minusYears(DEFAULT_BACKTEST_YEARS));
        if (!from.isBefore(to)) {
            throw new PriceHistoryNotFoundException("from must be before to");
        }
        List<DailyClose> closes;
        try {
            closes = prices.closes(request.symbol(), from, to);
        } catch (DataAccessException e) {
            // The ETL has not created analytics.fact_daily_prices yet
            log.warn("Price history unavailable: {}", e.getMessage());
            throw new PriceHistoryNotFoundException("No price history yet: the ETL loads it into the analytics schema");
        }
        if (closes.size() <= rule.barsNeeded()) {
            throw new PriceHistoryNotFoundException("Not enough daily prices for " + request.symbol() + " between "
                    + from + " and " + to + ": " + closes.size() + " found, the rule needs more than "
                    + rule.barsNeeded());
        }
        return backtester.run(request.symbol(), rule, closes,
                Optional.ofNullable(request.initialCash()).orElse(DEFAULT_INITIAL_CASH));
    }

    /** The strategy, if the caller may see it */
    private Strategy visible(Caller caller, Long id) {
        Strategy strategy = strategies.findById(id).orElseThrow(() -> new StrategyNotFoundException(id));
        if (!caller.support() && !caller.accountId().map(strategy.getAccountId()::equals).orElse(false)) {
            throw new NotOwnerException("Strategy " + id + " is not on your account");
        }
        return strategy;
    }

    /** The strategy, if the caller owns it */
    private Strategy owned(Caller caller, Long id) {
        Strategy strategy = strategies.findById(id).orElseThrow(() -> new StrategyNotFoundException(id));
        if (!caller.accountId().map(strategy.getAccountId()::equals).orElse(false)) {
            throw new NotOwnerException("Strategy " + id + " is not on your account");
        }
        return strategy;
    }

    private List<SignalResponse> withOrderStatus(List<StrategySignal> found, Map<Long, Strategy> owners) {
        Map<UUID, String> statuses = platform.orderStatuses(found.stream()
                .map(StrategySignal::getOrderId).filter(Objects::nonNull).toList());
        return found.stream().map(signal -> {
            Strategy strategy = owners.get(signal.getStrategyId());
            return new SignalResponse(signal.getId(), signal.getStrategyId(), strategy.getName(),
                    strategy.getSymbol(), signal.getSide(), signal.getPrice(), signal.getBarTime(),
                    signal.getSource(), signal.getOrderId(), signal.getOutcome(),
                    signal.getOrderId() == null ? null : statuses.get(signal.getOrderId()), signal.getDetail(),
                    signal.getCreatedOn());
        }).toList();
    }

    StrategyResponse toResponse(Strategy strategy) {
        TradingRule rule = strategy.rule();
        return new StrategyResponse(strategy.getId(), strategy.getAccountId(), strategy.getName(),
                strategy.getSymbol(), strategy.getRuleType(), strategy.getRuleParams(), rule.describe(),
                strategy.getQuantity(), strategy.getStatus(), strategy.getPosition(), rule.barsNeeded(),
                bars.view(strategy.getSymbol()), strategy.getCreatedOn(), strategy.getUpdatedOn());
    }
}
