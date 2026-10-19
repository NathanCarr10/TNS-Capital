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
import com.neueda.leap.strategy.rules.RuleType;
import com.neueda.leap.strategy.trading.MarketOrder;
import com.neueda.leap.strategy.trading.OrderRefusedException;
import com.neueda.leap.strategy.trading.TradingApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StrategyEngine")
class StrategyEngineTest {
    private static final Instant BAR_TIME = Instant.parse("2026-10-19T14:31:00Z");
    // SMA 2 / SMA 4: these closes cross upwards at the last bar, the second set downwards
    private static final List<Double> CROSS_UP = List.of(11.0, 11.0, 10.0, 10.0, 12.0);
    private static final List<Double> CROSS_DOWN = List.of(9.0, 9.0, 10.0, 10.0, 8.0);

    private StrategyRepository strategies;
    private StrategySignalRepository signals;
    private PlatformLookup platform;
    private TradingApiClient tradingApi;
    private StrategyEngine engine;
    private Strategy strategy;

    @BeforeEach
    void setUp() {
        strategies = mock(StrategyRepository.class);
        signals = mock(StrategySignalRepository.class);
        platform = mock(PlatformLookup.class);
        tradingApi = mock(TradingApiClient.class);
        engine = new StrategyEngine(strategies, signals, platform, tradingApi);
        strategy = new Strategy(7L, "MA 2/4", "SPY", RuleType.MA_CROSSOVER, Map.of("shortWindow", 2, "longWindow", 4), 5);
        ReflectionTestUtils.setField(strategy, "id", 42L);
        strategy.setStatus(StrategyStatus.ACTIVE);
        when(strategies.findBySymbolAndStatus("SPY", StrategyStatus.ACTIVE)).thenReturn(List.of(strategy));
        when(signals.findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(42L, SignalOutcome.PLACED))
                .thenReturn(Optional.empty());
    }

    private static ClosedBar bar(List<Double> closes) {
        return new ClosedBar("SPY", SignalSource.LIVE, null, BAR_TIME, BigDecimal.valueOf(closes.get(closes.size() - 1)),
                closes);
    }

    private StrategySignal savedSignal() {
        ArgumentCaptor<StrategySignal> saved = ArgumentCaptor.forClass(StrategySignal.class);
        verify(signals).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("Places a market BUY through the trading API when the rule fires and it holds nothing")
    void buysWhenFlat() {
        UUID orderId = UUID.randomUUID();
        when(tradingApi.place(any())).thenReturn(orderId);

        engine.onBar(bar(CROSS_UP));

        ArgumentCaptor<MarketOrder> order = ArgumentCaptor.forClass(MarketOrder.class);
        verify(tradingApi).place(order.capture());
        assertEquals(new MarketOrder(7L, "SPY", "BUY", 5, "stg-42-L-" + BAR_TIME.getEpochSecond() / 60 + "-BUY",
                BigDecimal.valueOf(12.0)), order.getValue());
        verify(strategies).updatePosition(42L, Position.LONG);
        StrategySignal signal = savedSignal();
        assertEquals(SignalOutcome.PLACED, signal.getOutcome());
        assertEquals(orderId, signal.getOrderId());
        assertEquals(BAR_TIME, signal.getBarTime());
    }

    @Test
    @DisplayName("Sells when the rule fires and it holds its shares")
    void sellsWhenLong() {
        strategy.setPosition(Position.LONG);
        when(tradingApi.place(any())).thenReturn(UUID.randomUUID());

        engine.onBar(bar(CROSS_DOWN));

        verify(strategies).updatePosition(42L, Position.FLAT);
        assertEquals("SELL", savedSignal().getSide());
    }

    @Test
    @DisplayName("Does nothing on a SELL while flat, a BUY while long, or no signal")
    void ignoresSignalsThatDoNotApply() {
        engine.onBar(bar(CROSS_DOWN));
        engine.onBar(bar(List.of(10.0, 11.0, 12.0, 13.0, 14.0)));
        strategy.setPosition(Position.LONG);
        engine.onBar(bar(CROSS_UP));

        verify(tradingApi, never()).place(any());
        verify(signals, never()).save(any());
    }

    @Test
    @DisplayName("Records why the API refused the order, and keeps its position")
    void recordsRefusal() {
        when(tradingApi.place(any())).thenThrow(new OrderRefusedException("ACC-403", "The account is not active"));

        engine.onBar(bar(CROSS_UP));

        StrategySignal signal = savedSignal();
        assertEquals(SignalOutcome.FAILED, signal.getOutcome());
        assertEquals("ACC-403 The account is not active", signal.getDetail());
        assertNull(signal.getOrderId());
        verify(strategies, never()).updatePosition(anyLong(), any());
    }

    @Test
    @DisplayName("Treats its last BUY as undone when that order was rejected, so it can buy again")
    void reconcilesRejectedOrder() {
        strategy.setPosition(Position.LONG);
        StrategySignal lastBuy = new StrategySignal(42L, "BUY", BigDecimal.TEN, BAR_TIME.minusSeconds(600),
                SignalSource.LIVE, null);
        UUID rejected = UUID.randomUUID();
        lastBuy.placed(rejected);
        when(signals.findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(42L, SignalOutcome.PLACED))
                .thenReturn(Optional.of(lastBuy));
        when(platform.orderStatuses(List.of(rejected))).thenReturn(Map.of(rejected, "REJECTED"));
        when(tradingApi.place(any())).thenReturn(UUID.randomUUID());

        engine.onBar(bar(CROSS_UP));

        verify(strategies).updatePosition(42L, Position.FLAT);
        verify(strategies).updatePosition(42L, Position.LONG);
        assertEquals("BUY", savedSignal().getSide());
    }

    @Test
    @DisplayName("Keeps its position while its last order is open or filled")
    void keepsPositionOfExecutedOrder() {
        StrategySignal lastSell = new StrategySignal(42L, "SELL", BigDecimal.TEN, BAR_TIME, SignalSource.LIVE, null);
        UUID filled = UUID.randomUUID();
        lastSell.placed(filled);
        when(signals.findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(42L, SignalOutcome.PLACED))
                .thenReturn(Optional.of(lastSell));
        when(platform.orderStatuses(List.of(filled))).thenReturn(Map.of(filled, "FILLED"));

        assertEquals(Position.FLAT, engine.reconciledPosition(strategy));
        verify(strategies, never()).updatePosition(anyLong(), any());
    }

    @Test
    @DisplayName("Counts its last order as executed while the API has not stored it yet")
    void keepsPositionOfUnknownOrder() {
        strategy.setPosition(Position.LONG);
        StrategySignal lastBuy = new StrategySignal(42L, "BUY", BigDecimal.TEN, BAR_TIME, SignalSource.LIVE, null);
        lastBuy.placed(UUID.randomUUID());
        when(signals.findFirstByStrategyIdAndOutcomeOrderByCreatedOnDesc(42L, SignalOutcome.PLACED))
                .thenReturn(Optional.of(lastBuy));
        when(platform.orderStatuses(any())).thenReturn(Map.of());

        assertEquals(Position.LONG, engine.reconciledPosition(strategy));
    }

    @Test
    @DisplayName("Survives a redelivered bar it already acted on, and one strategy's failure")
    void survivesFailures() {
        Strategy broken = new Strategy(8L, "Broken", "SPY", RuleType.MA_CROSSOVER, Map.of("shortWindow", 50), 1);
        when(strategies.findBySymbolAndStatus("SPY", StrategyStatus.ACTIVE)).thenReturn(List.of(broken, strategy));
        when(tradingApi.place(any())).thenReturn(UUID.randomUUID());
        when(signals.save(any())).thenThrow(new DataIntegrityViolationException("duplicate bar"));

        engine.onBar(bar(CROSS_UP));

        verify(tradingApi).place(any());
    }

    @Test
    @DisplayName("Makes the idempotency key of a replayed bar unique to its replay")
    void keysReplayedBars() {
        ClosedBar replayed = new ClosedBar("SPY", SignalSource.REPLAY, "0a1b2c3d-0000-0000-0000-000000000000",
                BAR_TIME, BigDecimal.TEN, CROSS_UP);

        assertEquals("stg-42-R0a1b2c3d-" + BAR_TIME.getEpochSecond() / 60 + "-SELL",
                StrategyEngine.idempotencyKey(strategy, "SELL", replayed));
    }
}
