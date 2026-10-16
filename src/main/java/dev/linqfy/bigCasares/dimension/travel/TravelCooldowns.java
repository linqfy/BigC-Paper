package dev.linqfy.bigCasares.dimension.travel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player transfer cooldowns (in memory; cooldowns intentionally reset on restart). */
public final class TravelCooldowns {

    private final Clock clock;
    private final Map<UUID, Instant> lastTransfer = new ConcurrentHashMap<>();

    public TravelCooldowns(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Remaining wait, or {@link Duration#ZERO} when the player may travel. */
    public Duration remaining(UUID playerId, Duration cooldown) {
        Instant last = lastTransfer.get(playerId);
        if (last == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(last, clock.instant());
        Duration remaining = cooldown.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void record(UUID playerId) {
        lastTransfer.put(playerId, clock.instant());
    }

    public void forget(UUID playerId) {
        lastTransfer.remove(playerId);
    }
}
