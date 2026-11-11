package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Block probe over a live world. Blocks outside the build height count as open (the void is a long drop) and
 * blocks in unloaded chunks count as solid, so probing never loads chunks.
 */
final class BukkitBlockProbe implements BlockProbe {

    private final World world;

    BukkitBlockProbe(World world) {
        this.world = world;
    }

    @Override
    public boolean passable(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return true;
        }
        Block block = block(x, y, z);
        return block != null && block.isPassable();
    }

    @Override
    public boolean tall(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }
        Block block = block(x, y, z);
        return block == null || block.getBoundingBox().getHeight() > 1.0;
    }

    private Block block(int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, y, z);
    }
}
