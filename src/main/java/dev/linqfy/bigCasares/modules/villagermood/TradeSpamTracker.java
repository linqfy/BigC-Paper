package dev.linqfy.bigCasares.modules.villagermood;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Counts trades per player and villager in a sliding window and reports when a player is spamming one villager.
 */
final class TradeSpamTracker {

    private record Pair(UUID player, UUID villager) {
    }

    private final long windowTicks;
    private final int maxTrades;
    private final Map<Pair, Deque<Long>> trades = new HashMap<>();

    TradeSpamTracker(long windowTicks, int maxTrades) {
        this.windowTicks = windowTicks;
        this.maxTrades = maxTrades;
    }

    /**
     * Records one trade at {@code nowTick}. Returns {@code true} when it exceeds the allowed trades in the window;
     * the window then starts over so the next complaint needs another burst.
     */
    boolean record(UUID player, UUID villager, long nowTick) {
        Pair key = new Pair(player, villager);
        Deque<Long> times = trades.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        prune(times, nowTick);
        times.addLast(nowTick);
        if (times.size() > maxTrades) {
            trades.remove(key);
            return true;
        }
        return false;
    }

    /** Drops windows that have fully expired. */
    void cleanup(long nowTick) {
        trades.values().removeIf(times -> {
            prune(times, nowTick);
            return times.isEmpty();
        });
    }

    void forgetPlayer(UUID player) {
        trades.keySet().removeIf(key -> key.player().equals(player));
    }

    void clear() {
        trades.clear();
    }

    private void prune(Deque<Long> times, long nowTick) {
        while (!times.isEmpty() && nowTick - times.peekFirst() >= windowTicks) {
            times.pollFirst();
        }
    }
}
