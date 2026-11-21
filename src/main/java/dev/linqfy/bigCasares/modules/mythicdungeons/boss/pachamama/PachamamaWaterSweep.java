package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Which water to dry after a blue round (no server types). The fight places water sources without physics and puts
 * them back through the journal, but if one of them flows, the infinite-water rule can make a new source between two
 * of ours (the corners of the chessboard) that the journal never saw. Columns are packed as {@link #key}.
 */
public final class PachamamaWaterSweep {

    private PachamamaWaterSweep() {
    }

    public static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public static int x(long key) {
        return (int) (key >> 32);
    }

    public static int z(long key) {
        return (int) key;
    }

    /** The painted columns plus a one-block border around them. */
    public static Set<Long> area(Set<Long> painted) {
        Set<Long> area = new HashSet<>();
        for (long column : painted) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    area.add(key(x(column) + dx, z(column) + dz));
                }
            }
        }
        return area;
    }

    /**
     * The columns of {@code area} inside the room with water now that had none when the round was painted.
     *
     * @param before columns with water when the round was painted (never touched)
     * @param water  whether a column has water at the water level now
     * @param inside whether a column is inside the room
     */
    public static List<Long> strays(Set<Long> area, Set<Long> before, LongPredicate water, LongPredicate inside) {
        List<Long> strays = new ArrayList<>();
        for (long column : area) {
            if (!before.contains(column) && inside.test(column) && water.test(column)) {
                strays.add(column);
            }
        }
        return strays;
    }
}
