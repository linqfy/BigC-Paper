package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Which cells of one layer of the room get a water source (no server types). It flood-fills the open (air) cells
 * reachable from the start; if the fill escapes the measured arena (plus one block of slack) or grows beyond the
 * cap, the room is not watertight on that layer and nothing is flooded, so water never reaches outside the room.
 */
public final class EnlilFloodPlanner {

    /** A column of a layer. */
    public record Cell(int x, int z) {
    }

    @FunctionalInterface
    public interface OpenCells {
        boolean open(int x, int z);
    }

    /** 33x33 is the generated arena; this leaves room for odd shapes but not for the open world. */
    public static final int MAX_CELLS = 1600;

    private EnlilFloodPlanner() {
    }

    /**
     * @return the cells to fill, from the walls inward (the water seems to pour in), or empty when the layer leaks
     */
    public static Optional<List<Cell>> plan(EnlilArena arena, int startX, int startZ, OpenCells open) {
        Optional<Cell> start = firstOpen(startX, startZ, open);
        if (start.isEmpty()) {
            return Optional.empty();
        }
        Set<Cell> seen = new HashSet<>();
        Deque<Cell> queue = new ArrayDeque<>();
        seen.add(start.get());
        queue.add(start.get());
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (cell.x() < arena.minX() - 1 || cell.x() > arena.maxX() + 1
                || cell.z() < arena.minZ() - 1 || cell.z() > arena.maxZ() + 1 || seen.size() > MAX_CELLS) {
                return Optional.empty();
            }
            for (Cell next : List.of(new Cell(cell.x() + 1, cell.z()), new Cell(cell.x() - 1, cell.z()),
                new Cell(cell.x(), cell.z() + 1), new Cell(cell.x(), cell.z() - 1))) {
                if (!seen.contains(next) && open.open(next.x(), next.z())) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        List<Cell> cells = new ArrayList<>(seen);
        double cx = arena.centerX() - 0.5;
        double cz = arena.centerZ() - 0.5;
        cells.sort(Comparator.<Cell>comparingDouble(cell -> -Math.hypot(cell.x() - cx, cell.z() - cz))
            .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return Optional.of(List.copyOf(cells));
    }

    /** The start column may be the altar; look in a small square around it for an open one. */
    private static Optional<Cell> firstOpen(int x, int z, OpenCells open) {
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius && open.open(x + dx, z + dz)) {
                        return Optional.of(new Cell(x + dx, z + dz));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
