package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import dev.linqfy.bigCasares.modules.mythicdungeons.DungeonBounds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * El piso de la sala de Zeus visto como grilla: 13x13 baldosas de 3x3 bloques (39 bloques, cubre la arena de 33x33)
 * centradas en donde aparecio. Solo lee bloques (Zeus no cambia ninguno: los rayos son {@code strikeLightningEffect},
 * sin fuego). Una baldosa existe si en su centro hay piso con dos bloques libres encima cerca de la altura del
 * jefe y, si se conoce, adentro de la caja de la mazmorra: asi paredes, pilares y lo de afuera quedan fuera.
 */
final class ZeusArena {

    static final int CELL_SIZE = 3;
    static final int RADIUS = 6;
    private static final int SCAN_UP = 2;
    private static final int SCAN_DOWN = 6;
    private static final int NO_FLOOR = Integer.MIN_VALUE;

    private final World world;
    private final ZeusGrid grid;
    private final int homeY;
    private final Location home;
    private final DungeonBounds bounds;
    private final Map<ZeusCell, Integer> cellFloors = new HashMap<>();
    private final Map<Long, Integer> columnFloors = new HashMap<>();
    private final List<ZeusCell> cells;
    private final ZeusArenaMembership membership;

    ZeusArena(Location spawn, Optional<DungeonBounds> bounds) {
        this.world = spawn.getWorld();
        this.home = spawn.clone();
        this.homeY = spawn.getBlockY();
        this.bounds = bounds.orElse(null);
        this.grid = new ZeusGrid(spawn.getBlockX() + 0.5, spawn.getBlockZ() + 0.5, CELL_SIZE, RADIUS);
        for (ZeusCell cell : grid.cells()) {
            floorAt(grid.centerX(cell), grid.centerZ(cell)).ifPresent(floor -> cellFloors.put(cell, floor));
        }
        this.cells = List.copyOf(cellFloors.keySet());
        this.membership = new ZeusArenaMembership(grid, cellFloors);
    }

    /** El lugar esta adentro de la sala (ver {@link ZeusArenaMembership}). */
    boolean contains(Location location) {
        return world.equals(location.getWorld())
            && membership.contains(location.getX(), location.getY(), location.getZ());
    }

    World world() {
        return world;
    }

    ZeusGrid grid() {
        return grid;
    }

    Location home() {
        return home.clone();
    }

    /** Las baldosas con piso (las que se marcan y donde caen rayos). */
    List<ZeusCell> cells() {
        return cells;
    }

    boolean has(ZeusCell cell) {
        return cellFloors.containsKey(cell);
    }

    /** El bloque de piso de una baldosa (el de arriba es donde se para un jugador). */
    OptionalInt cellFloor(ZeusCell cell) {
        Integer floor = cellFloors.get(cell);
        return floor == null ? OptionalInt.empty() : OptionalInt.of(floor);
    }

    ZeusCell cellAt(Location location) {
        return grid.cellAt(location.getX(), location.getZ());
    }

    /** Centro de la baldosa a la altura de los pies (encima del piso). */
    Location cellCenter(ZeusCell cell, double lift) {
        int floor = cellFloors.getOrDefault(cell, homeY - 1);
        return new Location(world, grid.centerX(cell), floor + 1.0 + lift, grid.centerZ(cell));
    }

    /** Las baldosas de los jugadores que estan sobre la grilla. */
    List<ZeusCell> cellsOf(List<? extends Entity> entities) {
        List<ZeusCell> list = new ArrayList<>();
        for (Entity entity : entities) {
            ZeusCell cell = cellAt(entity.getLocation());
            if (grid.contains(cell)) {
                list.add(cell);
            }
        }
        return list;
    }

    /** Piso de la columna de bloques bajo (x, z), cerca de la altura del jefe y adentro de la sala. */
    OptionalInt floorAt(double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        Integer cached = columnFloors.get(key);
        if (cached == null) {
            cached = scan(bx, bz);
            columnFloors.put(key, cached);
        }
        return cached == NO_FLOOR ? OptionalInt.empty() : OptionalInt.of(cached);
    }

    /** Adentro de la grilla y con piso. */
    boolean walkable(double x, double z) {
        return grid.contains(grid.cellAt(x, z)) && floorAt(x, z).isPresent();
    }

    private int scan(int bx, int bz) {
        if (bounds != null && !bounds.contains(world.getName(), bx + 0.5, homeY, bz + 0.5)) {
            return NO_FLOOR;
        }
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            return NO_FLOOR;
        }
        for (int y = homeY + SCAN_UP; y >= homeY - SCAN_DOWN; y--) {
            Block block = world.getBlockAt(bx, y, bz);
            if (block.getType().isSolid()
                && block.getRelative(0, 1, 0).isPassable()
                && block.getRelative(0, 2, 0).isPassable()) {
                return y;
            }
        }
        return NO_FLOOR;
    }
}
