package dev.linqfy.bigCasares.modules.legendary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Finite terrain work, measured from the surface block under the caster. */
final class LegendaryCrater {
    record Cell(int x, int y, int z) { }
    private static final List<Cell> CELLS = create();
    private int cursor;

    static int depth(int x, int z) {
        int squared = x * x + z * z;
        if (squared > 64) return 0;
        if (squared < 4) return 4;
        if (squared < 16) return 3;
        if (squared < 36) return 2;
        return 1;
    }

    static List<Cell> cells() { return CELLS; }
    boolean complete() { return cursor == CELLS.size(); }

    int step(int budget, Predicate<Cell> remove) {
        if (budget < 1) throw new IllegalArgumentException("El presupuesto debe ser positivo.");
        int removed = 0;
        for (int processed = 0; processed < budget && !complete(); processed++) {
            if (remove.test(CELLS.get(cursor++))) removed++;
        }
        return removed;
    }

    private static List<Cell> create() {
        var cells = new ArrayList<Cell>();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            for (int y = 0; y > -depth(x, z); y--) cells.add(new Cell(x, y, z));
        }
        cells.sort(Comparator.comparingInt((Cell cell) -> cell.x() * cell.x() + cell.z() * cell.z())
            .thenComparing(Comparator.comparingInt(Cell::y).reversed()));
        return List.copyOf(cells);
    }
}
