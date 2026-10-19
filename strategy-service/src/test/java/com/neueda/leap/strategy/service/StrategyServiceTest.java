package com.neueda.leap.strategy.service;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import com.neueda.leap.strategy.backtest.BacktestResult;
import com.neueda.leap.strategy.backtest.Backtester;
import com.neueda.leap.strategy.backtest.DailyClose;
import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.domain.SignalSource;
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
import com.neueda.leap.strategy.prices.DailyPriceRepository;
import com.neueda.leap.strategy.rules.InvalidRuleException;
import com.neueda.leap.strategy.rules.RuleType;
import com.neueda.leap.strategy.web.dto.BacktestRequest;
import com.neueda.leap.strategy.web.dto.CreateStrategyRequest;
import com.neueda.leap.strategy.web.dto.SignalResponse;
import com.neueda.leap.strategy.web.dto.StrategyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StrategyService")
class StrategyServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-19T14:00:00Z");
    private static final Caller TRADER = new Caller("trader", false, Optional.of(7L));
    private static final Caller OTHER = new Caller("someone", false, Optional.of(8L));
    private static final Caller SUPPORT = new Caller("support", true, Optional.empty());
    // Admins work in the analytics dashboard: no account, no strategies here
    private static final Caller ADMIN = new Caller("admin", false, Optional.empty());

    private StrategyRepository strategies;
    private StrategySignalRepository signals;
    private PlatformLookup platform;
    private DailyPriceRepository prices;
    private StrategyService service;
    private Strategy owned;

    @BeforeEach
    void setUp() {
        strategies = mock(StrategyRepository.class);
        signals = mock(StrategySignalRepository.class);
        platform = mock(PlatformLookup.class);
        prices = mock(DailyPriceRepository.class);
        StrategyProperties properties = new StrategyProperties(1, Duration.ofMinutes(2), 250, "http://api",
                "http://auth", "strategy-service", "x");
        service = new StrategyService(strategies, signals, platform, prices, new Backtester(),
                new BarSeriesStore(properties), properties, Clock.fixed(NOW, ZoneOffset.UTC));
        owned = strategy(1L, 7L);
        when(strategies.findById(1L)).thenReturn(Optional.of(owned));
        when(strategies.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static Strategy strategy(Long id, Long accountId) {
        Strategy strategy = new Strategy(accountId, "MA 20/50", "SPY", RuleType.MA_CROSSOVER,
                Map.of("shortWindow", 20, "longWindow", 50), 5);
        ReflectionTestUtils.setField(strategy, "id", id);
        return strategy;
    }

    @Test
    @DisplayName("Creates a PAUSED strategy on the trader's account, with the rule's defaults filled in")
    void createsStrategy() {
        when(platform.isTradable("SPY")).thenReturn(true);

        StrategyResponse created = service.create(TRADER, new CreateStrategyRequest(" Trend ", "SPY",
                RuleType.MA_CROSSOVER, Map.of("shortWindow", 10.0), 5));

        assertEquals(7L, created.accountId());
        assertEquals("Trend", created.name());
        assertEquals(StrategyStatus.PAUSED, created.status());
        assertEquals(Map.of("shortWindow", 10, "longWindow", 50), created.params());
        assertEquals("SMA 10 / SMA 50 crossover", created.rule());
        assertEquals(51, created.barsNeeded());
    }

    @Test
    @DisplayName("Refuses to create a strategy for support or an admin, on an untradable instrument or with bad settings")
    void refusesBadCreation() {
        CreateStrategyRequest onSpy = new CreateStrategyRequest("Trend", "SPY", RuleType.MA_CROSSOVER, null, 1);
        CreateStrategyRequest badRule = new CreateStrategyRequest("Trend", "SPY", RuleType.MA_CROSSOVER,
                Map.of("shortWindow", 60.0), 1);

        assertThrows(NotOwnerException.class, () -> service.create(SUPPORT, onSpy));
        assertThrows(NotOwnerException.class, () -> service.create(ADMIN, onSpy));
        assertThrows(InstrumentNotTradableException.class, () -> service.create(TRADER, onSpy));
        when(platform.isTradable("SPY")).thenReturn(true);
        assertThrows(InvalidRuleException.class, () -> service.create(TRADER, badRule));
        verify(strategies, never()).save(any());
    }

    @Test
    @DisplayName("Lists a trader's own strategies, every strategy for support and none for an admin")
    void listsStrategies() {
        when(strategies.findByAccountIdAndStatusNotOrderByCreatedOnDesc(7L, StrategyStatus.ARCHIVED))
                .thenReturn(List.of(owned));
        when(strategies.findByStatusNotOrderByCreatedOnDesc(StrategyStatus.ARCHIVED))
                .thenReturn(List.of(owned, strategy(2L, 8L)));

        assertEquals(1, service.list(TRADER).size());
        assertEquals(2, service.list(SUPPORT).size());
        assertTrue(service.list(ADMIN).isEmpty());
        assertTrue(service.list(new Caller("new", false, Optional.empty())).isEmpty());
    }

    @Test
    @DisplayName("Lets the owner and support see a strategy; others, admins included, get AUTH-403, a missing one STR-404")
    void checksVisibility() {
        assertEquals(1L, service.get(TRADER, 1L).id());
        assertEquals(1L, service.get(SUPPORT, 1L).id());
        assertThrows(NotOwnerException.class, () -> service.get(ADMIN, 1L));
        assertThrows(NotOwnerException.class, () -> service.get(OTHER, 1L));
        assertThrows(StrategyNotFoundException.class, () -> service.get(TRADER, 99L));
    }

    @Test
    @DisplayName("Switches a strategy on and off, one active strategy per account")
    void switchesOnAndOff() {
        assertEquals(StrategyStatus.ACTIVE, service.changeStatus(TRADER, 1L, StrategyStatus.ACTIVE).status());
        // Switching on an active strategy again does not count it twice
        when(strategies.countByAccountIdAndStatus(7L, StrategyStatus.ACTIVE)).thenReturn(1L);
        assertEquals(StrategyStatus.ACTIVE, service.changeStatus(TRADER, 1L, StrategyStatus.ACTIVE).status());
        assertEquals(StrategyStatus.PAUSED, service.changeStatus(TRADER, 1L, StrategyStatus.PAUSED).status());

        assertThrows(ActiveStrategyLimitException.class, () -> service.changeStatus(TRADER, 1L, StrategyStatus.ACTIVE));
    }

    @Test
    @DisplayName("Only the owner changes a strategy; an archived one stays archived")
    void protectsChanges() {
        assertThrows(NotOwnerException.class, () -> service.changeStatus(SUPPORT, 1L, StrategyStatus.PAUSED));
        assertThrows(NotOwnerException.class, () -> service.archive(OTHER, 1L));
        assertThrows(StrategyStateException.class, () -> service.changeStatus(TRADER, 1L, StrategyStatus.ARCHIVED));

        service.archive(TRADER, 1L);

        assertEquals(StrategyStatus.ARCHIVED, owned.getStatus());
        assertThrows(StrategyStateException.class, () -> service.changeStatus(TRADER, 1L, StrategyStatus.ACTIVE));
    }

    @Test
    @DisplayName("Shows each signal with its order's status now")
    void showsSignalsWithOrderStatus() {
        UUID orderId = UUID.randomUUID();
        StrategySignal placed = new StrategySignal(1L, "BUY", BigDecimal.TEN, NOW, SignalSource.REPLAY, "r-1");
        placed.placed(orderId);
        StrategySignal failed = new StrategySignal(1L, "SELL", BigDecimal.ONE, NOW, SignalSource.LIVE, null);
        failed.failed("x".repeat(300));
        when(signals.findTop100ByStrategyIdOrderByCreatedOnDesc(1L)).thenReturn(List.of(placed, failed));
        when(platform.orderStatuses(List.of(orderId))).thenReturn(Map.of(orderId, "FILLED"));

        List<SignalResponse> shown = service.signals(TRADER, 1L);

        assertEquals("FILLED", shown.get(0).orderStatus());
        assertEquals("MA 20/50", shown.get(0).strategyName());
        assertNull(shown.get(1).orderStatus());
        assertEquals(255, shown.get(1).detail().length());
    }

    @Test
    @DisplayName("Lists recent signals of the caller's strategies for notifications")
    void listsActivity() {
        when(strategies.findByAccountId(7L)).thenReturn(List.of(owned));
        StrategySignal recent = new StrategySignal(1L, "BUY", BigDecimal.TEN, NOW, SignalSource.LIVE, null);
        recent.placed(UUID.randomUUID());
        when(signals.findTop100ByStrategyIdInAndCreatedOnAfterOrderByCreatedOnDesc(java.util.Set.of(1L),
                NOW.minusSeconds(60))).thenReturn(List.of(recent));

        assertEquals(1, service.activity(TRADER, NOW.minusSeconds(60)).size());
        assertTrue(service.activity(new Caller("new", false, Optional.empty()), NOW).isEmpty());
    }

    @Test
    @DisplayName("Backtests the last five years by default, from 10,000 of cash")
    void backtestsDefaultPeriod() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        List<DailyClose> history = IntStream.range(0, 60)
                .mapToObj(i -> new DailyClose(today.minusDays(60L - i), BigDecimal.valueOf(100 + i % 7)))
                .toList();
        when(prices.closes("SPY", today.minusYears(5), today)).thenReturn(history);

        BacktestResult result = service.backtest(new BacktestRequest("SPY", RuleType.MA_CROSSOVER,
                Map.of("shortWindow", 5.0, "longWindow", 20.0), null, null, null));

        assertEquals(new BigDecimal("10000.00"), result.initialCash());
        assertEquals(60, result.equityCurve().size());
    }

    @Test
    @DisplayName("Reports PRC-404 without enough price history, or before the ETL has loaded any")
    void reportsMissingHistory() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        BacktestRequest request = new BacktestRequest("SPY", RuleType.MA_CROSSOVER, null, from, to, BigDecimal.TEN);
        BacktestRequest backwards = new BacktestRequest("SPY", RuleType.MA_CROSSOVER, null, to, from, null);
        when(prices.closes("SPY", from, to))
                .thenReturn(List.of(new DailyClose(from, BigDecimal.TEN)))
                .thenThrow(new DataAccessResourceFailureException("relation analytics.fact_daily_prices does not exist"));

        assertThrows(PriceHistoryNotFoundException.class, () -> service.backtest(request));
        assertThrows(PriceHistoryNotFoundException.class, () -> service.backtest(request));
        assertThrows(PriceHistoryNotFoundException.class, () -> service.backtest(backwards));
    }

    @Test
    @DisplayName("Compares the strategies' filled orders with the trader's own, for their account or one support names")
    void comparesPerformance() {
        UUID strategyOrder = UUID.randomUUID();
        StrategySignal placed = new StrategySignal(1L, "BUY", BigDecimal.TEN, NOW, SignalSource.LIVE, null);
        placed.placed(strategyOrder);
        when(strategies.findByAccountId(7L)).thenReturn(List.of(owned));
        when(signals.findByStrategyIdInAndOutcome(List.of(1L), com.neueda.leap.strategy.domain.SignalOutcome.PLACED))
                .thenReturn(List.of(placed));
        when(platform.filledOrders(7L)).thenReturn(List.of(
                new PlatformLookup.FilledOrder(strategyOrder, "SPY", "BUY", 2, BigDecimal.TEN, NOW),
                new PlatformLookup.FilledOrder(UUID.randomUUID(), "SPY", "BUY", 1, BigDecimal.ONE, NOW)));

        assertEquals(1, service.performance(TRADER, null).strategies().trades());
        assertEquals(1, service.performance(SUPPORT, 7L).manual().trades());
        assertThrows(NotOwnerException.class, () -> service.performance(SUPPORT, null));
        assertThrows(NotOwnerException.class, () -> service.performance(ADMIN, 7L));
        assertThrows(NotOwnerException.class, () -> service.performance(TRADER, 8L));
        assertThrows(NotOwnerException.class, () -> service.performance(new Caller("new", false, Optional.empty()), null));
    }
}
