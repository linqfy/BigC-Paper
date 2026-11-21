package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/** El terreno de la sala visto por las reglas puras (solo lectura: Odín nunca cambia bloques). */
final class OdinWorld {

    private OdinWorld() {
    }

    static OdinCharge.Terrain terrain(World world) {
        return (x, y, z) -> !world.getBlockAt(x, y, z).isPassable();
    }

    /** El primer lugar con piso y dos bloques libres en la columna de {@code at}, bajando desde {@code topY}. */
    static Optional<Location> groundNear(Location at, int topY, int depth) {
        World world = at.getWorld();
        if (world == null) {
            return Optional.empty();
        }
        OdinCharge.Terrain terrain = terrain(world);
        int x = at.getBlockX();
        int z = at.getBlockZ();
        for (int y = topY; y >= topY - depth; y--) {
            if (terrain.solid(x, y - 1, z) && !terrain.solid(x, y, z) && !terrain.solid(x, y + 1, z)) {
                return Optional.of(new Location(world, x + 0.5, y, z + 0.5));
            }
        }
        return Optional.empty();
    }
}
