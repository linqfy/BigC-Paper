package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.World;

/** {@link BlockGrid} de un mundo real: solido es todo bloque que no se puede atravesar. */
final class WorldBlockGrid implements BlockGrid {

    private final World world;

    WorldBlockGrid(World world) {
        this.world = world;
    }

    @Override
    public boolean solid(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return true;
        }
        return !world.getBlockAt(x, y, z).isPassable();
    }
}
