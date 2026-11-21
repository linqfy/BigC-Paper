package dev.linqfy.bigCasares.modules.legendary;

import java.util.random.RandomGenerator;

public record LegendarySearchAreas(Square outer, Square inner) {
    public record Square(int minX, int minZ, int size) {
        public Square {
            if (size <= 0) throw new IllegalArgumentException("Tamaño inválido.");
            Math.addExact(minX, size - 1);
            Math.addExact(minZ, size - 1);
        }
        public int maxX() { return minX + size - 1; }
        public int maxZ() { return minZ + size - 1; }
        public boolean contains(int x, int z) { return x >= minX && x <= maxX() && z >= minZ && z <= maxZ(); }
        public boolean contains(Square other) {
            return contains(other.minX, other.minZ) && contains(other.maxX(), other.maxZ());
        }
    }

    public LegendarySearchAreas {
        if (outer.size != 1000 || inner.size != 500 || !outer.contains(inner)) {
            throw new IllegalArgumentException("Zonas legendarias inválidas.");
        }
    }

    public static LegendarySearchAreas create(int x, int z, RandomGenerator random) {
        Square outer = new Square(Math.subtractExact(x, random.nextInt(1000)),
            Math.subtractExact(z, random.nextInt(1000)), 1000);
        int innerX = random.nextInt(Math.max(outer.minX, x - 499), Math.min(outer.minX + 500, x) + 1);
        int innerZ = random.nextInt(Math.max(outer.minZ, z - 499), Math.min(outer.minZ + 500, z) + 1);
        return new LegendarySearchAreas(outer, new Square(innerX, innerZ, 500));
    }
}
