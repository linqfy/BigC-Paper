package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;
import java.util.function.LongSupplier;

/** Separate player/weapon progress; death and item transfer never reset it. */
final class LegendaryPvpLevels {
    static final int MAX_LEVEL = 5;
    static final long REPEAT_VICTIM_MILLIS = 600_000;
    private final LegendaryStateStore store;
    private final LongSupplier clock;

    LegendaryPvpLevels(LegendaryStateStore store) {
        this(store, System::currentTimeMillis);
    }

    LegendaryPvpLevels(LegendaryStateStore store, LongSupplier clock) {
        this.store = store;
        this.clock = clock;
    }

    int level(UUID player, LegendaryWeapon weapon) {
        if (!weapon.isAvailable()) return 0;
        String raw = store.get(key(player, weapon));
        return raw == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, Integer.parseInt(raw)));
    }

    int kill(UUID killer, UUID victim, LegendaryWeapon weapon) throws IOException {
        int current = level(killer, weapon);
        if (killer.equals(victim) || !weapon.isAvailable() || current == MAX_LEVEL) return current;
        long now = clock.getAsLong();
        String rewardKey = "reward." + killer + "." + victim;
        String previous = store.get(rewardKey);
        if (previous != null && now < Long.parseLong(previous)) return current;
        int next = current + 1;
        // Shared across weapons and durable across reconnect/reload. The level
        // and anti-farming deadline must never commit independently.
        store.putAll(Map.of(key(killer, weapon), Integer.toString(next),
                rewardKey, Long.toString(Math.addExact(now, REPEAT_VICTIM_MILLIS))));
        return next;
    }

    void set(UUID player, LegendaryWeapon weapon, int level) throws IOException {
        if (!weapon.isAvailable() || level < 0 || level > MAX_LEVEL)
            throw new IllegalArgumentException("El nivel debe estar entre 0 y 5.");
        store.put(key(player, weapon), Integer.toString(level));
    }

    private static String key(UUID player, LegendaryWeapon weapon) {
        return "level." + player + "." + weapon.id;
    }
}
