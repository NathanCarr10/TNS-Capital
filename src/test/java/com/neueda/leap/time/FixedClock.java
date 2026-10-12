package com.neueda.leap.time;

import java.time.Instant;

public class FixedClock implements Clock {
    private Instant fixedInstant;

    public FixedClock(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
    }

    public FixedClock() {
        this.fixedInstant = Instant.now();
    }

    @Override
    public Instant now() {
        return fixedInstant;
    }

    public void setNow(Instant instant) {
        this.fixedInstant = instant;
    }

    public void incrementSeconds(long seconds) {
        this.fixedInstant = fixedInstant.plusSeconds(seconds);
    }
}
