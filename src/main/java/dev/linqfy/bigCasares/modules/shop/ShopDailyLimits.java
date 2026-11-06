package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.platform.PlayerStrings;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@code limite-diario} of shop.yml entries: lots each player may buy per day, counted with the player ({@link
 * PlayerStrings}, {@code shop_daily_v1}: "date|category/entry=lots,..."). A new day starts every count again; an
 * unreadable value counts as nothing bought.
 */
final class ShopDailyLimits {
    static final String KEY = "shop_daily_v1";
    static final ShopDailyLimits NONE = new ShopDailyLimits(null, LocalDate::now, entry -> java.util.Optional.empty());

    private final PlayerStrings store;
    private final Supplier<LocalDate> today;
    private final Function<ShopEntry, java.util.Optional<String>> keyOf;

    ShopDailyLimits(PlayerStrings store, Supplier<LocalDate> today, Function<ShopEntry, java.util.Optional<String>> keyOf) {
        this.store = store;
        this.today = Objects.requireNonNull(today, "today");
        this.keyOf = Objects.requireNonNull(keyOf, "keyOf");
    }

    /** Lots the player may still buy today; {@link Integer#MAX_VALUE} for an entry without a limit. */
    int remaining(Player player, ShopEntry entry) {
        if (entry.dailyLimit() <= 0 || store == null) {
            return Integer.MAX_VALUE;
        }
        String key = keyOf.apply(entry).orElse(null);
        if (key == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, entry.dailyLimit() - read(player).getOrDefault(key, 0));
    }

    /** Counts {@code lots} bought now (only entries with a limit are kept). */
    void bought(Player player, ShopEntry entry, int lots) {
        if (entry.dailyLimit() <= 0 || store == null || lots <= 0) {
            return;
        }
        keyOf.apply(entry).ifPresent(key -> {
            Map<String, Integer> counts = read(player);
            counts.merge(key, lots, Integer::sum);
            StringBuilder out = new StringBuilder(today.get().toString()).append('|');
            counts.forEach((k, v) -> out.append(k).append('=').append(v).append(','));
            store.set(player, KEY, out.substring(0, out.length() - 1));
        });
    }

    /** Today's counts; empty for another day or anything unreadable. */
    private Map<String, Integer> read(Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String[] parts = store.get(player, KEY).orElse("").split("\\|", 2);
        if (parts.length != 2 || !parts[0].equals(today.get().toString())) {
            return counts;
        }
        for (String pair : parts[1].split(",")) {
            int equals = pair.lastIndexOf('=');
            if (equals <= 0) continue;
            try {
                counts.put(pair.substring(0, equals), Math.max(0, Integer.parseInt(pair.substring(equals + 1))));
            } catch (NumberFormatException unreadable) {
                // skipped
            }
        }
        return counts;
    }
}
