package com.neueda.leap.time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SystemClockTest {
    @Test
    void testSystemClockReturnsInstant() {
        SystemClock clock = SystemClock.INSTANCE;
        Instant instant = clock.now();

        assertNotNull(instant);
    }

    @Test
    void testSystemClockNeverGoesBackwards() {
        SystemClock clock = SystemClock.INSTANCE;
        Instant first = clock.now();
        Instant second = clock.now();

        assertFalse(second.isBefore(first), "Second call to now() should not be before first call");
    }
}
