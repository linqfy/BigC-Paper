package dev.linqfy.bigCasares.testsupport;

import dev.linqfy.bigCasares.platform.PlayerStrings;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** {@link PlayerStrings} in memory: what the player's persistent data would hold. */
public final class MapPlayerStrings implements PlayerStrings {
    public final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> get(Player player, String key) {
        return Optional.ofNullable(values.get(player.getUniqueId() + "/" + key));
    }

    @Override
    public void set(Player player, String key, String value) {
        if (value == null || value.isEmpty()) {
            values.remove(player.getUniqueId() + "/" + key);
        } else {
            values.put(player.getUniqueId() + "/" + key, value);
        }
    }
}
