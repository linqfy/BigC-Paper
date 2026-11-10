package dev.linqfy.bigCasares.modules.discord;

import org.bukkit.configuration.ConfigurationSection;

public record DiscordSettings(
    long guildId,
    long publicChannelId,
    long adminChannelId,
    long logChannelId,
    long bridgeChannelId,
    long authorizedUserId,
    int reconciliationSeconds,
    String avatarTemplate,
    long raidAlertCategoryId
) {

    public DiscordSettings(
        long guildId, long publicChannelId, long adminChannelId, long logChannelId,
        long bridgeChannelId, long authorizedUserId, int reconciliationSeconds, String avatarTemplate
    ) {
        this(guildId, publicChannelId, adminChannelId, logChannelId, bridgeChannelId, authorizedUserId,
            reconciliationSeconds, avatarTemplate, 0L);
    }

    public static DiscordSettings load(ConfigurationSection config) {
        String root = "discord-integration.";
        return new DiscordSettings(
            config.getLong(root + "guild-id"), config.getLong(root + "public-channel-id"),
            config.getLong(root + "admin-channel-id"), config.getLong(root + "log-channel-id"),
            config.getLong(root + "bridge-channel-id"), config.getLong(root + "authorized-user-id"),
            Math.max(30, config.getInt(root + "reconciliation-seconds", 120)),
            config.getString(root + "bridge-avatar-template", "https://mc-heads.net/avatar/%player%/128"),
            config.getLong(root + "raid-alerts.category-id", 0L)
        );
    }

    public boolean hasRequiredIds() {
        return guildId > 0 && publicChannelId > 0 && adminChannelId > 0 && logChannelId > 0
            && bridgeChannelId > 0 && authorizedUserId > 0;
    }
}
