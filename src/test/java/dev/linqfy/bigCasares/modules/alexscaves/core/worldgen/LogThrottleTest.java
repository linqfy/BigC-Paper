package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogThrottleTest {

    @Test
    void logsOncePerIntervalAndCountsTheSuppressed() {
        AtomicLong clock = new AtomicLong(5_000_000_000L);
        LogThrottle throttle = new LogThrottle(1000, clock::get);
        assertEquals(0, throttle.tryLog("a"));
        assertEquals(-1, throttle.tryLog("a"));
        assertEquals(-1, throttle.tryLog("a"));
        assertEquals(0, throttle.tryLog("b"), "each key has its own interval");
        clock.addAndGet(1_000_000_000L);
        assertEquals(2, throttle.tryLog("a"));
        assertEquals(-1, throttle.tryLog("a"));
    }
}
