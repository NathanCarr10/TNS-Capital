package com.neueda.leap.time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;

public class SystemClockTest {
    @Test
    public void testSystemClockReturnsInstant() {
        SystemClock clock = SystemClock.INSTANCE;
        Instant instant = clock.now();

        assertNotNull(instant);
    }

    @Test
    public void testSystemClockMonotonicallyIncreases() throws InterruptedException {
        SystemClock clock = SystemClock.INSTANCE;
        Instant first = clock.now();

        Thread.sleep(1);

        Instant second = clock.now();

        assertTrue(second.isAfter(first), "Second call to now() should be after first call");
    }
}
