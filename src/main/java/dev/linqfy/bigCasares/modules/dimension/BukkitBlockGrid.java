package dev.linqfy.bigCasares.modules.dimension;

import dev.linqfy.bigCasares.dimension.travel.BlockGrid;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link BlockGrid} over a Bukkit world. Callers must load the chunks they query
 * first (asynchronously); unloaded chunks would otherwise be loaded synchronously.
 */
final class BukkitBlockGrid implements BlockGrid {

    private static final Set<Material> HAZARDS = EnumSet.of(
        Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CAMPFIRE,
        Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW,
        Material.WITHER_ROSE, Material.COBWEB, Material.POINTED_DRIPSTONE, Material.SCULK_SHRIEKER
    );

    private final World world;

    BukkitBlockGrid(World world) {
        this.world = world;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().getKey().asString();
    }

    @Override
    public boolean isSolidFloor(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        Block block = world.getBlockAt(x, y, z);
        return block.getType().isSolid() && block.getType().isOccluding();
    }

    @Override
    public boolean isPassable(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        Block block = world.getBlockAt(x, y, z);
        return block.isPassable() && !block.isLiquid();
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        return HAZARDS.contains(world.getBlockAt(x, y, z).getType());
    }

    @Override
    public int minY() {
        return world.getMinHeight();
    }

    @Override
    public int maxY() {
        return world.getMaxHeight() - 1;
    }
}
