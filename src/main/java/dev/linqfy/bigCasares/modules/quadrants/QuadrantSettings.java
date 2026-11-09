package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuración de los cuadrantes diagonales.
 *
 * @param worlds            mundos divididos en cuadrantes; el resto no se toca
 * @param centerX           X del cruce de los ejes
 * @param centerZ           Z del cruce de los ejes
 * @param anarchyQuadrant   uno de los cuadrantes de Anarquía; su opuesto también lo es
 * @param combatTagSeconds  búfer de combate tras un golpe PvP durante el que no se puede entrar a la zona Chill
 */
public record QuadrantSettings(
    Set<String> worlds,
    double centerX,
    double centerZ,
    Quadrant anarchyQuadrant,
    long combatTagSeconds
) {

    public static final String ROOT = "pvp-quadrants";

    public QuadrantSettings {
        worlds = Set.copyOf(worlds);
        if (!Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException("El centro de los cuadrantes debe ser finito.");
        }
        if (anarchyQuadrant == null) {
            throw new IllegalArgumentException("Falta el cuadrante de Anarquía.");
        }
        if (combatTagSeconds < 0L) {
            throw new IllegalArgumentException("combat-tag-seconds no puede ser negativo.");
        }
    }

    public static QuadrantSettings defaults() {
        return new QuadrantSettings(Set.of("world"), 0.0, 0.0, Quadrant.NORTH_EAST, 150L);
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
            parseDiagonal(config.getString(ROOT + ".anarchy-diagonal", "noreste-suroeste")),
            config.getLong(ROOT + ".combat-tag-seconds", defaults.combatTagSeconds())
        );
    }

    /** Acepta "noreste-suroeste" / "noroeste-sureste" (o NE-SW / NW-SE). */
    static Quadrant parseDiagonal(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (value) {
            case "noreste-suroeste", "suroeste-noreste", "ne-sw", "sw-ne", "ne-so", "so-ne" -> Quadrant.NORTH_EAST;
            case "noroeste-sureste", "sureste-noroeste", "nw-se", "se-nw", "no-se", "se-no" -> Quadrant.NORTH_WEST;
            default -> throw new IllegalArgumentException(
                "anarchy-diagonal debe ser noreste-suroeste o noroeste-sureste: " + raw);
        };
    }

    public QuadrantMap map() {
        return new QuadrantMap(this);
    }
}
