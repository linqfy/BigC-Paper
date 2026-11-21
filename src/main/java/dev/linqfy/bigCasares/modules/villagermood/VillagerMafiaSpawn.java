package dev.linqfy.bigCasares.modules.villagermood;

import java.util.Optional;

/** A bounded search outside the cells, using only already loaded terrain. */
final class VillagerMafiaSpawn {

    interface Terrain {
        boolean clear(int x, int y, int z);

        boolean supported(int x, int y, int z);
    }

    record Cell(int x, int y, int z) {
    }

    private VillagerMafiaSpawn() {
    }

    static Optional<Cell> find(Terrain terrain, int x, int y, int z, boolean mounted, int slot) {
        for (int radius : new int[] {12, 16, 20}) {
            for (int step = 0; step < 16; step++) {
                double angle = (step + slot * 3) * Math.PI / 8.0;
                int cx = x + (int) Math.round(Math.cos(angle) * radius);
                int cz = z + (int) Math.round(Math.sin(angle) * radius);
                for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6}) {
                    if (fits(terrain, cx, y + dy, cz, mounted)) {
                        return Optional.of(new Cell(cx, y + dy, cz));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean fits(Terrain terrain, int x, int y, int z, boolean mounted) {
        int width = mounted ? 1 : 0;
        int height = mounted ? 4 : 2;
        for (int dx = -width; dx <= width; dx++) {
            for (int dz = -width; dz <= width; dz++) {
                if (!terrain.supported(x + dx, y - 1, z + dz)) {
                    return false;
                }
                for (int dy = 0; dy < height; dy++) {
                    if (!terrain.clear(x + dx, y + dy, z + dz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
