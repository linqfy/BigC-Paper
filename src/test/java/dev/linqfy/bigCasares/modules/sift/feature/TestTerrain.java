package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntBinaryOperator;

/**
 * A block source for tests: Sift terrain up to a height per column (siftslate with a growth top), air
 * above, plus individual edits. Everything inside the rectangle exists; everything outside is no area.
 */
public final class TestTerrain implements BlockSource {

    private static final BlockStateSpec AIR = BlockStateSpec.of("minecraft:air");
    private static final Set<String> NOT_SOLID = Set.of("minecraft:air", "minecraft:light", "minecraft:water", "minecraft:vine",
        "minecraft:fern", "minecraft:bush", "minecraft:short_grass", "minecraft:crimson_roots", "minecraft:short_dry_grass");

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final IntBinaryOperator height;
    private final Map<Long, BlockStateSpec> edits = new HashMap<>();
    private final Set<Long> editedColumns = new java.util.HashSet<>();

    public TestTerrain(int minX, int maxX, int minZ, int maxZ, IntBinaryOperator height) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.height = height;
    }

    public static TestTerrain flat(int minX, int maxX, int minZ, int maxZ, int height) {
        return new TestTerrain(minX, maxX, minZ, maxZ, (x, z) -> height);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public TestTerrain put(int x, int y, int z, BlockStateSpec state) {
        edits.put(key(x, y, z), state);
        editedColumns.add(((long) x << 32) ^ (z & 0xFFFFFFFFL));
        return this;
    }

    @Override
    public int minY() {
        return 0;
    }

    @Override
    public int maxY() {
        return 256;
    }

    @Override
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Override
    public BlockStateSpec state(int x, int y, int z) {
        if (!contains(x, z) || y < 0 || y >= 256) {
            return AIR;
        }
        BlockStateSpec edited = edits.get(key(x, y, z));
        if (edited != null) {
            return edited;
        }
        int top = height.applyAsInt(x, z);
        if (y > top) {
            return AIR;
        }
        return y == top ? SiftTerrain.SIFTSLATE_GROWTH : SiftTerrain.SIFTSLATE;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        return !NOT_SOLID.contains(state(x, y, z).block().asString());
    }

    @Override
    public int topY(int x, int z) {
        if (contains(x, z) && !editedColumns.contains(((long) x << 32) ^ (z & 0xFFFFFFFFL))) {
            return Math.min(255, height.applyAsInt(x, z));
        }
        for (int y = 255; y >= 0; y--) {
            if (!state(x, y, z).equals(AIR)) {
                return y;
            }
        }
        return -1;
    }
}
