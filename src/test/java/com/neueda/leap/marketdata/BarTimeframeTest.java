package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BarTimeframe")
class BarTimeframeTest {

    @Test
    @DisplayName("Parses the timeframes Alpaca understands")
    void parsesSupportedValues() {
        assertEquals(BarTimeframe.MINUTES_15, BarTimeframe.fromValue("15Min"));
        assertEquals(BarTimeframe.HOUR_1, BarTimeframe.fromValue("1Hour"));
        assertEquals(BarTimeframe.DAY_1, BarTimeframe.fromValue("1Day"));
    }

    @Test
    @DisplayName("Rejects any other value")
    void rejectsUnsupportedValue() {
        assertThrows(IllegalArgumentException.class, () -> BarTimeframe.fromValue("1Week"));
        assertThrows(IllegalArgumentException.class, () -> BarTimeframe.fromValue("1day"));
    }
}
