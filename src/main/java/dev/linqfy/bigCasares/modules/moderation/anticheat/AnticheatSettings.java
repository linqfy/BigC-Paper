package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seccion {@code moderation-system.anticheat}. Todas las claves son opcionales: una config vieja sin la seccion usa estos
 * valores por defecto.
 */
public record AnticheatSettings(
    boolean enabled,
    String bypassPermission,
    boolean exemptBedrock,
    double minimumTps,
    int maxAttacksPerSecond,
    Set<String> disabledChecks
) {
    public static final String DEFAULT_BYPASS_PERMISSION = "bigcasares.anticheat.bypass";

    public static AnticheatSettings defaults() {
        return new AnticheatSettings(true, DEFAULT_BYPASS_PERMISSION, true, 17.0, 22, Set.of());
    }

    public static AnticheatSettings load(ConfigurationSection config, String root) {
        AnticheatSettings defaults = defaults();
        String prefix = root + "anticheat.";
        String permission = config.getString(prefix + "bypass-permission", defaults.bypassPermission());
        return new AnticheatSettings(
            config.getBoolean(prefix + "enabled", defaults.enabled()),
            permission == null || permission.isBlank() ? "" : permission.trim(),
            config.getBoolean(prefix + "exempt-bedrock", defaults.exemptBedrock()),
            Math.max(0, Math.min(20, config.getDouble(prefix + "minimum-tps", defaults.minimumTps()))),
            Math.max(10, config.getInt(prefix + "max-attacks-per-second", defaults.maxAttacksPerSecond())),
            config.getStringList(prefix + "disabled-checks").stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    public boolean checkEnabled(String rule) {
        return enabled && !disabledChecks.contains(rule);
    }
}
