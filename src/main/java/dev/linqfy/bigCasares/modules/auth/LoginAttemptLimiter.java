package dev.linqfy.bigCasares.modules.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Locks an address out after too many failed logins inside a sliding window. Thread-safe. */
public final class LoginAttemptLimiter {

    private final int maxFailures;
    private final long windowMillis;
    private final long lockMillis;
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(int maxFailures, long windowMillis, long lockMillis) {
        this.maxFailures = maxFailures;
        this.windowMillis = windowMillis;
        this.lockMillis = lockMillis;
    }

    /** Records a failure; returns {@code true} when this failure locked the address. */
    public boolean recordFailure(String addressKey, long nowMillis) {
        Deque<Long> recent = failures.computeIfAbsent(addressKey, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent, nowMillis);
            recent.addLast(nowMillis);
            if (recent.size() >= maxFailures) {
                recent.clear();
                lockedUntil.put(addressKey, nowMillis + lockMillis);
                return true;
            }
            return false;
        }
    }

    public void recordSuccess(String addressKey) {
        failures.remove(addressKey);
    }

    /** Remaining lock in milliseconds, or 0 when the address may try again. */
    public long lockRemainingMillis(String addressKey, long nowMillis) {
        Long until = lockedUntil.get(addressKey);
        if (until == null) {
            return 0L;
        }
        if (until <= nowMillis) {
            lockedUntil.remove(addressKey, until);
            return 0L;
        }
        return until - nowMillis;
    }

    public void lock(String addressKey, long nowMillis, long durationMillis) {
        lockedUntil.merge(addressKey, nowMillis + durationMillis, Math::max);
    }

    private void prune(Deque<Long> recent, long nowMillis) {
        while (!recent.isEmpty() && nowMillis - recent.peekFirst() > windowMillis) {
            recent.pollFirst();
        }
    }
}
