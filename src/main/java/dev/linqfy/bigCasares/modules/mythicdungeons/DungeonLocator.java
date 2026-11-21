package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Location;

import java.util.Optional;

/**
 * Donde estan las mazmorras: "adentro de la mazmorra" es adentro de la caja de una estructura generada
 * {@code bigc_myth:<cultura>}. La implementacion del servidor es {@link PaperDungeonLocator}; los tests usan una falsa.
 */
public interface DungeonLocator {

    /** La cultura de la mazmorra que contiene el lugar, si hay una. */
    default Optional<MythicCulture> cultureAt(Location location) {
        return dungeonBounds(location).map(DungeonBounds::culture);
    }

    /** La caja de la mazmorra que contiene el lugar, si hay una. */
    Optional<DungeonBounds> dungeonBounds(Location location);

    /**
     * {@code true} si el bloque de ese lugar es el altar que dejo la generacion en la sala del jefe (ver
     * {@link AltarRule}). Por defecto alcanza con estar adentro de una mazmorra.
     */
    default boolean isAltarSpot(Location block) {
        return dungeonBounds(block).isPresent();
    }
}
