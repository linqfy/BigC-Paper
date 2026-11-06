package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.util.UUID;

/** Separate player/weapon progress; death and item transfer never reset it. */
final class LegendaryPvpLevels {
    static final int MAX_LEVEL = 5;
    private final LegendaryStateStore store;

    LegendaryPvpLevels(LegendaryStateStore store) {
        this.store = store;
    }

    int level(UUID player, LegendaryWeapon weapon) {
        if (!weapon.isAvailable()) return 0;
        String raw = store.get(key(player, weapon));
        return raw == null ? 0 : Math.max(0, Math.min(MAX_LEVEL, Integer.parseInt(raw)));
    }

    int kill(UUID killer, UUID victim, LegendaryWeapon weapon) throws IOException {
        if (killer.equals(victim) || !weapon.isAvailable()) return level(killer, weapon);
        int next = Math.min(MAX_LEVEL, level(killer, weapon) + 1);
        set(killer, weapon, next);
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
