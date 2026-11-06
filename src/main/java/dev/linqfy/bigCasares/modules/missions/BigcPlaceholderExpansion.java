package dev.linqfy.bigCasares.modules.missions;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * {@code %bigc_...%}: missions and shop numbers for scoreboards and tab lists. Its own identifier, because the team
 * placeholders already own {@code bigcasares}. Loaded only when PlaceholderAPI is installed (by reflection).
 */
public final class BigcPlaceholderExpansion extends PlaceholderExpansion {
    private final MissionPlaceholderValues values;

    public BigcPlaceholderExpansion(MissionPlaceholderValues values) {
        this.values = Objects.requireNonNull(values, "values");
    }

    @Override
    public @NotNull String getIdentifier() {
        return MissionPlaceholderValues.IDENTIFIER;
    }

    @Override
    public @NotNull String getAuthor() {
        return "BigCasares";
    }

    @Override
    public @NotNull String getVersion() {
        return "1";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return player == null ? "" : values.resolve(player.getUniqueId(), params);
    }
}
