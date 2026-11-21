package dev.linqfy.bigCasares.modules.alexscaves.core.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Per-owner ability cooldowns measured in server ticks. */
public final class CooldownLedger {
    private final LongSupplier currentTick;
    private final Map<Key, Long> readyAt = new HashMap<>();

    public CooldownLedger(LongSupplier currentTick) {
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
    }

    public boolean isReady(UUID owner, String ability) {
        Long ready = readyAt.get(new Key(owner, ability));
        return ready == null || currentTick.getAsLong() >= ready;
    }

    /** Starts the cooldown only if it was ready; returns whether the ability may run. */
    public boolean tryUse(UUID owner, String ability, long cooldownTicks) {
        if (!isReady(owner, ability)) {
            return false;
        }
        readyAt.put(new Key(owner, ability), currentTick.getAsLong() + Math.max(0, cooldownTicks));
        return true;
    }

    public long remainingTicks(UUID owner, String ability) {
        Long ready = readyAt.get(new Key(owner, ability));
        return ready == null ? 0 : Math.max(0, ready - currentTick.getAsLong());
    }

    public void clear(UUID owner) {
        readyAt.keySet().removeIf(key -> key.owner().equals(owner));
    }

    public int size() {
        return readyAt.size();
    }

    private record Key(UUID owner, String ability) {
    }
}
