package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.Map;
import java.util.Objects;

/**
 * Quien esta adentro de la sala de Zeus, sin Bukkit. La audiencia del runtime llega a 48 bloques y atraviesa paredes;
 * solo un jugador parado sobre una baldosa con piso de la grilla, cerca de su altura, puede ser blanco de un patron,
 * de una lanza o de una nube, o quedar electrocutado.
 */
public record ZeusArenaMembership(ZeusGrid grid, Map<ZeusCell, Integer> floors) {

    /** Bloques por encima del piso de la baldosa que todavia cuentan (saltos, escalones del altar). */
    public static final double ABOVE_FLOOR = 6.0;
    /** Bloques por debajo del piso que todavia cuentan. */
    public static final double BELOW_FLOOR = 1.0;

    public ZeusArenaMembership {
        Objects.requireNonNull(grid, "grid");
        floors = Map.copyOf(Objects.requireNonNull(floors, "floors"));
    }

    /** {@code (x, y, z)} son los pies del jugador en coordenadas del mundo. */
    public boolean contains(double x, double y, double z) {
        ZeusCell cell = grid.cellAt(x, z);
        if (!grid.contains(cell)) {
            return false;
        }
        Integer floor = floors.get(cell);
        if (floor == null) {
            return false;
        }
        double standing = floor + 1.0;
        return y >= standing - BELOW_FLOOR && y <= standing + ABOVE_FLOOR;
    }
}
