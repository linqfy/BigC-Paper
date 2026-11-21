package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Set;

/**
 * Configuración del centro y de los mundos con cuadrantes.
 *
 * @param worlds            mundos divididos en cuadrantes; el resto no se toca
 * @param centerX           X del cruce de los ejes
 * @param centerZ           Z del cruce de los ejes
 * @param combatTagSeconds  búfer de combate tras un golpe PvP durante el que no se puede entrar a la zona Chill
 */
public record QuadrantSettings(
    Set<String> worlds,
    double centerX,
    double centerZ,
    long combatTagSeconds
) {

    public static final String ROOT = "pvp-quadrants";

    public QuadrantSettings {
        worlds = Set.copyOf(worlds);
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("El centro de los cuadrantes debe ser finito.");
        }
        if (combatTagSeconds < 0L) {
            throw new IllegalArgumentException("combat-tag-seconds no puede ser negativo.");
        }
    }

    public static QuadrantSettings defaults() {
        return new QuadrantSettings(Set.of("world"), 0.0, 0.0, 150L);
    }

    public static QuadrantSettings load(ConfigurationSection config) {
        QuadrantSettings defaults = defaults();
        if (config == null) {
            return defaults;
        }
        List<String> worlds = config.getStringList(ROOT + ".worlds");
        return new QuadrantSettings(
            worlds.isEmpty() && !config.isList(ROOT + ".worlds") ? defaults.worlds() : Set.copyOf(worlds),
            config.getDouble(ROOT + ".center-x", defaults.centerX()),
            config.getDouble(ROOT + ".center-z", defaults.centerZ()),
            config.getLong(ROOT + ".combat-tag-seconds", defaults.combatTagSeconds())
        );
    }

    public QuadrantMap map() {
        return new QuadrantMap(this);
    }
}
