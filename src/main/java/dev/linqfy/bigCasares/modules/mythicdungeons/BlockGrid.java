package dev.linqfy.bigCasares.modules.mythicdungeons;

/** Lo minimo del mundo que necesitan las reglas de lugar (sin servidor): si un bloque frena a un mob. */
@FunctionalInterface
public interface BlockGrid {

    /** Si el bloque en esas coordenadas no se puede atravesar (pared, piso, techo). */
    boolean solid(int x, int y, int z);
}
