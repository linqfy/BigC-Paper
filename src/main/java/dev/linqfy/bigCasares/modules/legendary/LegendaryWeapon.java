package dev.linqfy.bigCasares.modules.legendary;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public enum LegendaryWeapon {
    BLOODLUST(
            "bloodlust",
            "Bloodlust",
            "NETHERITE_SWORD",
            "blood-fury",
            "Bloodlust",
            "Santuario de Sangre",
            "NETHER",
            "CRIMSON_FOREST",
            Map.of(
                    "NETHERITE_SWORD",
                    1,
                    "REDSTONE_BLOCK",
                    4,
                    "GHAST_TEAR",
                    2,
                    "NETHERITE_BLOCK",
                    2)),
    FROST_SCYTHE(
            "frost_scythe",
            "Frost Scythe",
            "DIAMOND_HOE",
            "glacial-storm",
            "Tormenta Glacial",
            "Santuario Glacial",
            "NORMAL",
            "FROZEN_PEAKS",
            Map.of("DIAMOND_HOE", 1, "BLUE_ICE", 4, "HEART_OF_THE_SEA", 2, "DIAMOND_BLOCK", 2)),
    SHADOW_BLADE(
            "shadow_blade",
            "Shadow Blade",
            "NETHERITE_SWORD",
            "shadow-assassin",
            "Dagas de Sombra",
            "Santuario de Sombras",
            "NORMAL",
            "DEEP_DARK",
            Map.of(
                    "NETHERITE_SWORD",
                    1,
                    "ENDER_PEARL",
                    4,
                    "PHANTOM_MEMBRANE",
                    2,
                    "CRYING_OBSIDIAN",
                    2)),
    HYPERION(
            "hyperion",
            "Hyperion",
            "BLAZE_ROD",
            "arcane-destruction",
            "Destrucción Arcana",
            "Santuario Arcano",
            "THE_END",
            "END_HIGHLANDS",
            Map.of("BLAZE_ROD", 1, "AMETHYST_BLOCK", 4, "NETHER_STAR", 2, "GOLD_BLOCK", 2)),
    WINDWEAVER(
            "windweaver",
            "Windweaver",
            "MACE",
            "divine-tornado",
            "Viento",
            "Santuario del Viento",
            "NORMAL",
            "JAGGED_PEAKS",
            Map.of("MACE", 1, "WIND_CHARGE", 4, "FEATHER", 2, "IRON_BLOCK", 2)),
    EARTHBREAKER(
            "earthbreaker",
            "Earthbreaker",
            "NETHERITE_AXE",
            "titan-earthquake",
            "Earthshatter / Stalwart Absorption",
            "Santuario de la Tierra",
            "NORMAL",
            "DRIPSTONE_CAVES",
            Map.of("NETHERITE_AXE", 1, "DEEPSLATE", 4, "EMERALD_BLOCK", 2, "NETHERITE_INGOT", 2)),
    VOID_KATANA(
            "void_katana",
            "Void Katana",
            "DIAMOND_SWORD",
            "void-dimension",
            "Dimensión del Vacío",
            "Santuario del Vacío",
            "THE_END",
            "END_HIGHLANDS",
            Map.of("DIAMOND_SWORD", 1, "ENDER_EYE", 4, "CHORUS_FRUIT", 2, "PURPUR_BLOCK", 2)),
    THUNDER_SPEAR(
            "thunder_spear",
            "Thunder Spear",
            "NETHERITE_SPEAR",
            "thunder-javelin",
            "Trueno",
            "Santuario de Tormentas",
            "NORMAL",
            "STONY_PEAKS",
            Map.of(
                    "NETHERITE_SPEAR",
                    1,
                    "LIGHTNING_ROD",
                    4,
                    "HEART_OF_THE_SEA",
                    2,
                    "COPPER_BLOCK",
                    2));

    public static final long COOLDOWN_MILLIS = 40_000;
    // Keep historical enum names readable by the persisted journal and inventory recovery.
    private static final List<LegendaryWeapon> AVAILABLE =
            Arrays.stream(values()).filter(LegendaryWeapon::isAvailable).toList();
    public static final int STORM_TICKS = 200;
    public static final int LIGHTNING_INTERVAL_TICKS = 40;
    public final String id;
    public final String displayName;
    public final String material;
    public final String abilityId;
    public final String abilityName;
    public final String altarName;
    public final String environment;
    public final String biome;
    public final Map<String, Integer> ingredients;

    LegendaryWeapon(
            String id,
            String displayName,
            String material,
            String abilityId,
            String abilityName,
            String altarName,
            String environment,
            String biome,
            Map<String, Integer> ingredients) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.abilityId = abilityId;
        this.abilityName = abilityName;
        this.altarName = altarName;
        this.environment = environment;
        this.biome = biome;
        this.ingredients = Map.copyOf(ingredients);
    }

    public static Optional<LegendaryWeapon> find(String id) {
        return AVAILABLE.stream().filter(weapon -> weapon.id.equalsIgnoreCase(id)).findFirst();
    }

    public static List<LegendaryWeapon> available() {
        return AVAILABLE;
    }

    public boolean isAvailable() {
        return this != VOID_KATANA && this != HYPERION && this != FROST_SCYTHE;
    }

    public boolean canCraft(Map<String, Integer> inventory) {
        return isAvailable()
                && ingredients.entrySet().stream()
                        .allMatch(
                                entry ->
                                        inventory.getOrDefault(entry.getKey(), 0)
                                                >= entry.getValue());
    }

    public String abilityDescription() {
        return switch (this) {
            case BLOODLUST ->
                    "Infection; N1 Velocidad II, N2 Blood Tracker, N3 Blood Trail (Shift+F/G), N4"
                        + " Fuerza I, N5 Blood Hook (F/G). Medio corazón de curación por golpe"
                        + " directo durante Trail/Hook.";
            case SHADOW_BLADE ->
                    "F/G: Shadow Daggers (1-3 dagas, ceguera y lentitud, máximo 2 corazones base"
                        + " por rival). Desde N2 Shift+F/G: Shadow Leap; repite para terminar."
                        + " Velocidad II y 30% de atraer al atacar por detrás.";
            case WINDWEAVER ->
                    "N1 Shift+clic izquierdo al aire: Wind Dash. N2 clic derecho: Skybreaker"
                        + " (repite para caer). N3 Shift+derecho: Tornado. N4 Shift+izquierdo"
                        + " mirando al suelo: Wind Prison. N5 Shift+F/G: Heaven's Fall.";
            case EARTHBREAKER ->
                    "F/G: Earthshatter, hacha gigante y onda radial sin romper terreno, exactamente"
                        + " 2 corazones de salud por rival. Desde N3 Shift+F/G: Stalwart"
                        + " Absorption; refleja hasta 2 corazones base por rival.";
            case HYPERION, VOID_KATANA, FROST_SCYTHE -> "Arma retirada de esta versión.";
            case THUNDER_SPEAR ->
                    "N1 Shift+clic izquierdo al aire: Lightning Dash. N2 clic derecho: Thunder"
                        + " Mark. N3 Shift+derecho: Chain Lightning. N4 Shift+izquierdo mirando"
                        + " arriba: Thunderstorm. N5 Shift+F/G: Wrath of Zeus.";
        };
    }
}
