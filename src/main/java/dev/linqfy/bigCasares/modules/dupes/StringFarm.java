package dev.linqfy.bigCasares.modules.dupes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StringFarm {
    enum Cell { AIR, WATER, WATERLOGGED, WIRE, HOOK_NORTH, HOOK_SOUTH, HOOK_EAST, HOOK_WEST, OTHER, UNLOADED }
    record Pos(int x, int y, int z) {
        Pos offset(int dx, int dz) { return new Pos(x + dx, y, z + dz); }
    }
    interface View { Cell cell(Pos position); }
    record Line(Pos first, Pos last, int dx, int dz, List<Pos> wires) {
        Line { wires = List.copyOf(wires); }
    }

    static Line find(View view, Pos target) {
        if (view.cell(target) != Cell.WIRE) return null;
        Line line = findAxis(view, target, 1, 0, Cell.HOOK_EAST, Cell.HOOK_WEST);
        return line != null ? line : findAxis(view, target, 0, 1, Cell.HOOK_SOUTH, Cell.HOOK_NORTH);
    }

    private static Line findAxis(View view, Pos target, int dx, int dz, Cell firstHook, Cell lastHook) {
        int before = distanceToHook(view, target, -dx, -dz, firstHook);
        int after = distanceToHook(view, target, dx, dz, lastHook);
        if (before < 1 || after < 1 || before + after > 41) return null;
        Pos first = target.offset(-dx * before, -dz * before);
        Pos last = target.offset(dx * after, dz * after);
        List<Pos> wires = new ArrayList<>();
        for (int step = 1; step < before + after; step++) wires.add(first.offset(dx * step, dz * step));
        return new Line(first, last, dx, dz, wires);
    }

    private static int distanceToHook(View view, Pos target, int dx, int dz, Cell hook) {
        for (int step = 1; step <= 41; step++) {
            Cell cell = view.cell(target.offset(dx * step, dz * step));
            if (cell == hook) return step;
            if (cell != Cell.WIRE) return -1;
        }
        return -1;
    }

    static Set<Pos> restorable(View view, Map<Pos, Line> pending) {
        Set<Pos> allowed = new HashSet<>();
        for (Pos pos : pending.keySet()) {
            Cell cell = view.cell(pos);
            if (cell == Cell.AIR || cell == Cell.WATER) allowed.add(pos);
        }
        // Several wires can be washed away together. Only reinstate the whole
        // connected portion when all its missing positions are valid candidates.
        boolean changed;
        do {
            changed = allowed.removeIf(pos -> !intact(view, pending.get(pos), allowed));
        } while (changed);
        return Set.copyOf(allowed);
    }

    private static boolean intact(View view, Line line, Set<Pos> allowed) {
        Cell first = line.dx() == 1 ? Cell.HOOK_EAST : Cell.HOOK_SOUTH;
        Cell last = line.dx() == 1 ? Cell.HOOK_WEST : Cell.HOOK_NORTH;
        if (view.cell(line.first()) != first || view.cell(line.last()) != last) return false;
        for (Pos wire : line.wires()) {
            if (view.cell(wire) != Cell.WIRE && !allowed.contains(wire)) return false;
        }
        return true;
    }
}
