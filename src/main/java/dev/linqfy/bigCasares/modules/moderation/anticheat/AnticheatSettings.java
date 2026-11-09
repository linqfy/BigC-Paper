package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
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
    Set<String> disabledChecks,
    Set<Material> dupeTrackedItems,
    boolean trackShulkerBoxes
) {
    public static final String DEFAULT_BYPASS_PERMISSION = "bigcasares.anticheat.bypass";
    /** Items que no se apilan y valen lo suficiente como para duplicarlos. */
    static final Set<Material> DEFAULT_DUPE_TRACKED = Set.copyOf(EnumSet.of(
        Material.ELYTRA, Material.TOTEM_OF_UNDYING, Material.TRIDENT, Material.MACE,
        Material.NETHERITE_SWORD, Material.NETHERITE_PICKAXE, Material.NETHERITE_AXE, Material.NETHERITE_SHOVEL,
        Material.NETHERITE_HOE, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ));

    public static AnticheatSettings defaults() {
        return new AnticheatSettings(true, DEFAULT_BYPASS_PERMISSION, true, 17.0, 22, Set.of(),
            DEFAULT_DUPE_TRACKED, true);
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
                .collect(Collectors.toUnmodifiableSet()),
            dupeTracked(config, prefix + "dupe-tracked-items", defaults.dupeTrackedItems()),
            config.getBoolean(prefix + "dupe-track-shulker-boxes", defaults.trackShulkerBoxes())
        );
    }

    private static Set<Material> dupeTracked(ConfigurationSection config, String path, Set<Material> fallback) {
        if (!config.isList(path)) {
            return fallback;
        }
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : config.getStringList(path)) {
            Material material = Material.matchMaterial(value.trim());
            if (material != null) {
                materials.add(material);
            }
        }
        return Set.copyOf(materials);
    }

    public boolean checkEnabled(String rule) {
        return enabled && !disabledChecks.contains(rule);
    }
}
