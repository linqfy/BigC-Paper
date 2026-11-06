package dev.linqfy.bigCasares.platform;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Small text values kept with the player (their persistent data, saved in the player's own file by the server): no
 * extra files, and they follow the player. Server thread only. Keys carry a version ({@code shop_favorites_v1}) so a
 * format change never misreads old values.
 */
public interface PlayerStrings {
    Optional<String> get(Player player, String key);

    /** {@code value} null or empty removes the key. */
    void set(Player player, String key, String value);

    /** Backed by the player's PersistentDataContainer, under {@code plugin}'s namespace. */
    static PlayerStrings of(Plugin plugin) {
        return new PlayerStrings() {
            @Override
            public Optional<String> get(Player player, String key) {
                return Optional.ofNullable(player.getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, key), PersistentDataType.STRING));
            }

            @Override
            public void set(Player player, String key, String value) {
                NamespacedKey namespaced = new NamespacedKey(plugin, key);
                if (value == null || value.isEmpty()) {
                    player.getPersistentDataContainer().remove(namespaced);
                } else {
                    player.getPersistentDataContainer().set(namespaced, PersistentDataType.STRING, value);
                }
            }
        };
    }
}
