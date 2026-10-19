package dev.linqfy.bigCasares.modules.geyser;

import org.bukkit.configuration.ConfigurationSection;

public record GeyserSettings(
    boolean enabled,
    boolean customEntities,
    boolean bedrockForms,
    boolean bedrockResourcePack,
    boolean customItems,
    boolean safeFallbacks,
    boolean customBlocks
) {
    public static GeyserSettings load(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("geyser-integration");
        if (section == null) {
            return new GeyserSettings(true, true, true, true, true, true, true);
        }
        return new GeyserSettings(
            section.getBoolean("enabled", true),
            section.getBoolean("custom-entities", true),
            section.getBoolean("bedrock-forms", true),
            section.getBoolean("bedrock-resource-pack", true),
            section.getBoolean("custom-items", true),
            section.getBoolean("safe-fallbacks", true),
            section.getBoolean("custom-blocks", true)
        );
    }
}
