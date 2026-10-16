package dev.linqfy.bigCasares.dimension.travel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** In-memory {@link BlockGrid}: unset positions are air. */
final class GridFixture implements BlockGrid {

    private static final Set<String> HAZARDS = Set.of("minecraft:lava", "minecraft:fire", "minecraft:magma_block",
        "minecraft:cactus", "minecraft:powder_snow");
    private static final Set<String> PASSABLE = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:light",
        "minecraft:short_grass", "minecraft:sculk_vein", "minecraft:glow_lichen");

    private final Map<String, String> blocks = new HashMap<>();
    private final int minY;
    private final int maxY;

    GridFixture(int minY, int maxY) {
        this.minY = minY;
        this.maxY = maxY;
    }

    GridFixture set(int x, int y, int z, String id) {
        blocks.put(x + "," + y + "," + z, id);
        return this;
    }

    GridFixture fill(int x1, int y1, int z1, int x2, int y2, int z2, String id) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, id);
                }
            }
        }
        return this;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
    }

    @Override
    public boolean isSolidFloor(int x, int y, int z) {
        String id = blockId(x, y, z);
        return !PASSABLE.contains(id) && !id.equals("minecraft:water") && !HAZARDS.contains(id);
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        return PASSABLE.contains(blockId(x, y, z));
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        return HAZARDS.contains(blockId(x, y, z));
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}
