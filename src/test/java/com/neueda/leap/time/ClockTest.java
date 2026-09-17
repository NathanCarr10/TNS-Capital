package com.neueda.leap.time;

import java.time.Instant;

public class ClockTest implements Clock {
    private Instant fixedInstant;

    public ClockTest(Instant fixedInstant) {
        this.fixedInstant = fixedInstant;
    }

    public ClockTest() {
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
