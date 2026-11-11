package dev.linqfy.bigCasares.modules.trimabilities;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;

/**
 * @param tiers              armor tiers whose full trim sets grant abilities (material prefix, e.g. DIAMOND)
 * @param checkIntervalTicks how often worn armor is re-evaluated and effects refreshed
 */
record TrimAbilitySettings(Set<String> tiers, long checkIntervalTicks) {

    static TrimAbilitySettings defaults() {
        return new TrimAbilitySettings(Set.of("DIAMOND"), 20L);
    }

    static TrimAbilitySettings load(FileConfiguration config) {
        TrimAbilitySettings defaults = defaults();
        List<String> tiers = config.getStringList("trim-abilities.tiers");
        long interval = config.getLong("trim-abilities.check-interval-ticks", defaults.checkIntervalTicks());
        return new TrimAbilitySettings(
            tiers.isEmpty() ? defaults.tiers() : Set.copyOf(tiers),
            interval > 0 ? interval : defaults.checkIntervalTicks()
        );
    }
}
