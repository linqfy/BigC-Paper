package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.platform.PlayerStrings;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Each player's favourite products, kept with the player ({@link PlayerStrings}) as {@code category/entry} lines in
 * the order they were added. A favourite the catalog no longer has is skipped, never deleted, so it comes back if the
 * product does; anything unreadable is ignored.
 */
final class ShopFavorites {
    static final String KEY = "shop_favorites_v1";
    /** The virtual category listing them, first among the categories while there is any. */
    static final String CATEGORY = "#favoritos";
    static final String NAME = "★ Favoritos";
    static final int MAX = 54;
    /** A category or entry id as shop.yml keys are written: starting with a letter or digit, never "." or "..". */
    private static final String PART = "[A-Za-z0-9][A-Za-z0-9_.-]{0,63}";

    private final PlayerStrings store;

    ShopFavorites(PlayerStrings store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    static String key(String categoryId, String entryId) {
        return categoryId + "/" + entryId;
    }

    /** The saved favourites, valid lines only, oldest first. */
    Set<String> keys(Player player) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : store.get(player, KEY).orElse("").split("\n")) {
            String key = line.strip();
            if (key.matches(PART + "/" + PART) && keys.size() < MAX) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Adds or removes a favourite; true when it is a favourite now. Past {@link #MAX}, the oldest one leaves. */
    boolean toggle(Player player, String categoryId, String entryId) {
        Set<String> keys = keys(player);
        String key = key(categoryId, entryId);
        boolean added = keys.add(key);
        if (!added) {
            keys.remove(key);
        }
        while (keys.size() > MAX) {
            keys.remove(keys.iterator().next());
        }
        store.set(player, KEY, String.join("\n", keys));
        return added;
    }

    /**
     * The favourites as a category of the current catalog: each entry under its {@code category/entry} key as id (so
     * a row says where it comes from), or empty when none of them exists.
     */
    Optional<ShopCategory> category(Player player, ShopCatalog catalog) {
        List<ShopEntry> entries = new ArrayList<>();
        for (String key : keys(player)) {
            resolve(catalog, key).ifPresent(entry -> entries.add(new ShopEntry(key, entry.slot(), entry.material(),
                entry.customItemId(), entry.amount(), entry.buyPrice(), entry.sellPrice(), entry.displayName(), entry.lore(),
                entry.dailyLimit())));
        }
        return entries.isEmpty() ? Optional.empty()
            : Optional.of(new ShopCategory(CATEGORY, NAME, Material.NETHER_STAR, 0, entries));
    }

    /** The real catalog entry behind a {@code category/entry} key. */
    static Optional<ShopEntry> resolve(ShopCatalog catalog, String key) {
        int slash = key.indexOf('/');
        return slash <= 0 ? Optional.empty() : catalog.entry(key.substring(0, slash), key.substring(slash + 1));
    }
}
