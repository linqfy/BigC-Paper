package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.mythicdungeons.DungeonBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * La sala de Enma en el mundo (solo lectura: Enma no cambia ningun bloque). Junta el circulo de {@link EnmaArena} con
 * la caja de la mazmorra, si se conoce, y encuentra piso para caminar o hacer nacer reflejos y oni.
 */
final class EnmaTerrain {

    private static final int SCAN_UP = 3;
    private static final int SCAN_DOWN = 6;

    private final World world;
    private final EnmaArena arena;
    private final DungeonBounds bounds;
    private final int homeY;

    EnmaTerrain(Location spawn, EnmaArena arena, Optional<DungeonBounds> bounds) {
        this.world = spawn.getWorld();
        this.arena = arena;
        this.bounds = bounds.orElse(null);
        this.homeY = spawn.getBlockY();
    }

    World world() {
        return world;
    }

    EnmaArena arena() {
        return arena;
    }

    /** El lugar esta adentro de la sala (circulo, altura y caja de la mazmorra). */
    boolean inside(Location location) {
        return world.equals(location.getWorld())
            && arena.contains(location.getX(), location.getY(), location.getZ())
            && (bounds == null || bounds.contains(world.getName(), location.getX(), location.getY(), location.getZ()));
    }

    /** Las balas se rompen contra bloques solidos y contra el borde de la mazmorra. */
    EnmaBullets.Solid solid() {
        return (x, y, z) -> {
            if (bounds != null && !bounds.contains(world.getName(), x + 0.5, y + 0.5, z + 0.5)) {
                return true;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return true;
            }
            return !world.getBlockAt(x, y, z).isPassable();
        };
    }

    /** Bloque de piso de la columna (x, z) cerca de la altura de Enma, con dos libres encima y adentro de la sala. */
    OptionalInt floorAt(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!arena.containsHorizontally(x, z)) {
            return OptionalInt.empty();
        }
        if (bounds != null && !bounds.contains(world.getName(), bx + 0.5, homeY, bz + 0.5)) {
            return OptionalInt.empty();
        }
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return OptionalInt.empty();
        }
        for (int y = homeY + SCAN_UP; y >= homeY - SCAN_DOWN; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (block.getType().isSolid()
                && block.getRelative(0, 1, 0).isPassable()
                && block.getRelative(0, 2, 0).isPassable()) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /** Un lugar parado sobre el piso en (x, z), si hay. */
    Optional<Location> standAt(double x, double z) {
        OptionalInt floor = floorAt(x, z);
        if (floor.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, floor.getAsInt() + 1.0, z));
    }
}
