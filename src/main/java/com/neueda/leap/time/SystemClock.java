package com.neueda.leap.time;

import java.time.Instant;

public class SystemClock implements Clock {
    public static final SystemClock INSTANCE = new SystemClock();

    private SystemClock() {
        // Singleton
    }

    @Override
    public Instant now() {
        return Instant.now();
    }
}
