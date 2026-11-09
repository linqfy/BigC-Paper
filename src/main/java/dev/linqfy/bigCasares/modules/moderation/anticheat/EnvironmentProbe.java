package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Vehicle;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** Lectura de bloques alrededor del jugador para decidir si la fisica vanilla simple aplica ahi. */
final class EnvironmentProbe {
    enum Surroundings {
        /** Suelo y aire comunes: los limites de MovementCheck valen. */
        NORMAL,
        /** Algo cambia la friccion, el salto o empuja (hielo, miel, agua, trepables, techo bajo, pistones...). */
        SPECIAL,
        /** Algo que rebota (slime, camas): la inercia dura bastante mas. */
        BOUNCE
    }

    private static final Set<Material> SPECIAL = EnumSet.of(
        Material.HONEY_BLOCK, Material.SOUL_SAND, Material.SOUL_SOIL, Material.BUBBLE_COLUMN,
        Material.WATER, Material.LAVA, Material.COBWEB, Material.POWDER_SNOW, Material.SCAFFOLDING,
        Material.MOVING_PISTON, Material.PISTON_HEAD, Material.PISTON, Material.STICKY_PISTON,
        Material.SWEET_BERRY_BUSH, Material.END_PORTAL, Material.NETHER_PORTAL, Material.END_GATEWAY
    );

    private EnvironmentProbe() {
    }

    /** Bloques que tocan o rodean la caja del jugador parado en {@code at}, mas el techo justo encima de la cabeza. */
    static Surroundings surroundings(Player player, Location at) {
        World world = at.getWorld();
        if (world == null) {
            return Surroundings.SPECIAL;
        }
        double half = player.getWidth() / 2.0;
        double height = player.getHeight();
        int minX = floor(at.getX() - half - 0.3);
        int maxX = floor(at.getX() + half + 0.3);
        int minZ = floor(at.getZ() - half - 0.3);
        int maxZ = floor(at.getZ() + half + 0.3);
        int minY = floor(at.getY() - 1.0);
        int maxY = floor(at.getY() + height + 0.1);
        Surroundings result = Surroundings.NORMAL;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Surroundings found = classify(world.getBlockAt(x, y, z));
                    if (found == Surroundings.BOUNCE) {
                        return found;
                    }
                    if (found == Surroundings.SPECIAL) {
                        result = found;
                    }
                }
            }
        }
        if (result == Surroundings.NORMAL && lowCeiling(world, at, half, height)) {
            return Surroundings.SPECIAL;
        }
        return result;
    }

    /**
     * Nada en que apoyarse: ni un bloque (que no sea aire) en un margen amplio que llega dos bloques por debajo de los
     * pies, ni una entidad que sirva de plataforma (botes, vagonetas, shulkers, ghasts con arnes...).
     */
    static boolean airborne(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return false;
        }
        double half = player.getWidth() / 2.0;
        int minX = floor(at.getX() - half - 0.8);
        int maxX = floor(at.getX() + half + 0.8);
        int minZ = floor(at.getZ() - half - 0.8);
        int maxZ = floor(at.getZ() + half + 0.8);
        int minY = Math.max(world.getMinHeight(), floor(at.getY() - 2.0));
        int maxY = floor(at.getY() + player.getHeight() + 0.5);
        if (minY > maxY) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        for (Entity nearby : player.getNearbyEntities(2.0, 3.0, 2.0)) {
            if (platform(nearby)) {
                return false;
            }
        }
        return true;
    }

    private static boolean platform(Entity entity) {
        if (entity instanceof Vehicle || entity instanceof Boat || entity instanceof Minecart || entity instanceof Shulker) {
            return true;
        }
        String type = entity.getType().name().toUpperCase(Locale.ROOT);
        return type.contains("GHAST") || type.contains("BOAT") || type.contains("RAFT") || type.contains("MINECART");
    }

    private static Surroundings classify(Block block) {
        Material type = block.getType();
        if (type.isAir()) {
            return Surroundings.NORMAL;
        }
        if (type == Material.SLIME_BLOCK || Tag.BEDS.isTagged(type)) {
            return Surroundings.BOUNCE;
        }
        if (SPECIAL.contains(type) || Tag.ICE.isTagged(type) || Tag.CLIMBABLE.isTagged(type) || block.isLiquid()) {
            return Surroundings.SPECIAL;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return Surroundings.SPECIAL;
        }
        return Surroundings.NORMAL;
    }

    /** Un bloque solido justo encima de la cabeza corta el salto y deja encadenar saltos mas rapidos. */
    private static boolean lowCeiling(World world, Location at, double half, double height) {
        int y = floor(at.getY() + height + 0.6);
        for (int x = floor(at.getX() - half); x <= floor(at.getX() + half); x++) {
            for (int z = floor(at.getZ() - half); z <= floor(at.getZ() + half); z++) {
                if (!world.getBlockAt(x, y, z).isPassable()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
