package com.neueda.leap.strategy.engine;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderSettlement")
class OrderSettlementTest {
    private static final UUID ORDER = UUID.randomUUID();

    private PlatformLookup platform;
    private OrderSettlement settlement;

    @BeforeEach
    void setUp() {
        platform = mock(PlatformLookup.class);
        settlement = new OrderSettlement(platform, Duration.ofMillis(200), Duration.ofMillis(10));
    }

    @Test
    @DisplayName("Waits while the order is open or not stored yet, and returns how it ended")
    void waitsForFinalStatus() {
        when(platform.orderStatuses(List.of(ORDER)))
                .thenReturn(Map.of())
                .thenReturn(Map.of(ORDER, "NEW"))
                .thenReturn(Map.of(ORDER, "REJECTED"));

        assertEquals(Optional.of("REJECTED"), settlement.await(ORDER));
        verify(platform, times(3)).orderStatuses(List.of(ORDER));
    }

    @Test
    @DisplayName("Gives up after the timeout")
    void givesUp() {
        when(platform.orderStatuses(List.of(ORDER))).thenReturn(Map.of(ORDER, "NEW"));

        assertTrue(settlement.await(ORDER).isEmpty());
    }

    @Test
    @DisplayName("Stops waiting when its thread is interrupted")
    void stopsWhenInterrupted() {
        when(platform.orderStatuses(List.of(ORDER))).thenReturn(Map.of(ORDER, "NEW"));
        Thread.currentThread().interrupt();
        try {
            assertTrue(settlement.await(ORDER).isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            assertTrue(Thread.interrupted());
            assertFalse(Thread.currentThread().isInterrupted());
        }
    }
}
