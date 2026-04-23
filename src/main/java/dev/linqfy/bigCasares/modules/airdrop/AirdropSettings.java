package dev.linqfy.bigCasares.modules.airdrop;

import org.bukkit.configuration.file.FileConfiguration;

public record AirdropSettings(
        int intervalMinutes,
        int radius,
        int dropHeight
) {
    private static final String PREFIX = "airdrop-system.";

    public static AirdropSettings fromConfig(FileConfiguration config) {
        return new AirdropSettings(
                config.getInt(PREFIX + "interval-minutes", 30),
                config.getInt(PREFIX + "radius", 500),
                config.getInt(PREFIX + "drop-height", 30)
        );
    }
}
