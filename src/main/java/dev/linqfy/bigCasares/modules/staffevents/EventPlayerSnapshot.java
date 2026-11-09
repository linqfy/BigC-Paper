package dev.linqfy.bigCasares.modules.staffevents;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything an arena event replaces on a player, kept on disk until it is given back
 * so a crash or disconnect never costs the real inventory.
 */
public record EventPlayerSnapshot(
    UUID playerId,
    ItemStack[] contents,
    StoredLocation location,
    String gameMode,
    double health,
    int foodLevel,
    float saturation,
    int level,
    float exp,
    List<PotionEffect> effects
) {
    public EventPlayerSnapshot {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(location, "location");
        contents = contents == null ? new ItemStack[0] : Arrays.copyOf(contents, contents.length);
        gameMode = gameMode == null || gameMode.isBlank() ? "SURVIVAL" : gameMode;
        effects = List.copyOf(effects);
    }

    @Override
    public ItemStack[] contents() {
        return Arrays.copyOf(contents, contents.length);
    }

    public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
        public StoredLocation {
            world = world == null ? "" : world;
        }
    }
}
