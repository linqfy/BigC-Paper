package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import java.util.ArrayList;
import java.util.List;

/**
 * La grilla cuadrada de baldosas sobre el piso de la arena, sin Bukkit: {@code (2 * radius + 1)^2} baldosas de
 * {@code cellSize} bloques centradas en {@code (originX, originZ)}. Las coordenadas del mundo son las de bloque
 * (dobles); la baldosa {@code (0, 0)} cubre {@code origin +- cellSize / 2}.
 */
public record ZeusGrid(double originX, double originZ, int cellSize, int radius) {

    public ZeusGrid {
        if (cellSize < 1) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be positive");
        }
    }

    public int side() {
        return 2 * radius + 1;
    }

    public boolean contains(ZeusCell cell) {
        return Math.abs(cell.x()) <= radius && Math.abs(cell.z()) <= radius;
    }

    /** La baldosa bajo un punto del mundo (puede quedar afuera de la grilla: mirar {@link #contains}). */
    public ZeusCell cellAt(double x, double z) {
        return new ZeusCell(
            (int) Math.floor((x - originX) / cellSize + 0.5),
            (int) Math.floor((z - originZ) / cellSize + 0.5));
    }

    /** La baldosa mas cercana dentro de la grilla. */
    public ZeusCell clamp(ZeusCell cell) {
        return new ZeusCell(Math.max(-radius, Math.min(radius, cell.x())),
            Math.max(-radius, Math.min(radius, cell.z())));
    }

    public double centerX(ZeusCell cell) {
        return originX + (double) cell.x() * cellSize;
    }

    public double centerZ(ZeusCell cell) {
        return originZ + (double) cell.z() * cellSize;
    }

    public List<ZeusCell> cells() {
        List<ZeusCell> cells = new ArrayList<>(side() * side());
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                cells.add(new ZeusCell(x, z));
            }
        }
        return cells;
    }
}
