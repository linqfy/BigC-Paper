package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.Objects;

/**
 * La caja de una mazmorra generada (la de su estructura {@code bigc_myth:<cultura>}), sin Bukkit. Las coordenadas son
 * de bloque e incluyen los dos extremos. {@link #key()} identifica a la mazmorra (mundo + esquina minima).
 */
public record DungeonBounds(String world, MythicCulture culture, int minX, int minY, int minZ, int maxX, int maxY,
                            int maxZ) {

    public DungeonBounds {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(culture, "culture");
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("inverted dungeon bounds");
        }
    }

    public boolean contains(String worldName, double x, double y, double z) {
        return world.equals(worldName)
            && x >= minX && x < maxX + 1.0
            && y >= minY && y < maxY + 1.0
            && z >= minZ && z < maxZ + 1.0;
    }

    public String key() {
        return world + ":" + culture.id() + ":" + minX + ":" + minY + ":" + minZ;
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerY() {
        return (minY + maxY + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }
}
