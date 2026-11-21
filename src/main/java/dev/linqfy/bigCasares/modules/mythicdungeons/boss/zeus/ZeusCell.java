package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

/** Una baldosa de la grilla de rayos de Zeus, relativa a la baldosa central de la arena. */
public record ZeusCell(int x, int z) {

    /** Distancia de Chebyshev (cuantas baldosas en cualquier direccion, diagonal incluida). */
    public int chebyshev(ZeusCell other) {
        return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
    }
}
