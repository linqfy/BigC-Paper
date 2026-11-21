package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Lets a repeated failure be logged at most once per interval for each key; the rest are only counted. */
public final class LogThrottle {
    private final long intervalNanos;
    private final LongSupplier nanos;
    private final Map<String, long[]> state = new ConcurrentHashMap<>();

    public LogThrottle(long intervalMillis) {
        this(intervalMillis, System::nanoTime);
    }

    LogThrottle(long intervalMillis, LongSupplier nanos) {
        this.intervalNanos = intervalMillis * 1_000_000L;
        this.nanos = nanos;
    }

    /**
     * Returns how many failures of the key were suppressed since the last logged one, plus the current one when it may
     * be logged now, or -1 when it must stay quiet.
     */
    public long tryLog(String key) {
        long now = nanos.getAsLong();
        long[] entry = state.computeIfAbsent(key, ignored -> new long[] {0L, 0L, 0L});
        synchronized (entry) {
            if (entry[2] != 0L && now - entry[0] < intervalNanos) {
                entry[1]++;
                return -1L;
            }
            long suppressed = entry[1];
            entry[0] = now;
            entry[1] = 0L;
            entry[2] = 1L;
            return suppressed;
        }
    }
}
